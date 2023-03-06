package spotify.api;

import java.io.File;
import java.util.List;

public interface SpotifyDependenciesSettings {

  /**
   * List of required Spotify API scopes for your app. Create a new class that extends this interface
   * and make sure it is the only one that does so. If you don't need any special scopes, just return an empty list.
   *
   * @return the list of required scopes
   */
  List<String> requiredScopes();

  /**
   * The server port to use for this application. If none is specified, 8080 will be used.
   *
   * @return the server port
   */
  default int port() {
    return 8080;
  }

  /**
   * Whether to log anything externally or not.
   *
   * @return true if external logging is activated, false if not
   */
  default boolean enableExternalLogging() {
    return false;
  }

  /**
   * The base path for all configuration files, such as spotifybot.properties
   *
   * @return the base path, working directy by default
   */
  default File configFilesBase() {
    return new File(".");
  }
}
