package spotify.api.events;

/**
 * A general wrapper for every type of Exception related to outside requests to
 * the Spotify Web API, most commonly (but not limited to) the
 * {@link se.michaelthelin.spotify.exceptions.SpotifyWebApiException}.
 */
@SuppressWarnings("unused")
public class SpotifyApiException extends RuntimeException {
  private final Exception nestedException;

  public SpotifyApiException(Exception e) {
    this.nestedException = e;
  }

  public Exception getNestedException() {
    return nestedException;
  }

  @Override
  public String toString() {
    return nestedException.toString();
  }
}
