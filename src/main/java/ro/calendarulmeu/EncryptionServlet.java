package ro.calendarulmeu;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import ro.calendarulmeu.SecretManager.SecretInfo;

@WebServlet("/encryption/*")
public class EncryptionServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            String pathInfo = request.getPathInfo();
            
            if ("/encrypt".equals(pathInfo)) {
                handleEncrypt(request, response);
            } else if ("/decrypt".equals(pathInfo)) {
                handleDecrypt(request, response);
            } else {
                sendErrorResponse(response, HttpServletResponse.SC_NOT_FOUND, "Invalid endpoint");
            }
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    private void handleEncrypt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String payload = request.getParameter("payload");
        String secretOcid = request.getParameter("secretOcid");

        if (payload == null || secretOcid == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Payload and secretOcid are required");
            return;
        }

        try {
            SecretInfo secretInfo = SecretManager.getSecret(secretOcid);
            String aesKey = secretInfo.getContent();
            Long secretVersion = secretInfo.getVersion();

            if (aesKey == null) {
                throw new Exception("Failed to retrieve AES key from SecretManager");
            }

            String encryptedPayload = SecretManager.encryptAES(payload, aesKey);
            EncryptResult result = new EncryptResult(encryptedPayload, secretVersion);
            sendJsonResponse(response, result);
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, 
                "Error encrypting payload: " + e.getMessage());
        }
    }

    private void handleDecrypt(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String encryptedPayload = request.getParameter("encryptedPayload");
        String secretOcid = request.getParameter("secretOcid");
        String secretVersionStr = request.getParameter("secretVersion");

        if (encryptedPayload == null || secretOcid == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                "EncryptedPayload and secretOcid are required");
            return;
        }

        try {
            Optional<Long> secretVersion = Optional.empty();
            if (secretVersionStr != null && !secretVersionStr.isEmpty()) {
                try {
                    secretVersion = Optional.of(Long.parseLong(secretVersionStr));
                } catch (NumberFormatException e) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, 
                        "Invalid secret version number: " + secretVersionStr);
                    return;
                }
            }

            SecretInfo secretInfo = secretVersion.isPresent() 
                ? SecretManager.getSecret(secretOcid, secretVersion)
                : SecretManager.getSecret(secretOcid);
            String aesKey = secretInfo.getContent();

            if (aesKey == null) {
                throw new Exception("Failed to retrieve AES key from SecretManager");
            }

            String decryptedPayload = SecretManager.decryptAES(encryptedPayload, aesKey);
            DecryptResult result = new DecryptResult(decryptedPayload);
            sendJsonResponse(response, result);
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

    private static class EncryptResult {
        @SuppressWarnings("unused")
        public String encryptedPayload;
        @SuppressWarnings("unused")
        public Long secretVersion;

        public EncryptResult(String encryptedPayload, Long secretVersion) {
            this.encryptedPayload = encryptedPayload;
            this.secretVersion = secretVersion;
        }
    }

    private static class DecryptResult {
        @SuppressWarnings("unused")
        public String decryptedPayload;

        public DecryptResult(String decryptedPayload) {
            this.decryptedPayload = decryptedPayload;
        }
    }
} 