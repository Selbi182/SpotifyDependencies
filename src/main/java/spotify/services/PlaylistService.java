package spotify.services;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistSimplified;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.requests.data.playlists.AddItemsToPlaylistRequest;
import se.michaelthelin.spotify.requests.data.playlists.ChangePlaylistsDetailsRequest;
import se.michaelthelin.spotify.requests.data.playlists.CreatePlaylistRequest;
import spotify.api.SpotifyCall;
import spotify.util.SpotifyUtils;

@Service
public class PlaylistService {
  private final static String TRACK_URI_PREFIX = "spotify:track:";
  private final static int PLAYLIST_INTERACTION_LIMIT = 100;

  private final SpotifyApi spotifyApi;
  private final UserService userService;

  PlaylistService(SpotifyApi spotifyApi, UserService userService) {
    this.spotifyApi = spotifyApi;
    this.userService = userService;
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
    addSongsToPlaylistById(playlist, trackIds, null);
  }

  /**
   * Add the given list of song IDs to the playlist (at the start)
   *
   * @param playlist the playlist to add the songs to
   * @param trackIds the track IDs to add
   */
  public void addSongsToPlaylistByIdTop(Playlist playlist, List<String> trackIds) {
    addSongsToPlaylistById(playlist, trackIds, 0);
  }

  /**
   * Add the given list of tracks to the end of the given playlist
   *
   * @param playlist the playlist to add the tracks to
   * @param tracks the tracks to add
   */
  public void addTracksToPlaylist(Playlist playlist, List<Track> tracks) {
    addTracksToPlaylist(playlist, tracks, null);
  }

  /**
   * Add the given list of tracks to the start of the given playlist
   *
   * @param playlist the playlist to add the tracks to
   * @param tracks the tracks to add
   */
  public void addTracksToPlaylistTop(Playlist playlist, List<Track> tracks) {
    addTracksToPlaylist(playlist, tracks, 0);
  }

  /**
   * Add the given list of tracks to the playlist
   *
   * @param playlist the playlist to add the tracks to
   * @param tracks the tracks to add
   * @param position custom position for the new tracks within the playlist (end if null)
   */
  public void addTracksToPlaylist(Playlist playlist, List<Track> tracks, Integer position) {
    List<String> trackIds = tracks.stream().map(Track::getId).collect(Collectors.toList());
    addSongsToPlaylistById(playlist, trackIds, position);
  }

  /**
   * Add the given list of song IDs to the playlist with position
   *
   * @param playlist the playlist to add the songs to
   * @param trackIds the track IDs to add
   * @param position the position to add the songs at
   */
  public void addSongsToPlaylistById(Playlist playlist, List<String> trackIds, Integer position) {
    if (!trackIds.isEmpty()) {
      JsonArray json = new JsonArray();
      for (String id : trackIds) {
        json.add(TRACK_URI_PREFIX + id);
      }
      AddItemsToPlaylistRequest.Builder builder = spotifyApi.addItemsToPlaylist(playlist.getId(), json);
      if (position != null) {
        builder = builder.position(position);
      }
      SpotifyCall.execute(builder);
    }
  }

  /**
   * Remove every single song from the given playlist
   *
   * @param playlist the playlist to clear
   */
  public void clearPlaylist(Playlist playlist) {
    String playlistId = playlist.getId();
    List<IPlaylistItem> playlistTracks = SpotifyCall.executePaging(spotifyApi.getPlaylistsItems(playlistId)).stream()
      .map(PlaylistTrack::getTrack)
      .collect(Collectors.toList());
    removeItemsFromPlaylist(playlistId, playlistTracks);
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
   * Create a new playlist with the given name for the current user and return it.
   * The playlist will have no description and will be set to private.
   *
   * @param title the title
   * @return the new playlist
   */
  public Playlist createPlaylist(String title) {
    return createPlaylist(title, null, false);
  }

  /**
   /**
   * Create a new playlist with the given name, description, and public status
   * for the current user and return it
   *
   * @param title the title
   * @param description (optional) the description of the playlist
   * @param public_ whether playlist should be public or not (_ because public is a reserved keyword)
   * @return the new playlist
   */
  public Playlist createPlaylist(String title, String description, boolean public_) {
    CreatePlaylistRequest.Builder builder = spotifyApi.createPlaylist(userService.getCurrentUser().getId(), title).public_(public_);
    if (description != null) {
      builder = builder.description(description);
    }

    return SpotifyCall.execute(builder);
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
   * Get all songs from the given playlist. The provided playlist must ONLY contain songs.
   *
   * @param playlist the playlist
   * @return the tracks
   * @throws ClassCastException if the given playlist contains anything but regular songs
   *                            (such as podcasts)
   */
  public List<Track> getAllPlaylistSongs(Playlist playlist) throws ClassCastException {
    return SpotifyCall.executePaging(spotifyApi.getPlaylistsItems(playlist.getId()))
      .stream()
      .map(PlaylistTrack::getTrack)
      .map(Track.class::cast)
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
   * Remove every item specified in itemsToDelete from the playlist.
   * Note: If the number of items to remove exceeds 100, this method will need
   * to perform multiple API calls and may take more time to finish.
   *
   * @param playlistId the playlist ID
   * @param itemsToDelete the items to delete
   */
  public void removeItemsFromPlaylist(String playlistId, List<IPlaylistItem> itemsToDelete) {
    for (List<IPlaylistItem> partition : SpotifyUtils.partitionList(itemsToDelete, PLAYLIST_INTERACTION_LIMIT)) {
      JsonArray partitionDeletion = new JsonArray();
      for (IPlaylistItem item : partition) {
        JsonObject itemToDelete = new JsonObject();
        itemToDelete.addProperty("uri", item.getUri());
        partitionDeletion.add(itemToDelete);
      }
      SpotifyCall.execute(spotifyApi.removeItemsFromPlaylist(playlistId, partitionDeletion));
    }
  }

  /**
   * Attach a picture to the given playlist from a URL. The image URL must point to a JPEG no larger than 256 KB.
   *
   * @param playlist the playlist to attach the picture to
   * @param imageUrl the URL to the given image
   */
  public void attachImageToPlaylist(Playlist playlist, String imageUrl) {
    if (imageUrl != null) {
      String base64image = SpotifyUtils.toBase64Image(imageUrl);
      if (base64image != null) {
        SpotifyCall.execute(spotifyApi.uploadCustomPlaylistCoverImage(playlist.getId()).image_data(base64image));
      }
    }
  }
}
