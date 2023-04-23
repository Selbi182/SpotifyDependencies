package spotify.util;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.jsoup.Connection;
import org.jsoup.Jsoup;

import se.michaelthelin.spotify.enums.AlbumGroup;
import se.michaelthelin.spotify.enums.ModelObjectType;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Album;
import se.michaelthelin.spotify.model_objects.specification.AlbumSimplified;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.AudioFeatures;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.TrackSimplified;
import spotify.services.TrackService;
import spotify.util.data.AlbumTrackPair;

public final class SpotifyUtils {
  private final static double EPSILON = 0.01;

  private final static Pattern EP_MATCHER = Pattern.compile("\\bE\\W?P\\W?\\b");
  private final static int EP_SONG_COUNT_THRESHOLD = 5;
  private final static int EP_DURATION_THRESHOLD = 20 * 60 * 1000;
  private final static int EP_SONG_COUNT_THRESHOLD_LESSER = 3;
  private final static int EP_DURATION_THRESHOLD_LESSER = 10 * 60 * 1000;

  private final static Pattern LIVE_MATCHER = Pattern.compile("\\b(LIVE|SHOW|TOUR)\\b", Pattern.CASE_INSENSITIVE);
  private final static Pattern LIVE_MATCHER_EXTRA =
    Pattern.compile("(\\bLIVE\\W*$|\\bLIVE.*?\\b(\\d{4}|(IN|AT|ON|PERFORMANCE|SHOW|CONCERT|SESSION))\\b)", Pattern.CASE_INSENSITIVE);
  private final static double LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD_DEFINITE = 0.9;
  private final static double LIVENESS_THRESHOLD = 0.55;
  private final static double LIVENESS_THRESHOLD_LESSER = 0.4;
  private final static int LIVE_MIN_SONG_COUNT_FOR_SHORTCUT = 3;

  private final static Pattern REMIX_MATCHER = Pattern.compile("\\b(RMX|REMIX+|REMIXES)\\b", Pattern.CASE_INSENSITIVE);
  private final static double REMIX_SONG_COUNT_PERCENTAGE_THRESHOLD = 0.67;
  private final static double REMIX_SONG_COUNT_PERCENTAGE_THRESHOLD_LESSER = 0.2;

  private final static List<String> VERBOSE_RELEASE_WORDS = List.of(
    "anniversary.*", "bonus track", "deluxe.*", "special.*", "remaster.*", "explicit.*", "extended.*", "expansion.*",
    "expanded.*", "cover.*", "original.*", "motion\\spicture.*", "re.?issue", "re.?record", "\\d{4}.*", "feat.*");

  /**
   * Utility class
   */
  private SpotifyUtils() {
  }

  ///////

  /**
   * Performs a <code>Thread.sleep(sleepMs);</code> call in a surrounded try-catch
   * that ignores any interrupts. This method mostly exists to reduce the number
   * of try-catch and throws clutter throughout the code. Yes, I know it's bad
   * practice, cry me a river.
   *
   * @param millis the number of milliseconds to sleep
   */
  public static void sneakySleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  /**
   * Return the given list of scopes as a Spotify-compatible, space-separated string
   * @param scopes the scopes
   * @return the Spotify-compatible string
   */
  public static String buildScopes(List<String> scopes) {
    return String.join(" ", scopes);
  }

  /**
   * Check if the given old date is still within the allowed timeout window
   *
   * @param baseDate       the date to check "now" against
   * @param timeoutInHours the timeout in hours
   * @return true if is within timeout window
   */
  public static boolean isWithinTimeoutWindow(LocalDateTime baseDate, int timeoutInHours) {
    LocalDateTime currentTime = LocalDateTime.now();
    return currentTime.minus(timeoutInHours, ChronoUnit.HOURS).isBefore(baseDate);
  }

  /**
   * Check if the given old date is still within the allowed timeout window
   *
   * @param baseDate       the date to check "now" against
   * @param timeoutInHours the timeout in hours
   * @return true if is within timeout window
   */
  public static boolean isWithinTimeoutWindow(Date baseDate, int timeoutInHours) {
    Instant baseTime = Instant.ofEpochMilli(baseDate.getTime());
    Instant currentTime = Instant.now();
    return currentTime.minus(timeoutInHours, ChronoUnit.HOURS).isBefore(baseTime);
  }

