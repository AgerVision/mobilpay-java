package ro.calendarulmeu;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.model.SecretBundle;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import java.util.Optional;

public class SecretManager {
    private static final long serialVersionUID = 1L;

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
}
