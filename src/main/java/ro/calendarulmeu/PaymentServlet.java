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

@WebServlet("/payment")
public class PaymentServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String action = request.getParameter("action");
        
        if ("parse".equals(action)) {
            handleParsePaymentResponse(request, response);
        } else if ("prepare".equals(action)) {
            handlePreparePaymentRequest(request, response);
        } else {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("Invalid action");
        }
    }

    private void handleParsePaymentResponse(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String data = request.getParameter("data");
        String envKey = request.getParameter("envKey");
        String privateKey = request.getParameter("privateKey");

        // Log the input parameters
        System.out.println("Data: " + (data != null ? data.substring(0, Math.min(data.length(), 100)) + "..." : "null"));
        System.out.println("EnvKey: " + (envKey != null ? envKey.substring(0, Math.min(envKey.length(), 100)) + "..." : "null"));
        System.out.println("PrivateKey: " + (privateKey != null ? "Not null, length: " + privateKey.length() : "null"));

        String[] action = new String[1];
        String[] email = new String[1];
        BigDecimal[] processedAmount = new BigDecimal[1];
        String[] crc = new String[1];
        BigDecimal[] errorCode = new BigDecimal[1];
        String[] errorMessage = new String[1];
        String[] javaErrorDetails = new String[1];

        if (data == null || envKey == null || privateKey == null) {
            javaErrorDetails[0] = "One or more required parameters are null. Data: " + (data != null) + ", EnvKey: " + (envKey != null) + ", PrivateKey: " + (privateKey != null);
        } else {
            Main.parsePaymentResponse(data, envKey, privateKey, action, email, processedAmount, crc, errorCode, errorMessage, javaErrorDetails);
        }

        ObjectMapper mapper = new ObjectMapper();
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), new ParseResponseResult(action[0], email[0], processedAmount[0], crc[0], errorCode[0], errorMessage[0], javaErrorDetails[0]));
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

            ObjectMapper mapper = new ObjectMapper();
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            mapper.writeValue(response.getWriter(), new PrepareRequestResult(data, envKey, errorDetails[0]));
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

        public ParseResponseResult(String action, String email, BigDecimal processedAmount, String crc, BigDecimal errorCode, String errorMessage, String javaErrorDetails) {
            this.action = action;
            this.email = email;
            this.processedAmount = processedAmount;
            this.crc = crc;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.javaErrorDetails = javaErrorDetails;
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
}