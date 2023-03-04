package spotify.api;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import spotify.config.SpotifyApiConfig;
import spotify.spring.SpringPortConfig;

@Configuration
public class SpotifyApiWrapper {

  private final SpotifyApiConfig config;
  private final SpringPortConfig springPortConfig;

  @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
  SpotifyApiWrapper(SpotifyApiConfig config, SpringPortConfig springPortConfig) {
    this.config = config;
    this.springPortConfig = springPortConfig;
  }

  /**
   * Creates a SpotifyApi instance with the most common settings. A
   * pre-configuration from the settings is taken first.
   *
   * @return the API instance
   */
  @Bean
  SpotifyApi spotifyApi() {
    SpotifyApi spotifyApi = new SpotifyApi.Builder()
        .setClientId(config.spotifyBotConfig().getClientId())
        .setClientSecret(config.spotifyBotConfig().getClientSecret())
        .setRedirectUri(generateRedirectUri())
        .build();
    spotifyApi.setAccessToken(config.spotifyBotConfig().getAccessToken());
    spotifyApi.setRefreshToken(config.spotifyBotConfig().getRefreshToken());
    return spotifyApi;
  }

  private URI generateRedirectUri() {
    String redirectUri = "http://localhost:" + springPortConfig.getPort() + SpotifyApiAuthorization.LOGIN_CALLBACK_URI;

    String redirectUriFromEnv = System.getenv("redirect_uri");
    if (redirectUriFromEnv != null) {
      if (!redirectUriFromEnv.endsWith(SpotifyApiAuthorization.LOGIN_CALLBACK_URI)) {
        throw new IllegalStateException("'redirect_uri' must end with /login-callback");
      }
      redirectUri = redirectUriFromEnv;
    }

    return SpotifyHttpManager.makeUri(redirectUri);
  }
}
