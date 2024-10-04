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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Clob;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.io.IOException;

import javax.sql.rowset.serial.SerialClob;

import ro.mobilPay.payment.request.Abstract;
import ro.mobilPay.payment.request.Card;
import ro.mobilPay.util.ListItem;
import ro.mobilPay.util.OpenSSL;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Do you want to test a request or a response? (Enter 'req' or 'res')");
        String choice = scanner.nextLine().trim().toLowerCase();

        if (choice.equals("req")) {
            test_request();
        } else if (choice.equals("res")) {
            test_response();
        } else {
            System.out.println("Invalid choice. Please run the program again and enter 'req' or 'res'.");
        }
        scanner.close();
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

    public static void test_response() {
        try {
            System.out.println("Testing response...");
            
            String bodyEnvKey = "7HbgKMMNe3HkW%2BM4BR7fz9Q8YzvkX0Hh0B4Pv7fr9wmjMNQs1cXgDxTQ0Uy%2FUglQXoKbzcnH%2FRDKbSubn85kOROlA7CLnpdoF7lcWO3tDB5vNbt5MqKMP1LP25xPX0DYT%2FXko3NMQeXsgykJn5kFz3fv24VSyLk88AmYu1VhzQk%3D";
            String bodyData = "NfcRa9Lu%2FLf9CpJ3YN3cnGbZbdu5pyDd2teeMgpwwxbhore2O%2BeANe9sUbp%2FoPduabtaTO5lc8L4OStN%2Fe0qlm9ryCD8YRwN6Je3fXyb%2BuPfPZGDrdmhf%2FWJrOV%2F8RzuFZdgdPA1RHBxqpCfpR3ao3R6eY9mBZ%2FnnMwk%2BbFwQjDci5tiVFT685uiQGfgh1uUsxxCZcEYKBCtqJO9NdgLoTp5%2B0GgRJKe%2F%2BqANb0hTFMtYhP5%2FzOU2ttcR%2BxF1NKuMHKuasMC8TTtF0zsp7Xcq6op%2FlZ0jIhgHpUZZ%2Bzv%2Bwcp8c3dkm7aCQaa6XqtfGqAQHIQaNXcrgBXnTqSUDBFNnBhXHYoPh0qHZXDC9MIQ5s4gMjiXcTQe2PqHtvphZkuwKuf4%2FIh%2FA%2Fwkf6y50SSxRqpn3hgFYMvuRYAGWMi9x4u%2B%2B%2BMip%2B4pjzrK6gfpY1xmWwfYfTa%2FvY00xM%2BBoebe0iAJfZS1vk26n%2FA9SYHvW6VXTTtviy5YkzJZVKppdIwJvMO%2BG%2Bz8BB3aFt9bQnFXtNzzdXs0%2Fc15on8XookpRGOeLAhJB9mI7WrSrjcGkqRfScwAVsJid5suLy%2F0WetA3JU8Plh9Sdc8BZkgWjKUnyYmZw3Bt5OpCPIWcCQzUts98GWqSZwHH%2FvwepHJvaojbFdXRF8wZH4GbqPwYSpuusyo8Pb0ozCS6iwtamx6a4nYplpwSiUHGFrOoCg2mRGEs2VB4xe9hl50Zb1m%2FaP15Q5FvuphsVDam%2FJrXMIgVmQjQhNiTKqG5rSJKosaL1BIWSRcWIZZA4iDMCVbSTQnqhP%2Ft99PR8Ro8v68mbPViAldQSvsNz1b%2BNUX%2BIlRbLXViLRWvyrnDosJcpeHIAjViNvz6xxF4Xmhv5QwT2z80o4i0BVLMqdQvPjwrebwiYpTOQlgrfymxnZ17%2FTIS%2F06cvTZ7Vt%2B%2BfQDlwA7LVCSq7v5wrCsUGPMQKCUb3KjlfhetNAdEfpo9DEwQrblb%2FdxmV0hXpqLjDQSMqRqCDxLVlwMzEgAq%2Fg%2F2hll1RoXCxpJ3REv3VQBa3osdJ2KOUZzP1rdHikGsbKYKuLbNylyiF5VTGYApnKzvwOXLjdz1ixt2o1qQELOHoAJ0Hn%2Brxsz2gy1jd90O%2BJKLUodrYGxjR1JZuHCsATuhmnkQXJkrkRmVxnkg4B3CCGZfXepnTfAhJuANdkOWpNN9afACM833MshDrLJJIr6WugDOdoLdNHInbn57UMy2xdHCaLPFJbUQksaGZWLxM2cbGg1P2H9EmoWHcP8Ns7GzDpGd25Xl0veHLSoFcJF2XRcWScQDfawqW2pHeskq%2Fc0Mr6RILjOpBnbBQVX1QJZ8fHGTXQ0MJflKctko%2BsXesi4wzBQk6XKICp%2BnhzXzgqUM9UaeHoikVhTfpkPWCvRnT%2B8wMkEtAJ%2FmHWTV6hhEay8DhaYjbCoDfsuNfjnR1zWy1kNJvZZdjEciLVXotg4wk2lJBInD3cIwE%3D";
            String cipher = "rc4";

            String envKey = URLDecoder.decode(bodyEnvKey, StandardCharsets.UTF_8.name());
            String data = URLDecoder.decode(bodyData, StandardCharsets.UTF_8.name());

            System.out.println("envKey: " + envKey);
            System.out.println("data: " + data);


            Abstract paymentResponse = Abstract.factoryFromEncrypted(envKey, data, data);
        } catch (Exception e) {
            System.out.println("An error occurred: " + e.getMessage());
            e.printStackTrace();
        }

        
    }

    public static void preparePaymentRequest(String certificate, String xmlData, Clob[] dataParameter, Clob[] envKeyParameter, String[] errorDetails) {
        try {
            // Initialize Bouncy Castle Provider
            OpenSSL.extraInit();

            // Use the factory method to create the appropriate Abstract object
            Abstract paymentRequest = Card.factory(xmlData);

            if (paymentRequest instanceof ro.mobilPay.payment.request.Card) {
                Card cardRequest = (ro.mobilPay.payment.request.Card) paymentRequest;

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

            String errorMessage = "Error in preparePaymentRequest: " + e.getMessage() + "\n" + fullStackTrace;
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
    private static String clobToString(Clob clob) throws Exception {
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