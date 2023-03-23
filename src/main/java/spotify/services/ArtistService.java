package spotify.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.ModelObjectType;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.Paging;
import spotify.api.SpotifyApiException;
import spotify.api.SpotifyCall;
import spotify.util.SpotifyUtils;

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
	 * @param artistIds artist IDs
	 * @return the artists
	 */
	public List<Artist> getArtists(List<String> artistIds) {
		List<Artist> allArtists = new ArrayList<>();

		List<List<String>> artistIdsPartitioned = SpotifyUtils.partitionList(artistIds, MAX_ARTIST_FETCH_LIMIT);
		for (List<String> artistIdPartition : artistIdsPartitioned) {
			String[] artistIdsArray = artistIdPartition.toArray(String[]::new);
			Artist[] artists = SpotifyCall.execute(spotifyApi.getSeveralArtists(artistIdsArray));
			allArtists.addAll(Arrays.asList(artists));
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

	/**
	 * Search for the given artist name and return up to 50 results (ordered from most to least relevant).
	 *
	 * @param searchArtistString the artist string to search for
	 * @return the results (up to 50)
	 */
	public List<Artist> searchArtists(String searchArtistString) {
		Paging<Artist> execute = SpotifyCall.execute(spotifyApi.searchArtists(searchArtistString).limit(MAX_ARTIST_FETCH_LIMIT));
		return Arrays.asList(execute.getItems());
	}

	/**
	 * Search for the given artist name and return ALL results (ordered from most to least relevant).
	 *
	 * @param searchArtistString the artist string to search for
	 * @return the results
	 */
	public List<Artist> searchAllArtists(String searchArtistString) {
		return SpotifyCall.executePaging(spotifyApi.searchArtists(searchArtistString).limit(MAX_ARTIST_FETCH_LIMIT));
	}
}
