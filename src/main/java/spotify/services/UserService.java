package spotify.services;

import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.User;
import spotify.api.SpotifyCall;

@Service
public class UserService {
  private final SpotifyApi spotifyApi;

  UserService(SpotifyApi spotifyApi) {
    this.spotifyApi = spotifyApi;
  }

  /**
   * Get the current user
   *
   * @return the current user
   */
  public User getCurrentUser() {
    return SpotifyCall.execute(spotifyApi.getCurrentUsersProfile());
  }

  /**
   * Get the country code (market) of the current user
   *
   * @return the country code
   */
  public CountryCode getMarketOfCurrentUser() {
    return getCurrentUser().getCountry();
  }
}
