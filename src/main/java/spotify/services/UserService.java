package spotify.services;

import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.User;
import spotify.api.SpotifyCall;

@Service
public class UserService {
  private final SpotifyApi spotifyApi;

  private User currentUserCached;

  UserService(SpotifyApi spotifyApi) {
    this.spotifyApi = spotifyApi;
  }

  /**
   * Get the current user. Successive calls to this method will be taken from the cache to save on resources.
   * This is because the current user likely never changes.
   *
   * @return the current user
   */
  public User getCurrentUser() {
    return getCurrentUser(true);
  }

  /**
   * Get the current user.
   *
   * @param cache whether to get the information from cache or not
   * @return the current user
   */
  public User getCurrentUser(boolean cache) {
    if (!cache || this.currentUserCached == null) {
      this.currentUserCached = SpotifyCall.execute(spotifyApi.getCurrentUsersProfile());
    }
    return this.currentUserCached;
  }

  /**
   * Get the country code (market) of the current cached user
   *
   * @return the country code
   */
  public CountryCode getMarketOfCurrentUser() {
    return getCurrentUser().getCountry();
  }
}
