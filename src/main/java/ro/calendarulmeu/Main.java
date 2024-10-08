package ro.calendarulmeu;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Clob;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.io.IOException;
import java.math.BigDecimal;

import javax.sql.rowset.serial.SerialClob;

import ro.mobilPay.payment.request.Abstract;
import ro.mobilPay.payment.request.Card;
import ro.mobilPay.util.ListItem;
import ro.mobilPay.util.OpenSSL;
import ro.mobilPay.payment.request.Notify;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

public class Main {
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Choose an option:");
        System.out.println("1. Encrypt");
        System.out.println("2. Decrypt");
        System.out.println("3. Test request/response");
        
        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        switch (choice) {
            case 1:
                System.out.print("Enter the AES key (Base64 encoded): ");
                String encryptKey = scanner.nextLine().trim();
                System.out.println("Enter the plaintext to encrypt (type 'END' on a new line to finish):");
                StringBuilder plainTextBuilder = new StringBuilder();
                String line;
                while (scanner.hasNextLine()) {
                    line = scanner.nextLine();
                    if (line.equals("END")) {
                        break;
                    }
                    plainTextBuilder.append(line).append("\n");
                }
                String plainText = plainTextBuilder.toString().trim();
                try {
                    String encrypted = encryptAES(plainText, encryptKey);
                    System.out.println("Encrypted text: " + encrypted);
                } catch (Exception e) {
                    System.out.println("Error during encryption: " + e.getMessage());
                }
                break;
            case 2:
                System.out.print("Enter the AES key (Base64 encoded): ");
                String decryptKey = scanner.nextLine().trim();
                System.out.print("Enter the encrypted text: ");
                String encryptedText = scanner.nextLine().trim();
                try {
                    String decrypted = decryptAES(encryptedText, decryptKey);
                    System.out.println("Decrypted text:");
                    System.out.println(decrypted);
                } catch (Exception e) {
                    System.out.println("Error during decryption: " + e.getMessage());
                }
                break;
            case 3:
                testRequestResponse(scanner);
                break;
            default:
                System.out.println("Invalid choice. Please run the program again and enter 1, 2, or 3.");
        }
        scanner.close();
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

    private static void testRequestResponse(Scanner scanner) {
        System.out.println("Do you want to test a request or a response? (Enter 'req' or 'res')");
        String choice = scanner.nextLine().trim().toLowerCase();

        if (choice.equals("req")) {
            test_request();
        } else if (choice.equals("res")) {
            System.out.println("Enter the private key (paste the entire key, then type 'END' on a new line when finished):");
            StringBuilder privateKeyBuilder = new StringBuilder();
            String line;
            while (scanner.hasNextLine()) {
                line = scanner.nextLine();
                if (line.equals("END")) {
                    break;
                }
                privateKeyBuilder.append(line).append("\n");
            }
            String privateKey = privateKeyBuilder.toString().trim();
            test_response(privateKey);
        } else {
            System.out.println("Invalid choice. Please run the program again and enter 'req' or 'res'.");
        }
    }

