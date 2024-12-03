package ro.calendarulmeu;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.keymanagement.KmsCryptoClient;
import com.oracle.bmc.keymanagement.requests.DecryptRequest;
import com.oracle.bmc.keymanagement.requests.EncryptRequest;
import com.oracle.bmc.keymanagement.responses.DecryptResponse;
import com.oracle.bmc.keymanagement.responses.EncryptResponse;
import com.oracle.bmc.keymanagement.model.DecryptDataDetails;
import com.oracle.bmc.keymanagement.model.EncryptDataDetails;

import java.util.Base64;
import java.util.Optional;

public class SecretManager {
    private SecretManager() {
        // Private constructor to prevent instantiation
    }

    public static SecretInfo getSecret(String secretOcid) throws Exception {
        return getSecret(secretOcid, Optional.empty());
    }

    public static SecretInfo getSecret(String secretOcid, Optional<Long> version) throws Exception {
        try (SecretsClient secretsClient = createSecretsClient()) {
            return retrieveSecret(secretsClient, secretOcid, version);
        }
    }

    private static SecretsClient createSecretsClient() throws Exception {
        return SecretsClient.builder()
            .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
    }

    private static SecretInfo retrieveSecret(SecretsClient secretsClient, String secretId, Optional<Long> version) {
        GetSecretBundleRequest.Builder requestBuilder = GetSecretBundleRequest.builder()
                .secretId(secretId);

        version.ifPresentOrElse(
            versionNumber -> requestBuilder.versionNumber(versionNumber),
            () -> requestBuilder.stage(GetSecretBundleRequest.Stage.Current)
        );

        SecretBundle secretBundle = secretsClient
                .getSecretBundle(requestBuilder.build())
                .getSecretBundle();

        Base64SecretBundleContentDetails contentDetails =
                (Base64SecretBundleContentDetails) secretBundle.getSecretBundleContent();

        return new SecretInfo(contentDetails.getContent(), secretBundle.getVersionNumber());
    }

    public static String encryptWithKms(String plainText, String masterKeyId, String cryptoEndpoint) throws Exception {
        try {
            InstancePrincipalsAuthenticationDetailsProvider provider = 
                InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            
            try (KmsCryptoClient client = KmsCryptoClient.builder()
                    .endpoint(cryptoEndpoint)
                    .build(provider)) {

                byte[] plainTextBytes = plainText.getBytes("UTF-8");
                String base64PlainText = Base64.getEncoder().encodeToString(plainTextBytes);

                EncryptDataDetails encryptDataDetails = EncryptDataDetails.builder()
                    .keyId(masterKeyId)
                    .plaintext(base64PlainText)
                    .build();

                EncryptRequest encryptRequest = EncryptRequest.builder()
                    .encryptDataDetails(encryptDataDetails)
                    .build();

                EncryptResponse encryptResponse = client.encrypt(encryptRequest);
                return encryptResponse.getEncryptedData().getCiphertext();
            }
        } catch (Exception e) {
            System.err.println("Failed to encrypt with KMS: " + e.getMessage());
            throw e;
        }
    }

    public static String decryptWithKms(String cipherText, String masterKeyId, String cryptoEndpoint) throws Exception {
        try {
            InstancePrincipalsAuthenticationDetailsProvider provider = 
                InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            
            try (KmsCryptoClient client = KmsCryptoClient.builder()
                    .endpoint(cryptoEndpoint)
                    .build(provider)) {

                DecryptDataDetails decryptDataDetails = DecryptDataDetails.builder()
                    .keyId(masterKeyId)
                    .ciphertext(cipherText)
                    .build();

                DecryptRequest decryptRequest = DecryptRequest.builder()
                    .decryptDataDetails(decryptDataDetails)
                    .build();

                DecryptResponse decryptResponse = client.decrypt(decryptRequest);
                
                byte[] decryptedBytes = Base64.getDecoder().decode(decryptResponse.getDecryptedData().getPlaintext());
                return new String(decryptedBytes, "UTF-8");
            }
        } catch (Exception e) {
            System.err.println("Failed to decrypt with KMS: " + e.getMessage());
            throw e;
        }
    }

    public static class SecretInfo {
        private final String content;
        private final Long version;

        public SecretInfo(String content, Long version) {
            this.content = content;
            this.version = version;
        }

        public String getContent() {
            return content;
        }

        public Long getVersion() {
            return version;
        }
    }

    public static class EncryptResult {
        private String encryptedData;
        private String version;

        public EncryptResult(String encryptedData, String version) {
            this.encryptedData = encryptedData;
            this.version = version;
        }

        public String getEncryptedData() { return encryptedData; }
        public void setEncryptedData(String encryptedData) { this.encryptedData = encryptedData; }
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
    }

    public static class DecryptResult {
        private final String decryptedData;

        public DecryptResult(String decryptedData) {
            this.decryptedData = decryptedData;
        }

        public String getDecryptedData() {
            return decryptedData;
        }
    }
}
