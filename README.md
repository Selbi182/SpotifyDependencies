# spotify-dependencies

A collection of convenience features for the [Spotify Web API Java Wrapper](https://github.com/thelinmichael/spotify-web-api-java).

Also included is a basic SpringBoot-based login process.

## Installation
Add the repo and dependency in your `build.gradle`:

```groovy
    repositories {
        maven {
            url 'https://mymavenrepo.com/repo/FpgB1Mi6I9ud1Gd3tX0r/'
        }
    }
    
    dependencies {
        implementation 'spotify:spotify-dependencies:1.2.0'
    }
```

Make sure component scan is enabled for the `spotify` root packaged. Alternatively, just develop your entire app in that package as well.

## Main Use-Case
One problem that really gets on many people's nerves are 429 (Too many requests) errors. The main draw of this collection is to wrap any SpotifyApi call inside a static utility method that automatically retries the request on an error.

To do this, simply put your regular `spotifyApi` request inside the new `SpotifyCall.execute()` method:

```
Album rammsteinMutter = SpotifyCall.execute(spotifyApi.getAlbum("1CtTTpKbHU8KbHRB4LmBbv"));
```
This also works for paged endpoints, such as grabbing every single saved track you got in your library at once:

```
List<SavedTrack> yourSavedTracks = SpotifyCall.executePaging(spotifyApi.getUsersSavedTracks());
```
