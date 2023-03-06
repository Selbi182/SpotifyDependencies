package spotify.config;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import spotify.api.SpotifyDependenciesSettings;

@Configuration
public class SpotifyApiConfig {
    private static final String CLIENT_ID = "client_id";
    private static final String CLIENT_SECRET = "client_secret";
    private static final String ACCESS_TOKEN = "access_token";
    private static final String REFRESH_TOKEN = "refresh_token";

    private static final String PROPERTIES_FILE = "./spotifybot.properties";

    private final File propertiesFile;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    SpotifyApiConfig(SpotifyDependenciesSettings spotifyDependenciesSettings) {
        this.propertiesFile = new File(spotifyDependenciesSettings.configFilesBase(), PROPERTIES_FILE);
    }

    /**
     * Update the access and refresh tokens, both in the config object and
     * the settings
     *
     * @param accessToken  the new access token
     * @param refreshToken the new refresh token
     * @throws IOException on read/write failure
     */
    public void updateTokens(String accessToken, String refreshToken) throws IOException {
        spotifyBotConfig().setAccessToken(accessToken);
        spotifyBotConfig().setRefreshToken(refreshToken);

        spotifyApiProperties().setProperty(ACCESS_TOKEN, accessToken);
        spotifyApiProperties().setProperty(REFRESH_TOKEN, refreshToken);
        spotifyApiProperties().store(new FileOutputStream(propertiesFile), null);
    }

    ////////////////////
    // CONFIG DTOs

    @Bean
    public Properties spotifyApiProperties() {
        try {
            if (propertiesFile.exists()) {
                FileReader reader = new FileReader(propertiesFile);
                Properties properties = new Properties();
                properties.load(reader);
                return properties;
            } else {
                String clientIdEnv = System.getenv(CLIENT_ID);
                String clientSecretEnv = System.getenv(CLIENT_SECRET);
                if (clientIdEnv != null && clientSecretEnv != null) {
                    Properties properties = new Properties();
                    properties.setProperty(CLIENT_ID, clientIdEnv);
                    properties.setProperty(CLIENT_SECRET, clientSecretEnv);
                    return properties;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Failed to read " + propertiesFile + ". Terminating!");
            System.exit(1);
            return null;
        }
        throw new IllegalStateException(String.format("Failed to read %s and didn't find environment variables '%s' and '%s' as backup. Terminating!", propertiesFile, CLIENT_ID, CLIENT_SECRET));
    }

    /**
     * Returns the bot configuration. May be created if not present.
     *
     * @return the bot config
     */
    @Bean
    public OAuth2 spotifyBotConfig() {
        Properties properties = spotifyApiProperties();

        OAuth2 config = new OAuth2();
        config.setClientId(getNotBlankProperty(properties, CLIENT_ID));
        config.setClientSecret(getNotBlankProperty(properties, CLIENT_SECRET));
        config.setAccessToken(properties.getProperty(ACCESS_TOKEN));
        config.setRefreshToken(properties.getProperty(REFRESH_TOKEN));

        return config;
    }

    private String getNotBlankProperty(Properties properties, String propertyKey) {
        String property = properties.getProperty(propertyKey);
        if (property != null && !property.isBlank()) {
            return property;
        }
        throw new IllegalStateException("Missing required field in spotifybot.properties: " + propertyKey);
    }

    public static class OAuth2 {
        private String clientId;
        private String clientSecret;
        private String accessToken;
        private String refreshToken;

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public void setRefreshToken(String refreshToken) {
            this.refreshToken = refreshToken;
        }

        @Override
        public String toString() {
            return "OAuth2 [clientId=" + clientId + ", clientSecret=" + clientSecret + ", accessToken=" + accessToken + ", refreshToken=" + refreshToken + "]";
        }

    }
}