  /**
   * Creates a map with a full AlbumGroup to List relationship (the lists are
   * empty)
   *
   * @param <T> anything
   * @return the album group
   */
  public static <T> Map<AlbumGroup, List<T>> createAlbumGroupToListOfTMap() {
    Map<AlbumGroup, List<T>> albumGroupToList = new HashMap<>();
    for (AlbumGroup ag : AlbumGroup.values()) {
      albumGroupToList.put(ag, new ArrayList<>());
    }
    return albumGroupToList;
  }

  /**
   * Returns true if all mappings just contain an empty list (not null)
   *
   * @param <K> anything
   * @param <T> anything (inside a list)
   * @param listsByMap the map
   * @return true if is empty
   */
  public static <T, K> boolean isAllEmptyLists(Map<K, List<T>> listsByMap) {
    return listsByMap.values().stream().allMatch(List::isEmpty);
  }

  /**
   * Check if the given String is either <i>null</i> or "null" (a literal String)
   *
   * @param string the string
   * @return true if the given string qualifies as null string
   */
  public static boolean isNullString(String string) {
    return string == null || string.equalsIgnoreCase("null");
  }

  /**
   * Return the current time as unix timestamp
   *
   * @return the current time as long
   */
  public static long currentTime() {
    Calendar cal = Calendar.getInstance();
    return cal.getTimeInMillis();
  }

  /**
   * Build a readable String for an AlbumSimplified
   *
   * @param as the album
   * @return the string
   */
  public static String formatAlbum(AlbumSimplified as) {
    return String.format("[%s] %s - %s (%s)",
      as.getAlbumGroup().toString(),
      joinArtists(as.getArtists()),
      as.getName(),
      as.getReleaseDate());
  }

  /**
   * Build a readable String for a Track
   *
   * @param t the track
   * @return the string
   */
  public static String formatTrack(Track t) {
    return String.format("%s - %s",
      joinArtists(t.getArtists()),
      t.getName());
  }

  /**
   * Return a string representation of all artist names, separated by ", "
   *
   * @param artists the artists
   * @return the string
   */
  public static String joinArtists(ArtistSimplified[] artists) {
    return Stream.of(artists)
      .map(ArtistSimplified::getName)
      .collect(Collectors.joining(", "));
  }

  /**
   * Convert the ArtistSimplified to a list of the names
   *
   * @param artists the artists
   * @return the list of strings
   */
  public static List<String> toArtistNamesList(ArtistSimplified[] artists) {
    return Stream.of(artists)
      .map(ArtistSimplified::getName)
      .collect(Collectors.toList());
  }

  /**
   * Convert the Track to a list of the names
   *
   * @param t the song
   * @return the list of strings
   */
  public static List<String> toArtistNamesList(Track t) {
    return Stream.of(t.getArtists())
      .map(ArtistSimplified::getName)
      .collect(Collectors.toList());
  }

  /**
   * Convert the TrackSimplified to a list of the names
   *
   * @param ts the song
   * @return the list of strings
   */
  public static List<String> toArtistNamesList(TrackSimplified ts) {
    return Stream.of(ts.getArtists())
      .map(ArtistSimplified::getName)
      .collect(Collectors.toList());
  }

  /**
   * Returns the name of the first artist of this album (usually the only one)
   *
   * @param a the album
   * @return the name of the first artist
   */
  public static String getFirstArtistName(Album a) {
    return a.getArtists()[0].getName();
  }

  /**
   * Returns the name of the first artist of this album (usually the only one)
   *
   * @param as the album
   * @return the name of the first artist
   */
  public static String getFirstArtistName(AlbumSimplified as) {
    return as.getArtists()[0].getName();
  }

  /**
   * Returns the name of the first artist of this track (usually the only one)
   *
   * @param t the track
   * @return the name of the first artist
   */
  public static String getFirstArtistName(Track t) {
    return t.getArtists()[0].getName();
  }

  /**
   * Returns the name of the first artist of this track (usually the only one)
   *
   * @param ts the track
   * @return the name of the first artist
   */
  public static String getFirstArtistName(TrackSimplified ts) {
    return ts.getArtists()[0].getName();
  }

