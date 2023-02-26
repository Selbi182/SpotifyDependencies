package spotify.api;

import java.awt.Desktop;
import java.awt.HeadlessException;
import java.io.IOException;
import java.net.URI;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import spotify.api.events.SpotifyApiLoggedInEvent;
import spotify.config.SpotifyApiConfig;
import spotify.util.SpotifyLogger;
import spotify.util.SpotifyUtils;

@Component
@RestController
public class SpotifyApiAuthorization {
  protected static final String LOGIN_CALLBACK_URI = "/login-callback";

  private static final long LOGIN_TIMEOUT = 10;

  private final SpotifyApi spotifyApi;
  private final SpotifyApiConfig config;
  private final SpotifyApiScopes spotifyApiScopes;
  private final SpotifyLogger log;
  private final ApplicationEventPublisher applicationEventPublisher;

  /**
   * Authentication mutex to be used while the user is being prompted to log in
   */
  private static final Semaphore lock = new Semaphore(0);

  private SpotifyApiAuthorization(SpotifyApi spotifyApi, SpotifyApiConfig config, SpotifyApiScopes spotifyApiScopes, SpotifyLogger spotifyLogger, ApplicationEventPublisher applicationEventPublisher) {
    this.spotifyApi = spotifyApi;
    this.config = config;
    this.spotifyApiScopes = spotifyApiScopes;
    this.log = spotifyLogger;
    this.applicationEventPublisher = applicationEventPublisher;
    SpotifyCall.spotifyApiAuthorization = this;
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
      AuthorizationCodeUriRequest.Builder authorizationCodeUriBuilder = spotifyApi.authorizationCodeUri();
      String scopes = SpotifyUtils.buildScopes(spotifyApiScopes.requiredScopes());
      if (!scopes.isBlank()) {
        authorizationCodeUriBuilder.scope(scopes);
      }
      URI uri = SpotifyCall.execute(authorizationCodeUriBuilder);
      try {
        if (!Desktop.isDesktopSupported()) {
          throw new HeadlessException();
        }
        Desktop.getDesktop().browse(uri);
      } catch (IOException | HeadlessException e) {
        log.warning("Couldn't open browser window. Please log in at this URL:");
        System.out.println(uri.toString());
      }
      if (!lock.tryAcquire(LOGIN_TIMEOUT, TimeUnit.MINUTES)) {
        throw new InterruptedException();
      }
    } catch (InterruptedException | SpotifyApiException e) {
      log.error("Login timeout! Shutting down application in case of a Spotify Web API anomaly!");
      System.exit(182);
    }
  }

  /**
   * Callback receiver for logins
   *
   * @param code the authorization code from the Spotify API
   * @return a response entity indicating that the login was successful
   */
  @RequestMapping(LOGIN_CALLBACK_URI)
  private ResponseEntity<String> loginCallback(@RequestParam String code) {
    AuthorizationCodeCredentials acc = SpotifyCall.execute(spotifyApi.authorizationCode(code));
    updateTokens(acc);
    lock.release();
    return ResponseEntity.ok("Successfully logged in!");
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
        Set<String> requiredScopes = Set.copyOf(spotifyApiScopes.requiredScopes());
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
      String msg = "Failed to automatically login in. A manual (re-)login is required.";
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
}
