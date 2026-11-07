package fm.schwafelpatie;

import android.net.Uri;

import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

final class Catalog {
    static final URI SITE = URI.create("https://mrschwafel.github.io/Schwafelpatie-FM/");

    final List<MediaItem> tracks;
    final List<String> albums;

    private Catalog(List<MediaItem> tracks, List<String> albums) {
        this.tracks = tracks;
        this.albums = albums;
    }

    static Catalog load() throws IOException, JSONException {
        HttpURLConnection connection = connect(SITE.resolve("catalog.json").toURL());
        StringBuilder json = new StringBuilder();
        try (InputStream stream = connection.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null; ) json.append(line);
        } finally {
            connection.disconnect();
        }

        JSONObject catalog = new JSONObject(json.toString());
        JSONArray songs = catalog.getJSONArray("allSongs");
        List<MediaItem> tracks = new ArrayList<>(songs.length());
        for (int index = 0; index < songs.length(); index++) {
            JSONObject song = songs.getJSONObject(index);
            MediaMetadata metadata = new MediaMetadata.Builder()
                    .setTitle(song.getString("title"))
                    .setAlbumTitle(song.getString("album"))
                    .setArtist("Schwafelpatie FM")
                    .setArtworkUri(asset(song.getString("cover")))
                    .build();
            tracks.add(new MediaItem.Builder()
                    .setMediaId(song.getString("key"))
                    .setUri(asset(song.getString("file")))
                    .setMimeType(MimeTypes.AUDIO_MPEG)
                    .setMediaMetadata(metadata)
                    .build());
        }
        if (tracks.isEmpty()) throw new IOException("Leere Mediathek");

        JSONArray albumKeys = catalog.getJSONArray("albumKeys");
        List<String> albums = new ArrayList<>(albumKeys.length());
        for (int index = 0; index < albumKeys.length(); index++) albums.add(albumKeys.getString(index));
        return new Catalog(tracks, albums);
    }

    List<MediaItem> tracksIn(String album) {
        if (album == null) return tracks;
        List<MediaItem> matches = new ArrayList<>();
        for (MediaItem track : tracks) {
            if (album.contentEquals(track.mediaMetadata.albumTitle)) matches.add(track);
        }
        return matches;
    }

    static HttpURLConnection connect(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        return connection;
    }

    private static Uri asset(String path) throws IOException {
        URI resolved = SITE.resolve(path).normalize();
        if (!resolved.toString().startsWith(SITE.toString())) {
            throw new IOException("Ungültiger Medienpfad");
        }
        return Uri.parse(resolved.toString());
    }
}