  /**
   * Returns the name of the last artist of this album
   *
   * @param as the album
   * @return the name of the last artist
   */
  public static String getLastArtistName(AlbumSimplified as) {
    return as.getArtists()[as.getArtists().length - 1].getName();
  }

  /**
   * Normalizes a file by converting it to a Path object, calling .normalize(),
   * and returning it back as file.
   *
   * @param file the file
   * @return the normalized file
   */
  public static File normalizeFile(File file) {
    if (file != null) {
      return file.toPath().normalize().toFile();
    }
    return null;
  }

  /**
   * Adds all the items of the given (primitive) array to the specified List, if
   * and only if the item array is not null and contains at least one item.
   *
   * @param <T>    the shared class type
   * @param source the items to add
   * @param target the target list
   */
  public static <T> void addToListIfNotBlank(T[] source, List<T> target) {
    if (source != null && source.length > 0) {
      List<T> asList = Arrays.asList(source);
      target.addAll(asList);
    }
  }

  /**
   * Return the ID of a URI in the format spotify:album:123456789 to 123456789
   *
   * @param uri the uri
   * @return the ID of the uri
   */
  public static String getIdFromUri(String uri) {
    String[] split = uri.split(":");
    return split[split.length - 1];
  }

  /**
   * Find the release year of the album (which is in ISO format, so it's always the first four characters)
   *
   * @param album the album
   * @return the release year as string
   */
  public static String findReleaseYear(Album album) {
    if (album.getReleaseDate() != null) {
      return album.getReleaseDate().substring(0, 4);
    }
    return null;
  }

  /**
   * Find the release year of the album (which is in ISO format, so it's always the first four characters)
   *
   * @param album the album
   * @return the release year as string
   */
  public static String findReleaseYear(AlbumSimplified album) {
    if (album.getReleaseDate() != null) {
      return album.getReleaseDate().substring(0, 4);
    }
    return null;
  }

  /**
   * Find the release year of the track (which is in ISO format, so it's always the first four characters)
   *
   * @param track the track
   * @return the release year as string
   */
  public static String findReleaseYear(Track track) {
    return findReleaseYear(track.getAlbum());
  }

  /**
   * Format the given time in milliseconds as mm:ss or hh:mm:ss if larger than one hour
   *
   * @param timeInMs the time in milliseconds
   * @return the formatted time as string
   */
  public static String formatTime(long timeInMs) {
    Duration duration = Duration.ofMillis(timeInMs);
    long hours = duration.toHours();
    int minutesPart = duration.toMinutesPart();
    int secondsPart = duration.toSecondsPart();
    if (hours > 0) {
      return String.format("%d:%02d:%02d", hours, minutesPart, secondsPart);
    } else {
      return String.format("%d:%02d", minutesPart, secondsPart);
    }
  }

  /**
   * Find the largest image of a given image array
   *
   * @param images primitive array of the images to check
   * @return the largest image (or null, if no images were passed)
   */
  public static String findLargestImage(Image[] images) {
    if (images != null) {
      Image largest = null;
      for (Image img : images) {
        if (largest == null || (img.getWidth() * img.getHeight()) > (largest.getWidth() * largest.getHeight())) {
          largest = img;
        }
      }
      return largest != null ? largest.getUrl() : null;
    }
    return null;
  }

  /**
   * Find the smallest image of a given image array
   *
   * @param images primitive array of the images to check
   * @return the smallest image (or null, if no images were passed)
   */
  public static String findSmallestImage(Image[] images) {
    if (images != null) {
      Image smallest = null;
      for (Image img : images) {
        if (smallest == null || (img.getWidth() * img.getHeight()) < (smallest.getWidth() * smallest.getHeight())) {
          smallest = img;
        }
      }
      return smallest != null ? smallest.getUrl() : null;
    }
    return null;
  }

