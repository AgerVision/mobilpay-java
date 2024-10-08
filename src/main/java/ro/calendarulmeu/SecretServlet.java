package ro.calendarulmeu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import java.util.Collections;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Base64;

@WebServlet("/get_secret")
public class SecretServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String secretOcid = request.getParameter("secretOcid");
        
        if (secretOcid == null || secretOcid.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Error: secretOcid parameter is required");
            return;
        }

        try (SecretsClient secretsClient = SecretsClient.builder()
                .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build())) {

            byte[] secretBytes = retrieveSecret(secretsClient, secretOcid);
            String encodedSecret = Base64.getEncoder().encodeToString(secretBytes);
            
            ObjectMapper mapper = new ObjectMapper();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), Collections.singletonMap("secret", encodedSecret));
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            String errorMessage = String.format("Error: %s\nCause: %s\nStack trace: %s",
                e.getMessage(),
                e.getCause() != null ? e.getCause().getMessage() : "No cause",
                getStackTraceAsString(e));
            response.getWriter().write(errorMessage);
            e.printStackTrace(); // Log the full stack trace
        }
    }

    private byte[] retrieveSecret(SecretsClient secretsClient, String secretId) {
        Base64SecretBundleContentDetails contentDetails =
                (Base64SecretBundleContentDetails)
                        secretsClient
                                .getSecretBundle(
                                        GetSecretBundleRequest.builder()
                                                .secretId(secretId)
                                                .stage(GetSecretBundleRequest.Stage.Current)
                                                .build())
                                .getSecretBundle()
                                .getSecretBundleContent();
        return Base64.getDecoder().decode(contentDetails.getContent());
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString());
            sb.append("\n");
        }
        return sb.toString();
    }
}