package spotify.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Service;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.AudioFeatures;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.api.BotException;
import spotify.api.SpotifyCall;
import spotify.util.BotLogger;
import spotify.util.data.AlbumTrackPair;

@Service
public class TrackService {

	private final static int MAX_PLAYLIST_TRACK_FETCH_LIMIT = 50;

	private final SpotifyApi spotifyApi;
	private final BotLogger log;

	TrackService(SpotifyApi spotifyApi, BotLogger botLogger) {
		this.spotifyApi = spotifyApi;
		this.log = botLogger;
	}

	/**
	 * Get all songs IDs of the given list of albums, categorized as
	 * {@link AlbumTrackPair}
	 *
	 * @param albums the albums to get the tracks from
	 * @return the result in a list of AlbumTrackPairs
	 */
	public List<AlbumTrackPair> getTracksOfAlbums(List<AlbumSimplified> albums) throws BotException {
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
	public AlbumTrackPair getTracksOfSingleAlbum(AlbumSimplified album) throws BotException {
		List<TrackSimplified> tracksOfAlbum = SpotifyCall.executePaging(spotifyApi
			.getAlbumsTracks(album.getId())
			.limit(MAX_PLAYLIST_TRACK_FETCH_LIMIT));
		return AlbumTrackPair.of(album, tracksOfAlbum);
	}

	/**
	 * Get the audio features for every track in the given list
	 * 
	 * @param tracks the tracks
	 * @return the audio features (in the same order as the passed tracks)
	 */
	public List<AudioFeatures> getAudioFeatures(List<TrackSimplified> tracks) {
		try {
			String[] trackIds = tracks.stream().map(TrackSimplified::getId).toArray(String[]::new);
			AudioFeatures[] audioFeatures = SpotifyCall.execute(spotifyApi.getAudioFeaturesForSeveralTracks(trackIds));
			return Arrays.asList(audioFeatures);
		} catch (BotException e) {
			log.stackTrace(e);
		}
		return null;
	}
}