  /**
   * Return true if any of the artists on this album match the given artistId
   *
   * @param album the album
   * @param artistId the artist ID
   * @return true if there is any match
   */
  public static boolean anyArtistMatches(AlbumSimplified album, String artistId) {
    for (ArtistSimplified artist : album.getArtists()) {
      if (artist.getId() != null && artist.getId().equals(artistId)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Creates an identifier string of the album using the album group, the first artist's name, and the album name.
   *
   * @param as the album
   * @return the string in the format "albumgroup_firstartistname_albumname"
   */
  public static String albumIdentifierString(AlbumSimplified as) {
    String artistPart = SpotifyUtils.strippedTitleIdentifier(SpotifyUtils.getFirstArtistName(as));
    String releasePart = SpotifyUtils.strippedTitleIdentifier(as.getName());
    return String.join("_", as.getAlbumGroup().getGroup(), artistPart, releasePart).toLowerCase();
  }

  /**
   * Creates a string that tries to be as normalized and generic as possible,
   * based on the given track or album. The name will be lower-cased, stripped off
   * any white space and special characters, and anything in brackets such as
   * "feat.", "bonus track", "remastered" will be removed.
   *
   * @param title a string that is assumed to be the title of a track or an album
   * @return the stripped title
   */
  public static String strippedTitleIdentifier(String title) {
    String identifier = title
      .toLowerCase()
      .replaceAll(",", " ");
    for (String word : VERBOSE_RELEASE_WORDS) {
      identifier = identifier.replaceAll(word, "");
    }
    return identifier
      .replaceAll("\\s+", "")
      .replaceAll("\\W+", "");
  }

  /**
   * Convert a full album an album simplified. Any extra variables are thrown
   * away.
   *
   * @param album the album to convert
   * @return the converted album
   */
  public static AlbumSimplified asAlbumSimplified(Album album) {
    AlbumSimplified.Builder as = new AlbumSimplified.Builder();

    as.setAlbumGroup(AlbumGroup.keyOf(album.getAlbumType().getType())); // Not exact but works
    as.setAlbumType(album.getAlbumType());
    as.setArtists(album.getArtists());
    as.setAvailableMarkets(album.getAvailableMarkets());
    as.setExternalUrls(album.getExternalUrls());
    as.setHref(album.getHref());
    as.setId(album.getId());
    as.setImages(album.getImages());
    as.setName(album.getName());
    as.setReleaseDate(album.getReleaseDate());
    as.setReleaseDatePrecision(album.getReleaseDatePrecision());
    as.setRestrictions(null); // No alternative
    // ModelObjectType missing
    as.setUri(album.getUri());

    return as.build();
  }

  /**
   * Convert a TrackSimplified to a full Track object. Any attributes missing in the simplified version
   * will become null after the conversion.
   *
   * @param ts the TrackSimplified object
   * @return the converted Track object
   */
  public static Track asTrack(TrackSimplified ts) {
    Track.Builder trackBuilder = new Track.Builder();

    trackBuilder.setArtists(ts.getArtists());
    trackBuilder.setAvailableMarkets(ts.getAvailableMarkets());
    trackBuilder.setDiscNumber(ts.getDiscNumber());
    trackBuilder.setDurationMs(ts.getDurationMs());
    trackBuilder.setExplicit(ts.getIsExplicit());
    trackBuilder.setExternalUrls(ts.getExternalUrls());
    trackBuilder.setHref(ts.getHref());
    trackBuilder.setId(ts.getId());
    trackBuilder.setIsPlayable(ts.getIsPlayable());
    trackBuilder.setLinkedFrom(ts.getLinkedFrom());
    trackBuilder.setName(ts.getName());
    trackBuilder.setPreviewUrl(ts.getPreviewUrl());
    trackBuilder.setTrackNumber(ts.getTrackNumber());
    trackBuilder.setPreviewUrl(ts.getPreviewUrl());
    trackBuilder.setType(ts.getType());
    trackBuilder.setUri(ts.getUri());

    return trackBuilder.build();
  }

  /**
   * Check if the given playlist item is a track
   *
   * @param t the playlist item
   * @return true if it is
   */
  public static boolean isTrack(IPlaylistItem t) {
    return ModelObjectType.TRACK.equals(t.getType());
  }

  /**
   * Returns true if the given AlbumTrackPair qualifies as EP. The definition
   * of an EP (on Spotify) is a SINGLE that fulfills ANY of the following attributes:
   * <ul>
   * <li>"EP" appears in the album title (uppercase, single word, may contain a
   * single symbol in between and after the letters)</li>
   * <li>min 5 songs</li>
   * <li>min 20 minutes</li>
   * <li>min 3 songs AND min 10 minutes AND doesn't have a title track*</li>
   * </ul>
   * *The great majority of EPs are covered by the first three strategies. The
   * last one for really silly edge cases in which an artist may release an EP
   * that is too similar to a slightly fancier single by numbers alone.
   *
   * @param albumTrackPair the AlbumTrackPair
   * @return if the AlbumTrackPair qualifies as EP
   */
  public static boolean isExtendedPlay(AlbumTrackPair albumTrackPair) {
    String albumTitle = albumTrackPair.getAlbum().getName();
    List<TrackSimplified> tracks = albumTrackPair.getTracks();
    if (EP_MATCHER.matcher(albumTitle).find()) {
      return true;
    }
    int trackCount = tracks.size();
    int totalDurationMs = tracks.stream().mapToInt(TrackSimplified::getDurationMs).sum();
    if (trackCount >= EP_SONG_COUNT_THRESHOLD || totalDurationMs >= EP_DURATION_THRESHOLD) {
      return true;
    }
    if (trackCount >= EP_SONG_COUNT_THRESHOLD_LESSER && totalDurationMs >= EP_DURATION_THRESHOLD_LESSER) {
      String strippedAlbumTitle = SpotifyUtils.strippedTitleIdentifier(albumTitle);
      return tracks.stream()
        .map(TrackSimplified::getName)
        .map(SpotifyUtils::strippedTitleIdentifier)
        .noneMatch(t -> t.equalsIgnoreCase(strippedAlbumTitle));
    }
    return false;
  }

  /**
   * Returns true if the given release qualifies as live release. The definition
   * of a live release is a release that fulfills ANY of the following attributes:
   * <ul>
   * <li>At least half of the songs of this release have a combined "liveness"
   * average of 50% or more</li>
   * <li>If the word "LIVE" is included in the release title, the required
   * liveness threshold is reduced to 25%</li>
   * </ul>
   * The liveness value is determined by the Spotify API for each individual song.
   * It gives a vague idea how probable it is for the song to be live. Hints like
   * recording quality and audience cheers are used.
   *
   * @param albumTrackPair the AlbumTrackPair
   * @param trackService the TrackService required to find the liveness values
   * @return true if the AlbumTrackPair qualifies as Live release
   */
  public static boolean isLiveRelease(AlbumTrackPair albumTrackPair, TrackService trackService) {
    String albumTitle = albumTrackPair.getAlbum().getName();
    List<TrackSimplified> tracks = albumTrackPair.getTracks();
    double trackCount = tracks.size();
    double liveTracks = tracks.stream().filter(t -> LIVE_MATCHER.matcher(t.getName()).find()).count();
    double liveTrackPercentage = liveTracks / trackCount;
    if (liveTrackPercentage > LIVE_SONG_COUNT_PERCENTAGE_THRESHOLD_DEFINITE) {
      if (trackCount > LIVE_MIN_SONG_COUNT_FOR_SHORTCUT
            || LIVE_MATCHER_EXTRA.matcher(albumTitle).find()) {
        return true;
      }
    }

    boolean hasLiveInTitle = LIVE_MATCHER.matcher(albumTitle).find();
    boolean hasLiveInTracks = liveTrackPercentage > EPSILON;

    if (hasLiveInTitle || hasLiveInTracks) {
      List<AudioFeatures> audioFeatures = trackService.getAudioFeatures(tracks);
      double averageLiveness = audioFeatures.stream()
        .filter(Objects::nonNull)
        .mapToDouble(AudioFeatures::getLiveness)
        .average()
        .orElse(0.0);
      boolean isLive = averageLiveness > LIVENESS_THRESHOLD;
      if (!isLive && hasLiveInTitle) {
        isLive = averageLiveness >= LIVENESS_THRESHOLD_LESSER;
      }
      return isLive;
    }
    return false;
  }

  /**
   * Returns true if the given release OR at least 2/3 of the release's tracks
   * have "REMIX" in their titles (one word, case-insensitive). Exception: Remix is
   * in the title, in which case the threshold is only 1/5.
   *
   * @param albumTrackPair the AlbumTrackPair
   * @return true if the given release qualifies as Remix
   */
  public static boolean isRemix(AlbumTrackPair albumTrackPair) {
    String albumTitle = albumTrackPair.getAlbum().getName();
    List<TrackSimplified> tracks = albumTrackPair.getTracks();
    boolean hasRemixInTitle = REMIX_MATCHER.matcher(albumTitle).find();
    List<String> trackNames = tracks.stream().map(TrackSimplified::getName).collect(Collectors.toList());
    double trackCountRemix = trackNames.stream().filter(t -> REMIX_MATCHER.matcher(t).find()).count();
    double trackCount = trackNames.size();
    double remixPercentage = trackCountRemix / trackCount;
    if (hasRemixInTitle) {
      return remixPercentage > REMIX_SONG_COUNT_PERCENTAGE_THRESHOLD_LESSER;
    }
    return remixPercentage > REMIX_SONG_COUNT_PERCENTAGE_THRESHOLD;
  }

  /**
   * Split the given list into smaller partitions, each part limited by the given partitionSize.
   * The last part may be smaller.
   *
   * @param inputList the input list to partition
   * @param partitionSize the size for each individual partition
   * @param <T> the type of the list's objects
   * @return the partitioned lists inside a collector list
   */
  public static <T> List<List<T>> partitionList(List<T> inputList, int partitionSize) {
    List<List<T>> partitionList = new ArrayList<>();
    for (int i = 0; i < inputList.size(); i += partitionSize) {
      int upperBound = Math.min(inputList.size(), i + partitionSize);
      List<T> subList = inputList.subList(i, upperBound);
      partitionList.add(subList);
    }
    return partitionList;
  }

  /**
   * Returns just the ID of a full Spotify URL. Example:
   *
   * <pre>https://open.spotify.com/track/2hJQeZMzZ7ijgQT6I7b8Hz?si=J3g-NfAuShS34IOTayJgDQ</pre>
   * Becomes:
   * <pre>2hJQeZMzZ7ijgQT6I7b8Hz</pre>
   *
   * @param spotifyUrl the Spotify URL as String
   * @return the ID as String
   * @throws MalformedURLException on an illegal URL
   */
  public static String getIdFromSpotifyUrl(String spotifyUrl) throws MalformedURLException {
    URL url = new URL(spotifyUrl);
    String path = url.getPath();
    return path.substring(path.lastIndexOf('/') + 1);
  }

  /**
   * Returns the full URL of a given shortened Spotify URL by following its redirection. Example:
   *
   * <pre>https://spotify.link/iyAqlLTP3xb</pre>
   * Becomes:
   * <pre>https://open.spotify.com/track/2hJQeZMzZ7ijgQT6I7b8Hz</pre>
   * Plus a bunch of other parameters that will likely be of little use.
   *
   * @param shortSpotifyUrl the shortened Spotify URL
   * @return the full URL as String
   * @throws IOException if the URL is malformed or the connection failed
   */
  public static String getFullUrlFromShortSpotifyUrl(String shortSpotifyUrl) throws IOException {
    Connection.Response execute = Jsoup.connect(shortSpotifyUrl).followRedirects(true).execute();
    return execute.url().toString();
  }

  /**
   * Returns true if the given String is a short Spotify URL. Or more formally,
   * if it starts with <code>https://spotify.link/</code>
   *
   * @param spotifyUrl the Spotify URL
   * @return true if it's a short URL
   */
  public static boolean isShortSpotifyUrl(String spotifyUrl) {
    return spotifyUrl.startsWith("https://spotify.link/");
  }

  /**
   * Download the given image URL and return the payload as JPEG encoded in Base64.
   *
   * @param imageUrl the URL to the image
   * @return the base64-encoded string of the image
   */
  public static String toBase64Image(String imageUrl) {
    try {
      URL url = new URL(imageUrl);
      BufferedImage img = ImageIO.read(url);
      ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
      if (ImageIO.write(img, "jpeg", byteStream)) {
        return Base64.getEncoder().encodeToString(byteStream.toByteArray());
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }
}
