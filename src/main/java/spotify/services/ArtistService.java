package spotify.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import spotify.api.SpotifyApiException;
import spotify.api.SpotifyCall;

@Service
public class ArtistService {

	private final static int MAX_ARTIST_FETCH_LIMIT = 50;

	private final SpotifyApi spotifyApi;

	ArtistService(SpotifyApi spotifyApi) {
		this.spotifyApi = spotifyApi;
	}

	/**
	 * Get several artists
	 *
	 * @param ids artist IDs
	 * @return the artists
	 */
	public List<Artist> getArtists(List<String> ids) {
		List<Artist> allArtists = new ArrayList<>();
		for (List<String> artistsPartition : Lists.partition(ids, MAX_ARTIST_FETCH_LIMIT)) {
			allArtists.addAll(Arrays.asList(SpotifyCall.execute(spotifyApi.getSeveralArtists(artistsPartition.toArray(String[]::new)))));
		}
		return allArtists;
	}

	/**
	 * Get the real artist IDs directly from the Spotify API
	 *
	 * @return the followed artists by the current user
	 */
	public List<Artist> getFollowedArtists() throws SpotifyApiException {
		return SpotifyCall.executePaging(spotifyApi
			.getUsersFollowedArtists(ModelObjectType.ARTIST)
			.limit(MAX_ARTIST_FETCH_LIMIT));
	}
}
