# spotify-dependencies

A collection of convenience features for the [Spotify Web API Java Wrapper](https://github.com/thelinmichael/spotify-web-api-java).

Also included is a basic SpringBoot-based login process.

## Main Use-Case
One problem that really gets on many people's nerves are 429 (Too many requests) errors. The main draw of this collection is to wrap any SpotifyApi call inside a static utility method that automatically retries the request on an error.

To do this, simply put your regular `spotifyApi` request inside the new `SpotifyCall.execute()` method:

```java
Album rammsteinMutter = SpotifyCall.execute(spotifyApi.getAlbum("1CtTTpKbHU8KbHRB4LmBbv"));
```
This also works for paged endpoints, such as grabbing every single saved track you got in your library at once:

```java
List<SavedTrack> yourSavedTracks = SpotifyCall.executePaging(spotifyApi.getUsersSavedTracks());
```

## Installation

### Dependencies
Add the repository and dependency in your `build.gradle`:

```groovy
repositories {
    maven {
        url 'https://mymavenrepo.com/repo/FpgB1Mi6I9ud1Gd3tX0r/'
    }
}

dependencies {
    implementation 'spotify:spotify-dependencies:1.6.5'
}
```

Make sure component scan is enabled for the `spotify` root packaged. Alternatively, just develop your entire app in that package as well.

### Server Port
The server port *must* be set manually in the `application.properties` file:

```
server.port=8080
```

If this property is not set, your app will immediately halt on startup.

### Scopes
Spotify requires your app to explicitly state which parts of their API you require access for. Some features are always accessible, while others require explicit permission from the user logging in. These must be set by creating a visible Spring component that implements `SpotifyApiScopes`, with the method `requiredScopes()` returning a list of Strings with the scopes you want.

For example:

```java
@Component
public static class SpotifyMyCustomAppScopes implements SpotifyApiScopes {

    @Override
    public List<String> requiredScopes() {
        return List.of(
            "user-read-playback-position",
            "user-read-playback-state",
            "user-read-currently-playing",
            "user-read-private"
        );
    }
}
```

A full list of the available scopes can be found [here](https://developer.spotify.com/documentation/general/guides/authorization/scopes).

If you don't require any special scopes, simply create an implementation that returns an empty list.

### Spotify Tokens
To even be able to access the Spotify API, you first need to create an app on the [Spotify Web API Developer Dashboard](https://developer.spotify.com/dashboard/applications). Once that is done, put the *Client ID* and *Client Secret* into a file named `spotifybot.properties` (obviously shortened here):

```
client_id=ecd2e4<...>
client_secret=2f4260<...>
```

### Login Callback
On the Spotify API Dashboard, you *must* set a login callback. If you're running your app locally, all you need is to know the server port you've set earlier. Apart from that, the path must always be `/login-callback`:

```
http://localhost:8080/login-callback
```

### Login on First Start
Once everything is set up, and you start your app for the first time, an automatic login to the Spotify API will be made. Since this is the first time it's started, that will obviously fail. After a short timeout, you will see a URL being printed to the console that should look something like this:

```
https://accounts.spotify.com:443/authorize?client_id=ecd2e4<...>&response_type=code&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin-callback&scope=user-read-playback-position%20user-read-playback-state%20user-read-private
```

Open it in your preferred browser (if it failed to do so by itself) and follow the login steps explained there. After you're done, your app is ready!

You can double-check if everything worked by looking into the `spotifybot.properties` file one more time, which should now have two new fields `access_token` and `refresh_token`. The access token will be automatically refreshed by this library on a periodic basis, so you don't need to worry about that.

### Login Callback
To synchronize your app to wait for the login to be completed before any further business logic is run, a custom event called `SpotifyApiLoggedInEvent` is fired that can be intercepted anywhere you'd like:

```java
@EventListener(SpotifyApiLoggedInEvent.class)
public void loggedInEvent() {
    System.out.println("Successfully logged into the Spotify API!");
}
```
