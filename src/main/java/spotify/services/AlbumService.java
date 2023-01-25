package spotify.services;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.neovisionaries.i18n.CountryCode;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Artist;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import spotify.api.BotException;
import spotify.api.SpotifyCall;
import spotify.util.BotUtils;

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
	public List<AlbumSimplified> getAllAlbumsOfArtists(List<String> artists, Collection<AlbumGroup> enabledAlbumGroups, CountryCode market) throws BotException {
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
	private String createAlbumGroupString(Collection<AlbumGroup> enabledAlbumGroups) {
		StringJoiner albumGroupsAsString = new StringJoiner(",");
		for (AlbumGroup ag : enabledAlbumGroups) {
			albumGroupsAsString.add(ag.getGroup());
		}
		return albumGroupsAsString.toString();
	}

	/**
	 * Return the albums of a single given artist with the original ID intact (so they won't get lost in appears-on releases)
	 * 
	 * @param artistId the artist ID to check up
	 * @param albumGroups the AlbumGroup string
	 * @param market the market to check for
	 * @return the albums
	 */
	private List<AlbumSimplified> getAlbumIdsOfSingleArtist(String artistId, String albumGroups, CountryCode market) throws BotException {
		List<AlbumSimplified> albumsOfCurrentArtist = SpotifyCall.executePaging(spotifyApi
			.getArtistsAlbums(artistId)
			.market(market)
			.limit(MAX_ALBUM_FETCH_LIMIT)
			.album_type(albumGroups));
		return attachOriginArtistIdForAppearsOnReleases(artistId, albumsOfCurrentArtist);
	}

	/**
	 * Attach the artist IDs for any appears_on releases, so they won't get lost down
	 * the way. For performance reasons, the proper conversion to an Artist object
	 * is done after the majority of filtering is completed (more specifically,
	 * after the previously cached releases have been removed).<br/>
	 * <br/>
	 * 
	 * See also {@link AlbumService#resolveViaAppearsOnArtistNames}.
	 * 
	 * @param artistId the artist ID
	 * @param albumsOfArtist the list of albums for this artist
	 * @return the extended album
	 */
	private List<AlbumSimplified> attachOriginArtistIdForAppearsOnReleases(String artistId, List<AlbumSimplified> albumsOfArtist) {
		List<AlbumSimplified> albumsExtended = new ArrayList<>();
		for (AlbumSimplified as : albumsOfArtist) {
			as = as.getAlbumGroup().equals(AlbumGroup.APPEARS_ON)
				? appendStringToArtist(artistId, as)
				: as;
			albumsExtended.add(as);
		}
		return albumsExtended;
	}

	/**
	 * Quick (and dirty) way to wrap the artist ID inside an ArtistSimplified and
	 * append it to the list of actual artists of this AlbumSimplified.
	 * 
	 * @param artistId the artist ID
	 * @param album the album
	 * @return the album with the artist string appended
	 */
	private AlbumSimplified appendStringToArtist(String artistId, AlbumSimplified album) {
		ArtistSimplified[] appendedArtists = new ArtistSimplified[album.getArtists().length + 1];

		ArtistSimplified wrappedArtistId = new ArtistSimplified.Builder()
			.setName(artistId)
			.build();
		appendedArtists[appendedArtists.length - 1] = wrappedArtistId;
		for (int i = 0; i < appendedArtists.length - 1; i++) {
			appendedArtists[i] = album.getArtists()[i];
		}

		// Builders can't copy-construct for some reason, so I got to copy everything
		// else over as well... Only keeping it to the important attributes though
		return album.builder()
			.setArtists(appendedArtists)
			.setAlbumGroup(album.getAlbumGroup())
			.setAlbumType(album.getAlbumType())
			.setId(album.getId())
			.setName(album.getName())
			.setReleaseDate(album.getReleaseDate())
			.setReleaseDatePrecision(album.getReleaseDatePrecision())
			.build();
	}

	/**
	 * Replace any appears_on releases' artists that were preserved in
	 * {@link AlbumService#attachOriginArtistIdForAppearsOnReleases}.
	 * 
	 * @param albums the albums to work with
	 * @return the new albums
	 * @throws BotException if anything goes wrong
	 */
	public List<AlbumSimplified> resolveViaAppearsOnArtistNames(List<AlbumSimplified> albums) throws BotException {
		List<String> relevantAppearsOnArtistsIds = albums.stream()
			.filter(album -> AlbumGroup.APPEARS_ON.equals(album.getAlbumGroup()))
			.map(BotUtils::getLastArtistName)
			.collect(Collectors.toList());

		Map<String, String> artistIdToName = new HashMap<>();
		for (List<String> sublistArtistIds : Lists.partition(relevantAppearsOnArtistsIds, 50)) {
			Artist[] execute = SpotifyCall.execute(spotifyApi.getSeveralArtists(sublistArtistIds.toArray(String[]::new)));
			for (Artist a : execute) {
				artistIdToName.put(a.getId(), a.getName());
			}
		}

		for (AlbumSimplified as : albums) {
			if (AlbumGroup.APPEARS_ON.equals(as.getAlbumGroup())) {
				String viaArtistId = BotUtils.getLastArtistName(as);
				String viaArtistName = artistIdToName.get(viaArtistId);
				if (viaArtistName != null) {
					ArtistSimplified viaArtistWithName = new ArtistSimplified.Builder()
						.setId(viaArtistId)
						.setName(String.format("(%s)", viaArtistName))
						.build();
					ArtistSimplified[] artists = as.getArtists();
					artists[artists.length - 1] = viaArtistWithName;
				}
			}
		}
		return albums;
	}
}
