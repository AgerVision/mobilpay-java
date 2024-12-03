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
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        
        try {
            String pathInfo = request.getPathInfo();
            
            switch (pathInfo) {
                case "/encrypt":
                    handleEncrypt(request, response);
                    break;
                case "/decrypt":
                    handleDecrypt(request, response);
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
        request.setCharacterEncoding("UTF-8");

        String payload = request.getParameter("payload");
        String masterKeyId = request.getParameter("masterKeyId");
        String cryptoEndpoint = request.getParameter("cryptoEndpoint");

        System.out.println("Received payload for encryption: " + payload);

        if (payload == null || masterKeyId == null || cryptoEndpoint == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "Payload, masterKeyId, and cryptoEndpoint are required");
            return;
        }

        try {
            String encryptedPayload = SecretManager.encryptWithKms(payload, masterKeyId, cryptoEndpoint);
            sendJsonResponse(response, new EncryptResult(encryptedPayload));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error encrypting payload: " + e.getMessage());
        }
    }

    private void handleDecrypt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        request.setCharacterEncoding("UTF-8");

        String encryptedPayload = request.getParameter("encryptedPayload");
        String masterKeyId = request.getParameter("masterKeyId");
        String cryptoEndpoint = request.getParameter("cryptoEndpoint");

        if (encryptedPayload == null || masterKeyId == null || cryptoEndpoint == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "EncryptedPayload, masterKeyId, and cryptoEndpoint are required");
            return;
        }

        try {
            String decryptedPayload = SecretManager.decryptWithKms(encryptedPayload, masterKeyId, cryptoEndpoint);
            System.out.println("Decrypted payload: " + decryptedPayload);
            sendJsonResponse(response, new DecryptResult(decryptedPayload));
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error decrypting payload: " + e.getMessage());
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("text/plain; charset=UTF-8");
        response.getWriter().write(message);
    }

    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), data);
    }
} 