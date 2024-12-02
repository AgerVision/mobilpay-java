package ro.calendarulmeu;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import ro.calendarulmeu.SecretManager.EncryptResult;
import ro.calendarulmeu.SecretManager.DecryptResult;

@WebServlet("/encryption/*")
public class EncryptionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String pathInfo = request.getPathInfo();
            
            switch (pathInfo) {
                case "/encrypt":
                    handleEncrypt(request, response);
                    break;
                case "/decrypt":
                    handleDecrypt(request, response);
                    break;
                case "/encrypt-old":
                    handleEncryptOld(request, response);
                    break;
                case "/decrypt-old":
                    handleDecryptOld(request, response);
                    break;
                default:
                    sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Invalid endpoint");
            }
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    private void handleEncrypt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String payload = request.getParameter("payload");
        String masterKeyId = request.getParameter("masterKeyId");
        String vaultId = request.getParameter("vaultId");

        if (payload == null || masterKeyId == null || vaultId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "Payload, masterKeyId, and vaultId are required");
            return;
        }

        try {
            String encryptedPayload = SecretManager.encryptWithKms(payload, masterKeyId, vaultId);
            sendJsonResponse(response, new EncryptResult(encryptedPayload, null));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error encrypting payload: " + e.getMessage());
        }
    }

    private void handleDecrypt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String encryptedPayload = request.getParameter("encryptedPayload");
        String masterKeyId = request.getParameter("masterKeyId");
        String vaultId = request.getParameter("vaultId");

        if (encryptedPayload == null || masterKeyId == null || vaultId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "EncryptedPayload, masterKeyId, and vaultId are required");
            return;
        }

        try {
            String decryptedPayload = SecretManager.decryptWithKms(encryptedPayload, masterKeyId, vaultId);
            sendJsonResponse(response, new DecryptResult(decryptedPayload));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error decrypting payload: " + e.getMessage());
        }
    }

    private void handleEncryptOld(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String payload = request.getParameter("payload");
        String secretId = request.getParameter("secretOcid");

        System.out.println("Encrypt-old received parameters:");
        System.out.println("payload: " + payload);
        System.out.println("secretOcid: " + secretId);

        if (payload == null || secretId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "Payload and secretOcid are required. Received payload: " + payload + ", secretOcid: " + secretId);
            return;
        }

        try {
            // Get the secret info first to get the version
            SecretManager.SecretInfo secretInfo = SecretManager.getSecret(secretId);
            String encryptedPayload = SecretManager.encryptWithSecret(payload, secretId);
            
            // Include the version in the response
            sendJsonResponse(response, new EncryptResult(encryptedPayload, secretInfo.getVersion().toString()));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error encrypting payload: " + e.getMessage());
        }
    }

    private void handleDecryptOld(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String encryptedPayload = request.getParameter("encryptedPayload");
        String secretId = request.getParameter("secretOcid");

        System.out.println("Decrypt-old received parameters:");
        System.out.println("encryptedPayload: " + encryptedPayload);
        System.out.println("secretOcid: " + secretId);

        if (encryptedPayload == null || secretId == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "EncryptedPayload and secretOcid are required. Received encryptedPayload: " + encryptedPayload + ", secretOcid: " + secretId);
            return;
        }

        try {
            String decryptedPayload = SecretManager.decryptWithSecret(encryptedPayload, secretId);
            sendJsonResponse(response, new DecryptResult(decryptedPayload));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error decrypting payload: " + e.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) 
            throws IOException {
        response.setStatus(statusCode);
        response.getWriter().write(message);
    }

    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), data);
    }
} 