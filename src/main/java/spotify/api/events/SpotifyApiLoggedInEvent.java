package spotify.api.events;

import org.springframework.context.ApplicationEvent;

public class SpotifyApiLoggedInEvent extends ApplicationEvent {
	private static final long serialVersionUID = 6985311786329483998L;

	public SpotifyApiLoggedInEvent(Object source) {
		super(source);
	}
}
