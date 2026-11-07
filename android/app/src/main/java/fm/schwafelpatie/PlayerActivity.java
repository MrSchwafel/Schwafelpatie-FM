package fm.schwafelpatie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class PlayerActivity extends Activity {
    private static final int ACCENT = Color.rgb(255, 196, 108);
    private static final int INK = Color.rgb(248, 242, 232);
    private static final int MUTED = Color.rgb(191, 178, 161);
    private static final int SEEK_STEPS = 1000;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private Catalog catalog;
    private String selectedAlbum;
    private boolean loadingCatalog;
    private boolean scrubbing;
    private Uri artwork;
    private ListenableFuture<MediaController> connection;
    private MediaController player;
    private TextView status, track, album, times;
    private ImageView cover;
    private SeekBar seek;
    private Button play, previous, next, shuffle, repeat, albums, songs, retry;

    private final Player.Listener listener = new Player.Listener() {
        @Override
        public void onEvents(Player source, Player.Events events) {
            render();
        }
    };

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!scrubbing) renderPosition();
            ui.postDelayed(this, 350);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) selectedAlbum = state.getString("album");
        setContentView(buildScreen());
        loadCatalog();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putString("album", selectedAlbum);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(this, token).buildAsync();
        connection = future;
        future.addListener(() -> {
            if (connection != future) return;
            try {
                player = future.get();
                player.addListener(listener);
                render();
            } catch (Exception error) {
                status.setText("Player konnte nicht gestartet werden.");
            }
        }, ui::post);
        ui.post(tick);
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(tick);
        if (player != null) player.removeListener(listener);
        player = null;
        if (connection != null) MediaController.releaseFuture(connection);
        connection = null;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        network.shutdownNow();
        super.onDestroy();
    }

    private void loadCatalog() {
        loadingCatalog = true;
        render();
        network.execute(() -> {
            Catalog loaded = null;
            try {
                loaded = Catalog.load();
            } catch (Exception ignored) {
                // The retry control stays available after a network or catalog error.
            }
            Catalog result = loaded;
            ui.post(() -> {
                if (isDestroyed()) return;
                catalog = result;
                loadingCatalog = false;
                if (catalog != null && !catalog.albums.contains(selectedAlbum)) selectedAlbum = null;
                render();
            });
        });
    }

    private void chooseAlbum() {
        List<String> options = new ArrayList<>();
        options.add("Alle Alben");
        options.addAll(catalog.albums);
        new AlertDialog.Builder(this)
                .setTitle("Album auswählen")
                .setItems(options.toArray(new String[0]), (dialog, index) -> {
                    selectedAlbum = index == 0 ? null : options.get(index);
                    render();
                })
                .show();
    }

    private void chooseSong() {
        List<MediaItem> queue = catalog.tracksIn(selectedAlbum);
        String[] titles = new String[queue.size()];
        for (int index = 0; index < queue.size(); index++) {
            MediaItem item = queue.get(index);
            titles[index] = selectedAlbum == null
                    ? item.mediaMetadata.title + " · " + item.mediaMetadata.albumTitle
                    : String.valueOf(item.mediaMetadata.title);
        }
        new AlertDialog.Builder(this)
                .setTitle("Song auswählen")
                .setItems(titles, (dialog, index) -> start(queue, index))
                .show();
    }

    private void start(List<MediaItem> queue, int index) {
        if (player == null || queue.isEmpty()) return;
        player.setMediaItems(queue, index, 0);
        player.prepare();
        player.play();
    }

    private void togglePlayback() {
        if (player == null) return;
        if (player.getMediaItemCount() == 0) {
            if (catalog != null) start(catalog.tracksIn(selectedAlbum), 0);
        } else if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED
                && player.getPlayerError() == null) {
            player.pause();
        } else {
            if (player.getPlaybackState() == Player.STATE_ENDED) player.seekToDefaultPosition();
            if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
            player.play();
        }
    }

    private void render() {
        boolean ready = player != null;
        boolean hasTrack = ready && player.getCurrentMediaItem() != null;
        boolean requested = hasTrack && player.getPlayWhenReady()
                && player.getPlaybackState() != Player.STATE_ENDED && player.getPlayerError() == null;
        play.setEnabled(ready && (catalog != null || hasTrack));
        play.setText(requested ? "Pause" : "Abspielen");
        previous.setEnabled(hasTrack && player.hasPreviousMediaItem());
        next.setEnabled(hasTrack && player.hasNextMediaItem());
        repeat.setEnabled(ready);
        shuffle.setEnabled(ready);
        if (ready) {
            markToggle(repeat, player.getRepeatMode() == Player.REPEAT_MODE_ONE);
            markToggle(shuffle, player.getShuffleModeEnabled());
        }
        albums.setEnabled(catalog != null);
        albums.setText(selectedAlbum == null ? "Alle Alben" : selectedAlbum);
        int trackCount = catalog == null ? 0 : catalog.tracksIn(selectedAlbum).size();
        songs.setEnabled(ready && trackCount > 0);
        songs.setText(trackCount > 0 ? "Song auswählen (" + trackCount + ")" : "Song auswählen");
        retry.setVisibility(catalog == null && !loadingCatalog ? Button.VISIBLE : Button.GONE);

        MediaItem item = hasTrack ? player.getCurrentMediaItem() : null;
        track.setText(item == null ? "Schwafelpatie FM" : item.mediaMetadata.title);
        album.setText(item == null ? "" : item.mediaMetadata.albumTitle);
        showArtwork(item == null ? null : item.mediaMetadata.artworkUri);
        if (hasTrack && player.getPlayerError() != null) status.setText("Stream unterbrochen. Erneut auf Abspielen tippen.");
        else if (hasTrack && player.isPlaying()) status.setText("Wird abgespielt");
        else if (requested) status.setText("Stream wird geladen …");
        else if (hasTrack) status.setText("Pausiert");
        else if (loadingCatalog) status.setText("Mediathek wird geladen …");
        else if (catalog == null) status.setText("Mediathek konnte nicht geladen werden.");
        else status.setText("Bereit zur Wiedergabe");
        if (!scrubbing) renderPosition();
    }

    private void showArtwork(Uri uri) {
        if (Objects.equals(uri, artwork)) return;
        artwork = uri;
        cover.setImageResource(R.drawable.ic_station);
        if (uri == null) return;
        network.execute(() -> {
            try {
                HttpURLConnection connection = Catalog.connect(new URL(uri.toString()));
                Bitmap image;
                try (InputStream stream = connection.getInputStream()) {
                    image = BitmapFactory.decodeStream(stream);
                } finally {
                    connection.disconnect();
                }
                ui.post(() -> {
                    if (!isDestroyed() && uri.equals(artwork) && image != null) cover.setImageBitmap(image);
                });
            } catch (Exception ignored) {
                // The station icon is the fallback when cover art is unavailable.
            }
        });
    }

    private void renderPosition() {
        long duration = player == null ? 0 : Math.max(0, player.getDuration());
        long position = player == null ? 0 : Math.max(0, player.getCurrentPosition());
        seek.setEnabled(duration > 0 && player.isCurrentMediaItemSeekable());
        seek.setProgress(duration > 0 ? (int) (position * SEEK_STEPS / duration) : 0);
        showTime(position, duration);
    }

    private void showTime(long position, long duration) {
        times.setText(formatTime(position) + " / " + formatTime(duration));
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private void markToggle(Button button, boolean active) {
        button.setTextColor(active ? ACCENT : INK);
        button.setSelected(active);
    }

    private ScrollView buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            scroll.setOnApplyWindowInsetsListener((view, window) -> {
                Insets safe = window.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return window;
            });
        }
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(18), dp(20), dp(24));
        scroll.addView(body);
        body.addView(label("SCHWAFELPATIE FM", 15, ACCENT, true));

        cover = new ImageView(this);
        cover.setImageResource(R.drawable.ic_station);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artworkBackground = new GradientDrawable();
        artworkBackground.setColor(Color.rgb(32, 29, 25));
        artworkBackground.setCornerRadius(dp(16));
        cover.setBackground(artworkBackground);
        cover.setClipToOutline(true);
        LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(dp(160), dp(160));
        art.gravity = Gravity.CENTER_HORIZONTAL;
        art.setMargins(0, dp(20), 0, dp(16));
        body.addView(cover, art);
        status = label("", 13, ACCENT, false);
        track = label("", 26, INK, true);
        album = label("", 15, MUTED, false);
        body.addView(status);
        body.addView(track);
        body.addView(album);

        seek = new SeekBar(this);
        seek.setMax(SEEK_STEPS);
        seek.setContentDescription("Wiedergabeposition");
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onStartTrackingTouch(SeekBar bar) { scrubbing = true; }
            @Override public void onStopTrackingTouch(SeekBar bar) {
                scrubbing = false;
                seekTo(bar.getProgress());
            }
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                if (!fromUser || player == null || player.getDuration() <= 0) return;
                if (scrubbing) showTime(player.getDuration() * value / SEEK_STEPS, player.getDuration());
                else seekTo(value);
            }
        });
        body.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        times = label("0:00 / 0:00", 13, MUTED, false);
        body.addView(times);

        previous = button("⏮", "Vorheriger Song", () -> player.seekToPreviousMediaItem());
        play = button("Abspielen", null, this::togglePlayback);
        play.setBackgroundTintList(ColorStateList.valueOf(ACCENT));
        play.setTextColor(Color.rgb(20, 18, 16));
        next = button("⏭", "Nächster Song", () -> player.seekToNextMediaItem());
        body.addView(row(previous, play, next));
        shuffle = button("Shuffle", "Zufallswiedergabe", () -> player.setShuffleModeEnabled(!player.getShuffleModeEnabled()));
        repeat = button("Repeat 1", "Aktuellen Song in Endlosschleife abspielen", () -> player.setRepeatMode(
                player.getRepeatMode() == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE));
        body.addView(row(shuffle, repeat));

        TextView heading = label("Mediathek", 19, INK, true);
        heading.setPadding(0, dp(15), 0, dp(5));
        body.addView(heading);
        albums = button("Alle Alben", null, this::chooseAlbum);
        songs = button("Song auswählen", null, this::chooseSong);
        retry = button("Mediathek erneut laden", null, this::loadCatalog);
        body.addView(albums);
        body.addView(songs);
        body.addView(retry);
        return scroll;
    }

    private void seekTo(int value) {
        if (player != null && player.isCurrentMediaItemSeekable() && player.getDuration() > 0) {
            player.seekTo(player.getDuration() * value / SEEK_STEPS);
        }
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private Button button(String title, String description, Runnable action) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(title);
        view.setTextColor(INK);
        view.setContentDescription(description);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private LinearLayout row(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        for (Button button : buttons) row.addView(button, new LinearLayout.LayoutParams(0, dp(52), 1));
        return row;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
