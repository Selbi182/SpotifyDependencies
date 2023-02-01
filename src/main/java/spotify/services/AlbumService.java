package spotify.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import spotify.api.BotException;
import spotify.api.SpotifyCall;

@Service
public class AlbumService {
	private final static int MAX_ALBUM_FETCH_LIMIT = 50;

	private final SpotifyApi spotifyApi;

	AlbumService(SpotifyApi spotifyApi) {
		this.spotifyApi = spotifyApi;
	}

	/**
	 * Fetch all albums of the given artists. (Note: This will very likely take up
	 * the majority of the crawling process, as it requires firing at least one
	 * Spotify Web API request for EVERY SINGLE ARTIST!)
	 * 
	 * @param artists the list of artists to search up the albums from
	 * @param enabledAlbumGroups the AlbumGroups to look for
	 * @param market the market
	 * @return the albums
	 */
	public List<AlbumSimplified> getAllAlbumsOfArtists(List<String> artists, Set<AlbumGroup> enabledAlbumGroups, CountryCode market) throws BotException {
		String albumGroupString = createAlbumGroupString(enabledAlbumGroups);

		// I've tried just about anything you can imagine. Parallel streams, threads,
		// thread pools, custom sleep intervals. It doesn't matter. Going through
		// every single artist in a simple for-each is just as fast as any more advanced
		// solution, while still being way more straightforward and comprehensible.
		// I wish Spotify's API allowed for fetching multiple artists' albums at once.

		List<AlbumSimplified> albums = new ArrayList<>();
		for (String artist : artists) {
			List<AlbumSimplified> albumIdsOfSingleArtist = getAlbumIdsOfSingleArtist(artist, albumGroupString, market);
			albums.addAll(albumIdsOfSingleArtist);
		}
		return albums;
	}

	/**
	 * Creates the comma-delimited, lowercase String of album groups to search for
	 * 
	 * @param enabledAlbumGroups the enabled AlbumGroups
	 * @return the searchable string for AlbumGroups
	 */
	public String createAlbumGroupString(Set<AlbumGroup> enabledAlbumGroups) {
		return enabledAlbumGroups.stream()
				.map(AlbumGroup::getGroup)
				.collect(Collectors.joining(","));
	}

	/**
	 * Return the albums of a single given artist with the original ID intact (so they won't get lost in appears-on releases)
	 * 
	 * @param artistId the artist ID to check up
	 * @param albumGroups the AlbumGroup string
	 * @param market the market to check for
	 * @return the albums
	 */
	public List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups, CountryCode market) throws BotException {
		return SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(market)
			.limit(MAX_ALBUM_FETCH_LIMIT)
			.album_type(albumGroups));
	}
}