    public static void test_request() {
        try {
            String certificatePath = "src\\main\\java\\ro\\calendarulmeu\\Netopia_public_certificate.cer";
            String certificate = readCertificateFromFile(certificatePath);

            // Create the XML string with current timestamp
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            String timestamp = now.format(formatter);

            String xmlData = 
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
                "<order type=\"card\" id=\"18\" timestamp=\"" + timestamp + "\">" +
                    "<signature>2BJY-MN5D-SHVP-AYRR-V2GO</signature>" +
                    "<url>" +
                        "<return>https://calendarulmeu.ro/return</return>" +
                        "<confirm>https://calendarulmeu.ro/confirm</confirm>" +
                    "</url>" +
                    "<invoice currency=\"RON\" amount=\"49.99\">" +
                         "<details>Avans programare client</details>" +
                    "</invoice>" +
                "</order>";

            System.out.println("xmlData: " + xmlData);

            String paymentUrl = "http://sandboxsecure.mobilpay.ro"; // Use https for live mode

            // Create a Clob array to hold the response
            Clob[] responseClob = new Clob[1];
            responseClob[0] = new SerialClob(new char[0]);

            String[] errorDetails = new String[1];
            
            initiatePaymentSetClob(certificate, xmlData, paymentUrl, responseClob, errorDetails);

            if (responseClob[0] != null) {
                try (Reader clobReader = responseClob[0].getCharacterStream()) {
                    StringBuilder clobText = new StringBuilder();
                    char[] buffer = new char[1024];
                    int bytesRead;
                    while ((bytesRead = clobReader.read(buffer)) != -1) {
                        clobText.append(buffer, 0, bytesRead);
                    }
                    System.out.println("Response: " + clobText.toString());
                }
            } else {
                System.out.println("Payment initiation failed. Error details: " + errorDetails[0]);
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void test_response(String privateKey) {
        try {
            System.out.println("Testing response...");
            
            String envKey = "7HbgKMMNe3HkW+M4BR7fz9Q8YzvkX0Hh0B4Pv7fr9wmjMNQs1cXgDxTQ0Uy/UglQXoKbzcnH/RDKbSubn85kOROlA7CLnpdoF7lcWO3tDB5vNbt5MqKMP1LP25xPX0DYT/Xko3NMQeXsgykJn5kFz3fv24VSyLk88AmYu1VhzQk=";
            String data = "NfcRa9Lu/Lf9CpJ3YN3cnGbZbdu5pyDd2teeMgpwwxbhore2O+eANe9sUbp/oPduabtaTO5lc8L4OStN/e0qlm9ryCD8YRwN6Je3fXyb+uPfPZGDrdmhf/WJrOV/8RzuFZdgdPA1RHBxqpCfpR3ao3R6eY9mBZ/nnMwk+bFwQjDci5tiVFT685uiQGfgh1uUsxxCZcEYKBCtqJO9NdgLoTp5+0GgRJKe/+qANb0hTFMtYhP5/zOU2ttcR+xF1NKuMHKuasMC8TTtF0zsp7Xcq6op/lZ0jIhgHpUZZ+zv+wcp8c3dkm7aCQaa6XqtfGqAQHIQaNXcrgBXnTqSUDBFNnBhXHYoPh0qHZXDC9MIQ5s4gMjiXcTQe2PqHtvphZkuwKuf4/Ih/A/wkf6y50SSxRqpn3hgFYMvuRYAGWMi9x4u+++Mip+4pjzrK6gfpY1xmWwfYfTa/vY00xM+Boebe0iAJfZS1vk26n/A9SYHvW6VXTTtviy5YkzJZVKppdIwJvMO+G+z8BB3aFt9bQnFXtNzzdXs0/c15on8XookpRGOeLAhJB9mI7WrSrjcGkqRfScwAVsJid5suLy/0WetA3JU8Plh9Sdc8BZkgWjKUnyYmZw3Bt5OpCPIWcCQzUts98GWqSZwHH/vwepHJvaojbFdXRF8wZH4GbqPwYSpuusyo8Pb0ozCS6iwtamx6a4nYplpwSiUHGFrOoCg2mRGEs2VB4xe9hl50Zb1m/aP15Q5FvuphsVDam/JrXMIgVmQjQhNiTKqG5rSJKosaL1BIWSRcWIZZA4iDMCVbSTQnqhP/t99PR8Ro8v68mbPViAldQSvsNz1b+NUX+IlRbLXViLRWvyrnDosJcpeHIAjViNvz6xxF4Xmhv5QwT2z80o4i0BVLMqdQvPjwrebwiYpTOQlgrfymxnZ17/TIS/06cvTZ7Vt++fQDlwA7LVCSq7v5wrCsUGPMQKCUb3KjlfhetNAdEfpo9DEwQrblb/dxmV0hXpqLjDQSMqRqCDxLVlwMzEgAq/g/2hll1RoXCxpJ3REv3VQBa3osdJ2KOUZzP1rdHikGsbKYKuLbNylyiF5VTGYApnKzvwOXLjdz1ixt2o1qQELOHoAJ0Hn+rxsz2gy1jd90O+JKLUodrYGxjR1JZuHCsATuhmnkQXJkrkRmVxnkg4B3CCGZfXepnTfAhJuANdkOWpNN9afACM833MshDrLJJIr6WugDOdoLdNHInbn57UMy2xdHCaLPFJbUQksaGZWLxM2cbGg1P2H9EmoWHcP8Ns7GzDpGd25Xl0veHLSoFcJF2XRcWScQDfawqW2pHeskq/c0Mr6RILjOpBnbBQVX1QJZ8fHGTXQ0MJflKctko+sXesi4wzBQk6XKICp+nhzXzgqUM9UaeHoikVhTfpkPWCvRnT+8wMkEtAJ/mHWTV6hhEay8DhaYjbCoDfsuNfjnR1zWy1kNJvZZdjEciLVXotg4wk2lJBInD3cIwE=";

            Abstract paymentResponse = Abstract.factoryFromEncrypted(envKey, data, privateKey);

            if (paymentResponse instanceof ro.mobilPay.payment.request.Card) {
                Card cardResponse = (Card) paymentResponse;
                // Process cardResponse...
                System.out.println("Successfully processed card response.");
            }
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }
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

    // Helper method to set Clob data, handling different Clob types
    private static void setClobData(Clob[] clobArray, String data) throws Exception {
        if (clobArray[0] instanceof javax.sql.rowset.serial.SerialClob) {
            // If it's a SerialClob, we need to replace it
            clobArray[0] = new javax.sql.rowset.serial.SerialClob(data.toCharArray());
        } else {
            // For Oracle CLOBs and other Clob implementations, use setString
            clobArray[0].setString(1, data);
        }
    }

    public static void initiatePaymentSetClob(String certificate, String xmlData, String paymentUrl, Clob[] responseClob, String[] errorDetails) {
        try {
            Clob[] dataParameter = new Clob[1];
            Clob[] envKeyParameter = new Clob[1];

            dataParameter[0] = new javax.sql.rowset.serial.SerialClob(new char[0]);
            envKeyParameter[0] = new javax.sql.rowset.serial.SerialClob(new char[0]);

            // Call preparePaymentRequest to get the data and envKey
            preparePaymentRequest(certificate, xmlData, dataParameter, envKeyParameter, errorDetails);

            if (dataParameter[0] == null || envKeyParameter[0] == null) {
                // If preparePaymentRequest failed, errorDetails will already be set
                responseClob[0] = null;
                return;
            }

            String data = clobToString(dataParameter[0]);
            String envKey = clobToString(envKeyParameter[0]);

            System.out.println("envKey: " + envKey);
            System.out.println("data: " + data);

            // envKey = "i/TU00pwR1lBhI2i3rL3Dc33fkdDVLWLmyR/xOROjLIowZNsug+OBkoF5SYK/30qx1026E0pbhECztKe6AzyBouZsl2b4zyVVgqS3sBr3dQCjbEJK0mfmBII3IJpd9l6b3a4un1NAskV5matnxlBojxEEB822ixTia51VD5AI94=";
            // data = "flTQwG73kOnVcid05zwt4P5W6PD8K3kgzvBo+Ll73Pdd7OyCSrVS2N0lLOA/YK9BMq2B9eC+z2BmZPiNPPe2yYHnsUiynAEoVbvQanVTWlP2H0UoyHwKdlEv8hHWkeOFU7cyx1aq+Q6ueWeRjTZ6MhrRXzrxvCJSvtpqKQRKFSUbFxzyGJjoTurRCHS03TcuA1EjPxePV7KcbSui9xXhyankQ71aktVrRxt9x/KBkFLueTOB+6kEL1bGKYCsfoKUWVBYIPpGnsGtCObQELYEJ1IrzNgOwU6RTvUiwD2Ooixge3w593g84Fb8mda1POZiQwri/Ls58g8e4MWxW27dskwMym8FfoykqJ0yZyV4hL0ehLt+asWqIt4FOOg9Uu8inMPK2MPkTADfkMmdEhOlawrg8Es5ghRlcCvZ6wuxZc0g//v5YAcpa9rW6z8pE//Ae9VuF0RhrbU86YDrIFXNJ3xjTXoU86R9fHWep9TtnH3nivYpVlSw9lTxP6MvvxbHWmHA4zelmGbZ4MIobYfnh6wkTyc=";

            // URL encode the key and data parameters
            String encodedKeyParam = URLEncoder.encode(envKey, StandardCharsets.UTF_8.name());
            String encodedDataParam = URLEncoder.encode(data, StandardCharsets.UTF_8.name());

            // Construct the form data string
            String formData = String.format("env_key=%s&data=%s", encodedKeyParam, encodedDataParam);

            // Create URL and HttpURLConnection
            URL url = new URL(paymentUrl);
            HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
            httpConnection.setRequestMethod("POST");
            httpConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            httpConnection.setDoOutput(true);

            // Send the request
            try (OutputStream os = httpConnection.getOutputStream()) {
                byte[] input = formData.getBytes(StandardCharsets.UTF_8.name());
                os.write(input, 0, input.length);
            }

            // Get the response
            int statusCode = httpConnection.getResponseCode();
            InputStream is = (statusCode == 200) ? httpConnection.getInputStream() : httpConnection.getErrorStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8.name()));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            String responseBody = response.toString();
            if (statusCode == 200 && !responseBody.contains("id=\"verifyAndPay\"")) {
                // Write to the Clob using setString method
                responseClob[0].setString(1, responseBody);
            } else {
                String errorMessage = "Error: " + (responseBody.contains("id=\"verifyAndPay\"") ? "VerifyAndPay error" : "Status Code: " + statusCode) + ", Response: " + responseBody;
                System.out.println(errorMessage);
                errorDetails[0] = errorMessage;
                responseClob[0] = null; // Handle error appropriately
            }
        } catch (Exception e) {
            // Capture the full stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String fullStackTrace = sw.toString();

            String errorMessage = "Error in initiatePaymentSetClob: " + e.getMessage() + "\n" + fullStackTrace;
            System.err.println(errorMessage); // Log to standard error
            errorDetails[0] = errorMessage;
            responseClob[0] = null;
        }
    }

    // Helper method to convert Clob to String
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

    private static String readCertificateFromFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Error reading certificate file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
}