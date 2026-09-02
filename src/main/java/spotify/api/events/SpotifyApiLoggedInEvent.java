package spotify.api.events;

import org.springframework.context.ApplicationEvent;

public class SpotifyApiLoggedInEvent extends ApplicationEvent {
  public SpotifyApiLoggedInEvent(Object source) {
    super(source);
  }
}
