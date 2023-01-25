package spotify.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.User;
import se.michaelthelin.spotify.requests.data.playlists.ChangePlaylistsDetailsRequest;
import spotify.api.SpotifyCall;

@Service
public class PlaylistService {
  private final static String TRACK_PREFIX = "spotify:track:";
  private final static int PLAYLIST_INTERACTION_LIMIT = 100;

  private final SpotifyApi spotifyApi;

  PlaylistService(SpotifyApi spotifyApi) {
    this.spotifyApi = spotifyApi;
  }

  /**
   * Get a playlist
   *
   * @param playlistId the id
   * @return the playlist
   */
  public Playlist getPlaylist(String playlistId) {
    return SpotifyCall.execute(spotifyApi.getPlaylist(playlistId));
  }

  /**
   * Get all tracks of the given playlist
   *
   * @param playlistId the playlist ID
   * @param offset the offset to start looking at within the playlist
   * @return the playlist tracks
   */
  public List<PlaylistTrack> getPlaylistTracks(String playlistId, int offset) {
    return SpotifyCall.executePaging(spotifyApi
        .getPlaylistsItems(playlistId)
        .offset(offset)
        .limit(PLAYLIST_INTERACTION_LIMIT));
  }

  /**
   * Add the given list of song IDs to the playlist (at the bottom)
   *
   * @param playlist the playlist to add the songs to
   * @param trackIds the track IDs to add
   */
  public void addSongsToPlaylistById(Playlist playlist, List<String> trackIds) {
    if (!trackIds.isEmpty()) {
      JsonArray json = new JsonArray();
      for (String id : trackIds) {
        json.add(TRACK_PREFIX + id);
      }
      SpotifyCall.execute(spotifyApi.addItemsToPlaylist(playlist.getId(), json));
    }
  }

  /**
   * Add the given list of song IDs to the playlist with position
   *
   * @param playlist the playlist to add the songs to
   * @param trackIds the track IDs to add
   * @param position the position to add the songs at
   */
  public void addSongsToPlaylistById(Playlist playlist, List<String> trackIds, int position) {
    if (!trackIds.isEmpty()) {
      JsonArray json = new JsonArray();
      for (String id : trackIds) {
        json.add(TRACK_PREFIX + id);
      }
      SpotifyCall.execute(spotifyApi.addItemsToPlaylist(playlist.getId(), json).position(position));
    }
  }

  /**
   * Remove every single song from the given playlist
   *
   * @param playlist the playlist to clear
   */
  public void clearPlaylist(Playlist playlist) {
    String playlistId = playlist.getId();
    List<PlaylistTrack> playlistTracks = SpotifyCall.executePaging(spotifyApi.getPlaylistsItems(playlistId));
    if (!playlistTracks.isEmpty()) {
      JsonArray json = new JsonArray();
      for (int i = 0; i < playlistTracks.size(); i++) {
        JsonObject object = new JsonObject();
        object.addProperty("uri", TRACK_PREFIX + playlistTracks.get(i).getTrack().getId());
        JsonArray positions = new JsonArray();
        positions.add(i);
        object.add("positions", positions);
        json.add(object);
      }

      SpotifyCall.execute(spotifyApi.removeItemsFromPlaylist(playlistId, json));
    }
  }

  /**
   * Get all playlists by the current user
   *
   * @return the playlists of the current user
   */
  public List<PlaylistSimplified> getCurrentUsersPlaylists() {
    return SpotifyCall.executePaging(spotifyApi.getListOfCurrentUsersPlaylists());
  }

  /**
   * Create a new playlist with the given name for the current user and return it
   *
   * @param title the title
   * @return the new playlist
   */
  public Playlist createPlaylist(String title) {
    User currentUser = SpotifyCall.execute(spotifyApi.getCurrentUsersProfile());
    return SpotifyCall.execute(spotifyApi.createPlaylist(currentUser.getId(), title));
  }

  /**
   * Convert a PlaylistSimplified to a fully-fledged Playlist
   *
   * @param playlistSimplified the PlaylistSimplified object
   * @return the upgraded Playlist object
   */
  public Playlist upgradePlaylistSimplified(PlaylistSimplified playlistSimplified) {
    return SpotifyCall.execute(spotifyApi.getPlaylist(playlistSimplified.getId()));
  }

  /**
   * Get all Tracks from the given playlist
   *
   * @param playlist the playlist
   * @return the tracks
   */
  public List<Track> getAllPlaylistTracks(Playlist playlist) {
    return SpotifyCall.executePaging(spotifyApi.getPlaylistsItems(playlist.getId()))
        .stream()
        .map(p -> (Track) p.getTrack())
        .collect(Collectors.toList());
  }

  /**
   * Update the playlist using an incomplete request builder (makes it easier to only update selected attributes)
   *
   * @param changePlaylistsDetailsRequest the incomplete builder
   */
  public void updatePlaylistDetails(ChangePlaylistsDetailsRequest.Builder changePlaylistsDetailsRequest) {
    SpotifyCall.execute(changePlaylistsDetailsRequest);
  }

  /**
   * Delete every given track from the playlist
   *
   * @param playlistId the playlist ID
   * @param json the tracks to delete in JsonArray format
   */
  public void deleteTracksFromPlaylist(String playlistId, JsonArray json) {
    SpotifyCall.execute(spotifyApi.removeItemsFromPlaylist(playlistId, json));
  }
}
