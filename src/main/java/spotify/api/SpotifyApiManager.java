package spotify.api;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.apache.hc.core5.net.URIBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import spotify.api.events.SpotifyApiException;
import spotify.api.events.SpotifyApiLoggedInEvent;
import spotify.config.SpotifyApiConfig;
import spotify.spring.SpringPortConfig;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyUtils;

@Component
@RestController
public class SpotifyApiManager {
  protected static final String LOGIN_CALLBACK_URI = "/callback";
  
  private static final String CUSTOM_REDIRECT_URI_FROM_ENV = "redirect_uri";

  private static final long LOGIN_TIMEOUT = 10;

  private final SpotifyApiConfig config;
  private final SpotifyDependenciesSettings spotifyDependenciesSettings;
  private final SpotifyLogger log;
  private final ApplicationEventPublisher applicationEventPublisher;

  private final URI redirectUri;

  private SpotifyApi spotifyApi;

  /**
   * Authentication mutex to be used while the user is being prompted to log in
   */
  private final Semaphore lock = new Semaphore(0);

  private SpotifyApiManager(SpotifyApiConfig config,
      SpotifyDependenciesSettings spotifyDependenciesSettings,
      SpotifyLogger spotifyLogger,
      SpringPortConfig springPortConfig,
      ApplicationEventPublisher applicationEventPublisher) throws URISyntaxException {
    this.config = config;
    this.spotifyDependenciesSettings = spotifyDependenciesSettings;
    this.log = spotifyLogger;
    this.applicationEventPublisher = applicationEventPublisher;

    this.redirectUri = generateRedirectUri(springPortConfig.getPort());

    SpotifyCall.spotifyApiManager = this;
  }

  /////////////////////

  /**
   * Callback receiver for logins
   *
   * @param code the authorization code from the Spotify API
   * @return a response entity indicating that the login was successful
   */
  @RequestMapping(LOGIN_CALLBACK_URI)
  public ResponseEntity<String> loginCallback(@RequestParam String code) {
    AuthorizationCodeCredentials acc = SpotifyCall.execute(spotifyApi.authorizationCode(code));
    updateTokens(acc);
    lock.release();
    return ResponseEntity.ok("Successfully logged in!");
  }

  /////////////////////

  /**
   * A general purpose SpotifyAPI instance.
   */
  @Bean
  SpotifyApi spotifyApi() {
    SpotifyApi spotifyApi = createSpotifyApi(config.spotifyBotConfig().getAccessToken(), config.spotifyBotConfig().getRefreshToken());
    this.spotifyApi = spotifyApi;
    return spotifyApi;
  }

  private SpotifyApi createSpotifyApi(String accessToken, String refreshToken) {
    return new SpotifyApi.Builder()
      .setClientId(config.spotifyBotConfig().getClientId())
      .setClientSecret(config.spotifyBotConfig().getClientSecret())
      .setRedirectUri(redirectUri)
      .setAccessToken(accessToken)
      .setRefreshToken(refreshToken)
      .build();
  }

  /////////////////////

  @EventListener(ApplicationReadyEvent.class)
  public void initialLogin() {
    refresh();
    applicationEventPublisher.publishEvent(new SpotifyApiLoggedInEvent(this));
  }

  public String refresh() {
    try {
      return authorizationCodeRefresh();
    } catch (IllegalStateException e) {
      authenticate();
      return refresh();
    }
  }

  ///////////////////////

  /**
   * Authentication process
   */
  private void authenticate() {
    try {
      AuthorizationCodeUriRequest.Builder authorizationCodeUriBuilder = spotifyApi.authorizationCodeUri().redirect_uri(redirectUri);

      String scopes = SpotifyUtils.buildScopes(spotifyDependenciesSettings.requiredScopes());
      if (!scopes.isBlank()) {
        authorizationCodeUriBuilder.scope(scopes);
      }

      URI uri = SpotifyCall.execute(authorizationCodeUriBuilder);

      log.info("Spotify authorization URL: ");
      log.logAtLevel(uri.toString(), SpotifyLogger.Level.INFO, false); // to avoid truncation
      log.info("Trying to open authorization URL in browser...");
      try {
        if (!Desktop.isDesktopSupported()) {
          throw new HeadlessException();
        }
        Desktop.getDesktop().browse(uri);
      } catch (Throwable e) { // must be Throwable because some systems get an UnsatisfiedLinkERROR if AWT is missing (not just an Exception)
        log.warning("Couldn't open browser window! Please copy-paste the authorization URL manually into your browser and follow the login steps");
      }
      if (!lock.tryAcquire(LOGIN_TIMEOUT, TimeUnit.MINUTES)) {
        throw new InterruptedException();
      }
    } catch (SpotifyApiException | InterruptedException e) {
      log.error("Login timeout! Shutting down application in case of a Spotify Web API anomaly!");
      System.exit(182);
    }
  }

  ///////////////////////

  /**
   * Refresh the access token
   *
   * @return the new access token
   */
  private String authorizationCodeRefresh() throws IllegalStateException {
    try {
      if (spotifyApi.getAccessToken() != null && spotifyApi.getRefreshToken() != null) {
        AuthorizationCodeCredentials acc = SpotifyCall.execute(spotifyApi.authorizationCodeRefresh());
        Set<String> requiredScopes = Set.copyOf(spotifyDependenciesSettings.requiredScopes());
        Set<String> accScopes = Set.of(acc.getScope().split(" "));
        if (!accScopes.containsAll(requiredScopes)) {
          throw new IllegalStateException("New required scopes have been added. A re-login is required");
        }
        updateTokens(acc);
        return acc.getAccessToken();
      } else {
        throw new IllegalStateException("Access and/or Refresh Tokens missing");
      }
    } catch (Exception e) {
      String msg = "Failed to automatically login. A manual (re-)login is required.";
      log.error(msg);
      throw e;
    }
  }

  /**
   * Store the access and refresh tokens in the settings
   */
  private void updateTokens(AuthorizationCodeCredentials acc) {
    String accessToken = spotifyApi.getAccessToken();
    if (acc.getAccessToken() != null) {
      accessToken = acc.getAccessToken();
    }
    String refreshToken = spotifyApi.getRefreshToken();
    if (acc.getRefreshToken() != null) {
      refreshToken = acc.getRefreshToken();
    }

    spotifyApi.setAccessToken(accessToken);
    spotifyApi.setRefreshToken(refreshToken);
    try {
      config.updateTokens(accessToken, refreshToken);
    } catch (IOException e) {
      log.error("Failed to update tokens in the properties file! These will get lost during a server restart.");
      e.printStackTrace();
    }
  }

  /////////////////////

  private URI generateRedirectUri(int port) throws URISyntaxException {
    String redirectUriFromEnv = System.getenv(CUSTOM_REDIRECT_URI_FROM_ENV);
    if (redirectUriFromEnv != null) {
      if (!redirectUriFromEnv.endsWith(SpotifyApiManager.LOGIN_CALLBACK_URI)) {
        throw new IllegalStateException("'" + CUSTOM_REDIRECT_URI_FROM_ENV + "' must end with " + SpotifyApiManager.LOGIN_CALLBACK_URI);
      }
      return SpotifyHttpManager.makeUri(redirectUriFromEnv);
    }
    return new URIBuilder()
      .setScheme("http")
      .setHost("127.0.0.1")
      .setPort(port)
      .setPath(SpotifyApiManager.LOGIN_CALLBACK_URI)
      .build();
  }

}
