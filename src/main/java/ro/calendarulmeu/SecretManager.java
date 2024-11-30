package ro.calendarulmeu;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;

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
}
