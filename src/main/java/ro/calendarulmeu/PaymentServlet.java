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
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.io.Reader;
import java.io.StringWriter;
import java.io.PrintWriter;
import ro.mobilPay.payment.request.Abstract;
import ro.mobilPay.payment.request.Card;
import ro.mobilPay.payment.request.Notify;
import ro.mobilPay.util.ListItem;
import ro.mobilPay.util.OpenSSL;

@WebServlet("/payment")
public class PaymentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

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
                case "encrypt":
                    handleEncryptPayload(request, response);
                    break;
                case "decrypt":
                    handleDecryptPayload(request, response);
                    break;
                default:
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid action");
            }
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error: " + e.getMessage());
        }
    }

    private void handleEncryptPayload(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String payload = request.getParameter("payload");
        String secretOcid = request.getParameter("secretOcid");

        if (payload == null || secretOcid == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Payload and secretOcid are required");
            return;
        }

        try {
            SecretManager.SecretInfo secretInfo = SecretManager.getSecret(secretOcid);
            String aesKey = secretInfo.getContent();
            Long secretVersion = secretInfo.getVersion();

            if (aesKey == null) {
                throw new Exception("Failed to retrieve AES key from SecretManager");
            }

            String encryptedPayload = encryptAES(payload, aesKey);
            EncryptResult result = new EncryptResult(encryptedPayload, secretVersion);
            sendJsonResponse(response, result);
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error encrypting payload: " + e.getMessage());
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

                SecretManager.SecretInfo secretInfo = SecretManager.getSecret(secretOcid, secretVersion);
                String aesKey = secretInfo.getContent();

                if (aesKey == null) {
                    throw new Exception("Failed to retrieve AES key from SecretManager");
                }

                String privateKey = decryptAES(encryptedPrivateKey, aesKey);

                parsePaymentResponse(data, envKey, privateKey, action, email, processedAmount,
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

            preparePaymentRequest(certificate, xmlData, dataParameter, envKeyParameter, errorDetails);

            String data = clobToString(dataParameter[0]);
            String envKey = clobToString(envKeyParameter[0]);

            PrepareRequestResult result = new PrepareRequestResult(data, envKey, errorDetails[0]);
            sendJsonResponse(response, result);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("Error: " + e.getMessage());
        }
    }

    public static String encryptAES(String plainText, String aesKey) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(aesKey);
        SecretKey secretKey = new SecretKeySpec(decodedKey, "AES");

        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

        byte[] encryptedText = cipher.doFinal(plainText.getBytes("UTF-8"));

        ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedText.length);
        byteBuffer.put(iv);
        byteBuffer.put(encryptedText);
        byte[] cipherMessage = byteBuffer.array();

        return Base64.getEncoder().encodeToString(cipherMessage);
    }

    public static String decryptAES(String encryptedText, String aesKey) throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(aesKey);
        SecretKey secretKey = new SecretKeySpec(decodedKey, "AES");

        byte[] decodedCipherMessage = Base64.getDecoder().decode(encryptedText);

        ByteBuffer byteBuffer = ByteBuffer.wrap(decodedCipherMessage);
        byte[] iv = new byte[GCM_IV_LENGTH];
        byteBuffer.get(iv);
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH * 8, iv);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

        byte[] decryptedText = cipher.doFinal(cipherText);

        return new String(decryptedText, "UTF-8");
    }

    public static void parsePaymentResponse(
        String data, String envKey, String privateKey,
        String[] action, String[] email, BigDecimal[] processedAmount,
        String[] crc, BigDecimal[] errorCode, String[] errorMessage, String[] javaErrorDetails, String[] orderId
    ) {
        try {
            // Check if data, envKey, or privateKey is null
            if (data == null || envKey == null || privateKey == null) {
                throw new IllegalArgumentException("data, envKey, and privateKey must not be null");
            }

            Abstract paymentResponse = Abstract.factoryFromEncrypted(envKey, data, privateKey);

            if (paymentResponse == null) {
                throw new Exception("factoryFromEncrypted returned null");
            }

            if (paymentResponse instanceof ro.mobilPay.payment.request.Card) {
                Card cardResponse = (Card) paymentResponse;

                Notify mobilpayResponse = cardResponse._objReqNotify;
                
                // Extract the required information from cardResponse
                orderId[0] = cardResponse._orderId;
                action[0] = mobilpayResponse._action;
                email[0] = mobilpayResponse._customer._email;
                processedAmount[0] = BigDecimal.valueOf(mobilpayResponse._processedAmount);
                crc[0] = mobilpayResponse._crc;
                errorCode[0] = new BigDecimal(mobilpayResponse._errorCode);
                errorMessage[0] = mobilpayResponse._errorMessage;
            } else {
                throw new Exception("Unexpected payment response type: " + (paymentResponse != null ? paymentResponse.getClass().getName() : "null"));
            }
        } catch (IllegalArgumentException e) {
            // Handle the case where input parameters are null
            String errorMsg = "Invalid input: " + e.getMessage();
            System.err.println(errorMsg);
            javaErrorDetails[0] = errorMsg;
        } catch (Exception e) {
            // Capture the full stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String fullStackTrace = sw.toString();

            String javaError = e.getMessage() + "\n" + fullStackTrace;
            System.err.println(javaError);
            javaErrorDetails[0] = javaError;
        }
    }

    public static void preparePaymentRequest(String certificate, String xmlData, Clob[] dataParameter, Clob[] envKeyParameter, String[] errorDetails) {
        try {
            // Initialize Bouncy Castle Provider
            OpenSSL.extraInit();

            // Use the factory method to create the appropriate Abstract object
            Abstract paymentRequest = Card.factory(xmlData);

            if (paymentRequest instanceof ro.mobilPay.payment.request.Card) {
                Card cardRequest = (Card) paymentRequest;

                ListItem encryptedData = cardRequest.encrypt(certificate);

                String envKey = encryptedData.getKey();
                String data = encryptedData.getVal();

                // Check if data and key are not null
                if (data == null || envKey == null) {
                    errorDetails[0] = "Data or key cannot be null";
                    dataParameter[0] = null;
                    envKeyParameter[0] = null;
                    return;
                }

                // Set the data and envKey parameters, handling different Clob types
                setClobData(dataParameter, data);
                setClobData(envKeyParameter, envKey);
            } else {
                errorDetails[0] = "Unexpected payment request type";
                dataParameter[0] = null;
                envKeyParameter[0] = null;
            }
        } catch (Exception e) {
            // Capture the full stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String fullStackTrace = sw.toString();

            String errorMessage = e.getMessage() + "\n" + fullStackTrace;
            System.err.println(errorMessage); // Log to standard error
            errorDetails[0] = errorMessage;
            dataParameter[0] = null;
            envKeyParameter[0] = null;
        }
    }

    private static void setClobData(Clob[] clobArray, String data) throws Exception {
        if (clobArray[0] instanceof javax.sql.rowset.serial.SerialClob) {
            // If it's a SerialClob, we need to replace it
            clobArray[0] = new javax.sql.rowset.serial.SerialClob(data.toCharArray());
        } else {
            // For Oracle CLOBs and other Clob implementations, use setString
            clobArray[0].setString(1, data);
        }
    }

    public static String clobToString(Clob clob) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (Reader reader = clob.getCharacterStream()) {
            char[] buffer = new char[1024];
            int chars;
            while ((chars = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, chars);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unused")
    private static class EncryptResult {
        public String encryptedPayload;
        public Long secretVersion;

        public EncryptResult(String encryptedPayload, Long secretVersion) {
            this.encryptedPayload = encryptedPayload;
            this.secretVersion = secretVersion;
        }
    }

    @SuppressWarnings("unused")
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

    @SuppressWarnings("unused")
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

    private void handleDecryptPayload(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String encryptedPayload = request.getParameter("encryptedPayload");
        String secretOcid = request.getParameter("secretOcid");
        String secretVersionStr = request.getParameter("secretVersion");

        if (encryptedPayload == null || secretOcid == null) {
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "EncryptedPayload and secretOcid are required");
            return;
        }

        try {
            Optional<Long> secretVersion = Optional.empty();
            if (secretVersionStr != null && !secretVersionStr.isEmpty()) {
                try {
                    secretVersion = Optional.of(Long.parseLong(secretVersionStr));
                } catch (NumberFormatException e) {
                    sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid secret version number: " + secretVersionStr);
                    return;
                }
            }

            SecretManager.SecretInfo secretInfo = secretVersion.isPresent() 
                ? SecretManager.getSecret(secretOcid, secretVersion)
                : SecretManager.getSecret(secretOcid);
            String aesKey = secretInfo.getContent();

            if (aesKey == null) {
                throw new Exception("Failed to retrieve AES key from SecretManager");
            }

            String decryptedPayload = decryptAES(encryptedPayload, aesKey);
            DecryptResult result = new DecryptResult(decryptedPayload);
            sendJsonResponse(response, result);
        } catch (Exception e) {
            sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error decrypting payload: " + e.getMessage());
        }
    }

    @SuppressWarnings("unused")
    private static class DecryptResult {
        public String decryptedPayload;

        public DecryptResult(String decryptedPayload) {
            this.decryptedPayload = decryptedPayload;
        }
    }
}
