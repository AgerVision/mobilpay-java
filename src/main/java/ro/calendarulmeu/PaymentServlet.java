package ro.calendarulmeu;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Clob;
import javax.sql.rowset.serial.SerialClob;
import java.util.Optional;

@WebServlet("/payment")
public class PaymentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        
        try {
            switch (action) {
                case "parse":
                    handleParsePaymentResponse(request, response);
                    break;
                case "prepare":
                    handlePreparePaymentRequest(request, response);
                    break;
                default:
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid action");
            }
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    private void handleParsePaymentResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String data = request.getParameter("data");
        String envKey = request.getParameter("envKey");
        String encryptedPrivateKey = request.getParameter("encryptedPrivateKey");
        String secretOcid = request.getParameter("secretOcid");
        String secretVersionStr = request.getParameter("secretVersion");

        String[] action = new String[1];
        String[] email = new String[1];
        BigDecimal[] processedAmount = new BigDecimal[1];
        String[] crc = new String[1];
        BigDecimal[] errorCode = new BigDecimal[1];
        String[] errorMessage = new String[1];
        String[] javaErrorDetails = new String[1];
        String[] orderId = new String[1];

        if (data == null || envKey == null || encryptedPrivateKey == null || secretOcid == null) {
            javaErrorDetails[0] = "One or more required parameters are null. Data: " + (data != null) + ", EnvKey: " + (envKey != null) + ", EncryptedPrivateKey: " + (encryptedPrivateKey != null) + ", SecretOcid: " + (secretOcid != null);
        } else {
            try {
                Optional<Long> secretVersion = Optional.empty();
                if (secretVersionStr != null && !secretVersionStr.isEmpty()) {
                    try {
                        secretVersion = Optional.of(Long.parseLong(secretVersionStr));
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid secret version number: " + secretVersionStr);
                    }
                }

                SecretServlet.SecretInfo secretInfo = SecretServlet.getSecret(secretOcid, secretVersion);
                String aesKey = secretInfo.getContent();

                if (aesKey == null) {
                    throw new Exception("Failed to retrieve AES key from SecretServlet");
                }

                String privateKey = Main.decryptAES(encryptedPrivateKey, aesKey);

                Main.parsePaymentResponse(data, envKey, privateKey, action, email, processedAmount,
                crc, errorCode, errorMessage, javaErrorDetails, orderId);
            } catch (Exception e) {
                javaErrorDetails[0] = "Error processing payment response: " + e.getMessage();
            }
        }

        ParseResponseResult result = new ParseResponseResult(action[0], email[0], processedAmount[0],
            crc[0], errorCode[0], errorMessage[0], javaErrorDetails[0], orderId[0]);
        sendJsonResponse(response, result);
    }

    private void handlePreparePaymentRequest(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String certificate = request.getParameter("certificate");
        String xmlData = request.getParameter("xmlData");

        Clob[] dataParameter = new Clob[1];
        Clob[] envKeyParameter = new Clob[1];
        String[] errorDetails = new String[1];

        try {
            dataParameter[0] = new SerialClob(new char[0]);
            envKeyParameter[0] = new SerialClob(new char[0]);

            Main.preparePaymentRequest(certificate, xmlData, dataParameter, envKeyParameter, errorDetails);

            String data = Main.clobToString(dataParameter[0]);
            String envKey = Main.clobToString(envKeyParameter[0]);

            PrepareRequestResult result = new PrepareRequestResult(data, envKey, errorDetails[0]);
            sendJsonResponse(response, result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: " + e.getMessage());
        }
    }

    private static class ParseResponseResult {
        public String action;
        public String email;
        public BigDecimal processedAmount;
        public String crc;
        public BigDecimal errorCode;
        public String errorMessage;
        public String javaErrorDetails;
        public String orderId;

        public ParseResponseResult(String action, String email, BigDecimal processedAmount,
            String crc, BigDecimal errorCode, String errorMessage, String javaErrorDetails, String orderId) {
            this.action = action;
            this.email = email;
            this.processedAmount = processedAmount;
            this.crc = crc;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.javaErrorDetails = javaErrorDetails;
            this.orderId = orderId;
        }
    }

    private static class PrepareRequestResult {
        public String data;
        public String envKey;
        public String errorDetails;

        public PrepareRequestResult(String data, String envKey, String errorDetails) {
            this.data = data;
            this.envKey = envKey;
            this.errorDetails = errorDetails;
        }
    }

    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.getWriter().write(message);
    }

    private void sendJsonResponse(HttpServletResponse response, Object data) throws IOException {
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), data);
    }
}
