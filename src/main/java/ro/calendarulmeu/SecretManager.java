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
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SecretManager {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 16;
    private static KmsCryptoClient kmsClient;

    static {
        try {
            System.out.println("Initializing KMS client with instance principals...");
            InstancePrincipalsAuthenticationDetailsProvider provider = 
                InstancePrincipalsAuthenticationDetailsProvider.builder().build();
            
            // The region is available from the authentication provider
            String region = provider.getRegion().getRegionId();
            String endpoint = String.format("https://kms.%s.oraclecloud.com", region);
            System.out.println("Using KMS endpoint: " + endpoint);

            // Add instance info logging
            System.out.println("Instance Region: " + provider.getRegion().getRegionId());
            System.out.println("Instance Principals Authentication: " + (provider != null ? "Configured" : "Not Configured"));

            kmsClient = KmsCryptoClient.builder()
                    .endpoint(endpoint)
                    .build(provider);
            System.out.println("Successfully initialized KMS client with instance principals");
        } catch (Exception e) {
            System.err.println("Failed to initialize KMS client with instance principals: " + e.getMessage());
            if (e.getCause() != null) {
                System.err.println("Cause: " + e.getCause().getMessage());
            }
            e.printStackTrace();
            kmsClient = null;
        }
    }

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

    public static String encryptWithKms(String plainText, String masterKeyId, String vaultId) throws Exception {
        String endpoint = String.format("https://kms.%s.oraclecloud.com/20180608/crypto/%s", 
            getRegionFromProvider(), vaultId);
        
        try {
            System.out.println("Using KMS endpoint: " + endpoint);
            System.out.println("Attempting to encrypt with key: " + masterKeyId);
            
            KmsCryptoClient client = KmsCryptoClient.builder()
                .endpoint(endpoint)
                .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());

            EncryptDataDetails encryptDataDetails = EncryptDataDetails.builder()
                    .keyId(masterKeyId)
                    .plaintext(Base64.getEncoder().encodeToString(plainText.getBytes("UTF-8")))
                    .build();

            EncryptRequest encryptRequest = EncryptRequest.builder()
                    .encryptDataDetails(encryptDataDetails)
                    .build();

            System.out.println("Sending encrypt request to KMS...");
            EncryptResponse encryptResponse = client.encrypt(encryptRequest);
            System.out.println("Successfully encrypted data");
            
            return encryptResponse.getEncryptedData().getCiphertext();
        } catch (Exception e) {
            System.err.println("Failed to encrypt: " + e.getMessage());
            System.err.println("Key ID used: " + masterKeyId);
            throw e;
        }
    }

    public static String decryptWithKms(String cipherText, String masterKeyId, String vaultId) throws Exception {
        String endpoint = String.format("https://kms.%s.oraclecloud.com/20180608/crypto/%s", 
            getRegionFromProvider(), vaultId);
        
        KmsCryptoClient client = KmsCryptoClient.builder()
            .endpoint(endpoint)
            .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
        
        DecryptDataDetails decryptDataDetails = DecryptDataDetails.builder()
                .keyId(masterKeyId)
                .ciphertext(cipherText)
                .build();

        DecryptRequest decryptRequest = DecryptRequest.builder()
                .decryptDataDetails(decryptDataDetails)
                .build();

        DecryptResponse decryptResponse = client.decrypt(decryptRequest);
        return new String(Base64.getDecoder().decode(decryptResponse.getDecryptedData().getPlaintext()), "UTF-8");
    }

    private static String getRegionFromProvider() throws Exception {
        return InstancePrincipalsAuthenticationDetailsProvider.builder()
            .build()
            .getRegion()
            .getRegionId();
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

    public static String encryptWithSecret(String plainText, String secretId) throws Exception {
        try {
            // Get the AES key from the secret
            SecretInfo secretInfo = getSecret(secretId);
            String aesKey = secretInfo.getContent();

            // Use AES encryption with GCM mode
            return encryptAES(plainText, aesKey);
        } catch (Exception e) {
            System.err.println("Failed to encrypt with secret: " + e.getMessage());
            throw e;
        }
    }

    public static String decryptWithSecret(String cipherText, String secretId) throws Exception {
        try {
            // Get the AES key from the secret
            SecretInfo secretInfo = getSecret(secretId);
            String aesKey = secretInfo.getContent();

            // Use AES decryption with GCM mode
            return decryptAES(cipherText, aesKey);
        } catch (Exception e) {
            System.err.println("Failed to decrypt with secret: " + e.getMessage());
            throw e;
        }
    }
}
