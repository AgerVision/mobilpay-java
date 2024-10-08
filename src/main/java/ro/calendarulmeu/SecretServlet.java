package ro.calendarulmeu;

import com.oracle.bmc.auth.InstancePrincipalsAuthenticationDetailsProvider;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;

public class SecretServlet {
    private static final long serialVersionUID = 1L;
    
    private static SecretsClient secretsClient;

    static {
        try {
            initializeSecretsClient();
        } catch (Exception e) {
            // Log the error or handle it appropriately
            e.printStackTrace();
        }
    }

    private static synchronized void initializeSecretsClient() throws Exception {
        if (secretsClient == null) {
            secretsClient = SecretsClient.builder()
                .build(InstancePrincipalsAuthenticationDetailsProvider.builder().build());
        }
    }

    public static String getSecret(String secretOcid) throws Exception {
        if (secretsClient == null) {
            initializeSecretsClient();
        }
        return retrieveSecret(secretsClient, secretOcid);
    }

    private static String retrieveSecret(SecretsClient secretsClient, String secretId) {
        Base64SecretBundleContentDetails contentDetails =
                (Base64SecretBundleContentDetails)
                        secretsClient
                                .getSecretBundle(
                                        GetSecretBundleRequest.builder()
                                                .secretId(secretId)
                                                .stage(GetSecretBundleRequest.Stage.Current)
                                                .build())
                                .getSecretBundle()
                                .getSecretBundleContent();
        return contentDetails.getContent();
    }
}