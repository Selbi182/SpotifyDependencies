package spotify.api;

import java.util.List;

public interface SpotifyApiScopes {

  /**
   * List of required Spotify API scopes for your app. Create a new class that extends this interface
   * and make sure it is the only one that does so. If you don't need any special scopes, just return an empty list.
   *
   * @return the list of required scopes
   */
  List<String> requiredScopes();
}
