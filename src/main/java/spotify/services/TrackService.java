package spotify.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.api.SpotifyCall;
import spotify.api.events.SpotifyApiException;
import spotify.util.SpotifyLogger;
import spotify.util.data.AlbumTrackPair;

@SuppressWarnings("unused")
@Service
public class TrackService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 10;

	private final SpotifyApi spotifyApi;

  TrackService(SpotifyApi spotifyApi, SpotifyLogger spotifyLogger) {
		this.spotifyApi = spotifyApi;
  }

	/**
	 * Get all songs IDs of the given list of albums, categorized as
	 * {@link AlbumTrackPair}
	 *
	 * @param albums the albums to get the tracks from
	 * @return the result in a list of AlbumTrackPairs
	 */
	public List<AlbumTrackPair> getTracksOfAlbums(List<AlbumSimplified> albums) throws SpotifyApiException {
		List<AlbumTrackPair> albumTrackPairs = new ArrayList<>();
		for (AlbumSimplified as : albums) {
			AlbumTrackPair tracksOfSingleAlbum = getTracksOfSingleAlbum(as);
			albumTrackPairs.add(tracksOfSingleAlbum);
		}
		return albumTrackPairs;
	}

	/**
	 * Get the tracks of the given album
	 * 
	 * @param album the album
	 * @return the AlbumTrackPair with the results
	 */
	public AlbumTrackPair getTracksOfSingleAlbum(AlbumSimplified album) throws SpotifyApiException {
		List<TrackSimplified> tracksOfAlbum = SpotifyCall.executePaging(spotifyApi
			.getAlbumTracks(album.getId())
			.limit(MAX_PLAYLIST_TRACK_FETCH_LIMIT));
		return AlbumTrackPair.of(album, tracksOfAlbum);
	}

	/**
	 * Search for a single track on Spotify. It will attempt to find an exact name match within the first
	 * ten results first. If it's unable to find any, the first result is returned.
	 *
	 * @param trackName the track name to search for
	 * @param artistName the artist to search for
	 * @return the most relevant single search result (null if none found)
	 */
	public Track searchTrack(String trackName, String artistName) {
		Track[] searchResults = SpotifyCall.execute(spotifyApi.searchTracks(artistName + " " + trackName).limit(10)).getItems();
		if (searchResults.length > 0) {
			return Arrays.stream(searchResults)
				.filter(track -> trackName.equalsIgnoreCase(track.getName()))
				.findFirst()
				.orElse(searchResults[0]);
		}
		return null;
	}
}
