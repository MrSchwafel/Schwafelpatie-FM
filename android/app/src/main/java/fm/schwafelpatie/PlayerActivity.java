package fm.schwafelpatie;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@UnstableApi
public final class PlayerActivity extends Activity {
    private static final int ACCENT = Color.rgb(255, 196, 108);
    private static final int INK = Color.rgb(248, 242, 232);
    private static final int MUTED = Color.rgb(191, 178, 161);
    private static final int BACKGROUND = 0xff141210;
    private static final int SURFACE = 0xff201d19;
    private static final int RAISED = 0xff2b2620;
    private static final int BORDER = 0xff443b31;
    private static final int SEEK_STEPS = 1000;
    private static final String SEBI_HYMN = "sebi-ist-schuld-die-hymne-des-gescheiterten-fortschritts";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private Catalog catalog;
    private SharedPreferences preferences;
    private Set<String> selectedAlbums;
    private boolean loadingCatalog;
    private boolean catalogRefreshFailed;
    private boolean scrubbing;
    private Uri artwork;
    private ListenableFuture<MediaController> connection;
    private MediaController player;
    private TextView status, track, album, elapsed, durationLabel, catalogStatus;
    private GradientDrawable statusDot;
    private ImageView cover;
    private SeekBar seek;
    private ImageButton play, previous, next, shuffle, repeat;
    private Button albums, songs, retry, newest, sebiSoundtrack, libraryButton;
    private AlertDialog libraryDialog;
    private MediaItem sebiSong;

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
        preferences = getSharedPreferences("player", MODE_PRIVATE);
        selectedAlbums = new HashSet<>(preferences.getStringSet("albums", Collections.emptySet()));
        setContentView(buildScreen());
    }

    @Override
    protected void onStart() {
        super.onStart();
        loadCatalog();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(this, token).buildAsync();
        connection = future;
        future.addListener(() -> {
            if (connection != future) return;
            try {
                player = future.get();
                player.addListener(listener);
                syncQueue();
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
        libraryDialog.dismiss();
        network.shutdownNow();
        super.onDestroy();
    }

    private void loadCatalog() {
        if (loadingCatalog) return;
        loadingCatalog = true;
        render();
        network.execute(() -> {
            Catalog cached = Catalog.readCached(this);
            if (cached != null) {
                ui.post(() -> {
                    if (!isDestroyed() && catalog == null) applyCatalog(cached);
                });
            }
            Catalog loaded = null;
            try {
                loaded = Catalog.refresh(this);
            } catch (Exception ignored) {
                // Keep using the last catalog until the next successful refresh.
            }
            Catalog result = loaded;
            ui.post(() -> {
                if (isDestroyed()) return;
                loadingCatalog = false;
                catalogRefreshFailed = result == null;
                if (result != null) applyCatalog(result);
                else render();
            });
        });
    }

    private void applyCatalog(Catalog result) {
        catalog = result;
        sebiSong = null;
        for (MediaItem item : catalog.tracks) {
            if (SEBI_HYMN.equals(item.mediaId)) {
                sebiSong = item;
                break;
            }
        }
        syncQueue();
        render();
    }

    private void saveAlbums(Set<String> selection) {
        selectedAlbums = selection.size() == catalog.albums.size() ? Collections.emptySet() : selection;
        preferences.edit().putStringSet("albums", selectedAlbums).apply();
    }

    private void chooseAlbum() {
        String[] options = catalog.albums.toArray(new String[0]);
        Set<String> selection = new HashSet<>(selectedAlbums);
        selection.retainAll(catalog.albums);
        boolean[] checked = new boolean[options.length];
        for (int index = 0; index < options.length; index++) checked[index] = selection.contains(options[index]);
        new AlertDialog.Builder(this)
                .setTitle("Alben zum Hören auswählen")
                .setMultiChoiceItems(options, checked, (dialog, index, enabled) -> {
                    if (enabled) selection.add(options[index]);
                    else selection.remove(options[index]);
                })
                .setNegativeButton("Abbrechen", null)
                .setNeutralButton("Alle Alben", (dialog, button) -> applyAlbums(Collections.emptySet()))
                .setPositiveButton("Übernehmen", (dialog, button) -> applyAlbums(selection))
                .show();
    }

    private void applyAlbums(Set<String> selection) {
        saveAlbums(selection);
        syncQueue();
        render();
    }

    private void syncQueue() {
        if (catalog == null || player == null || player.getCurrentMediaItem() == null) return;
        List<MediaItem> queue = new ArrayList<>(catalog.tracksIn(selectedAlbums));
        MediaItem current = player.getCurrentMediaItem();
        int index = indexOf(queue, current.mediaId);
        long position = player.getCurrentPosition();
        if (index < 0) {
            if (player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED) {
                Bundle extras = new Bundle();
                extras.putBoolean(HistoryPlayer.FINISH_CURRENT, true);
                queue.add(0, current.buildUpon().setUri(current.requestMetadata.mediaUri).setMediaMetadata(
                        current.mediaMetadata.buildUpon().setExtras(extras).build()).build());
            } else {
                position = 0;
            }
            index = 0;
        }
        if (queue.size() == player.getMediaItemCount()) {
            boolean same = true;
            for (int i = 0; i < queue.size(); i++) {
                MediaItem existing = player.getMediaItemAt(i);
                MediaItem candidate = queue.get(i);
                if (!candidate.mediaId.equals(existing.mediaId) || !candidate.requestMetadata.equals(existing.requestMetadata)
                        || !candidate.mediaMetadata.equals(existing.mediaMetadata)
                        || HistoryPlayer.isFinishing(candidate) != HistoryPlayer.isFinishing(existing)) {
                    same = false;
                    break;
                }
            }
            if (same) return;
        }
        player.setMediaItems(queue, index, position);
        if (player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
    }

    private int indexOf(List<MediaItem> queue, String key) {
        for (int index = 0; index < queue.size(); index++) {
            if (key.equals(queue.get(index).mediaId)) return index;
        }
        return -1;
    }

    private void chooseSong() {
        List<MediaItem> queue = catalog.tracksIn(selectedAlbums);
        String[] titles = new String[queue.size()];
        for (int index = 0; index < queue.size(); index++) {
            MediaItem item = queue.get(index);
            titles[index] = item.mediaMetadata.title + " · " + item.mediaMetadata.albumTitle;
        }
        new AlertDialog.Builder(this)
                .setTitle("Song auswählen")
                .setItems(titles, (dialog, index) -> {
                    libraryDialog.dismiss();
                    start(queue, index);
                })
                .show();
    }

    private void playLibrarySong(MediaItem item) {
        if (item == null) return;
        List<MediaItem> queue = catalog.tracksIn(selectedAlbums);
        int index = indexOf(queue, item.mediaId);
        if (index >= 0) start(queue, index);
    }

    private void playSebiSoundtrack() {
        if (sebiSong == null) return;
        saveAlbums(Collections.singleton(String.valueOf(sebiSong.mediaMetadata.albumTitle)));
        playLibrarySong(sebiSong);
        render();
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
            if (catalog != null) start(catalog.tracksIn(selectedAlbums), 0);
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
        play.setContentDescription(requested ? "Pausieren" : "Abspielen");
        int playIcon = requested ? R.drawable.ic_pause : R.drawable.ic_play;
        if (!Integer.valueOf(playIcon).equals(play.getTag())) {
            play.setImageResource(playIcon);
            play.setTag(playIcon);
        }
        previous.setEnabled(hasTrack && player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM));
        next.setEnabled(hasTrack && player.hasNextMediaItem());
        repeat.setEnabled(ready);
        shuffle.setEnabled(ready);
        if (ready) {
            repeat.setSelected(player.getRepeatMode() == Player.REPEAT_MODE_ONE);
            shuffle.setSelected(player.getShuffleModeEnabled());
        }
        albums.setEnabled(catalog != null);
        List<String> activeAlbums = new ArrayList<>();
        if (catalog != null) {
            for (String name : catalog.albums) if (selectedAlbums.contains(name)) activeAlbums.add(name);
        }
        albums.setText(activeAlbums.isEmpty() ? "Alle Alben" : activeAlbums.size() == 1
                ? activeAlbums.get(0) : activeAlbums.size() + " Alben ausgewählt");
        List<MediaItem> visible = catalog == null ? Collections.emptyList() : catalog.tracksIn(selectedAlbums);
        libraryButton.setText(visible.isEmpty() ? "Mediathek" : "Mediathek · " + visible.size()
                + (visible.size() == 1 ? " Song" : " Songs"));
        songs.setEnabled(ready && !visible.isEmpty());
        retry.setEnabled(!loadingCatalog);
        catalogStatus.setText(loadingCatalog ? "Mediathek wird aktualisiert …" : catalogRefreshFailed
                ? (catalog == null ? "Abruf fehlgeschlagen" : "Abruf fehlgeschlagen · gespeicherte Mediathek") : "");
        catalogStatus.setVisibility(loadingCatalog || catalogRefreshFailed ? TextView.VISIBLE : TextView.GONE);
        MediaItem latest = catalog == null ? null : catalog.newest;
        boolean showNewest = latest != null && indexOf(visible, latest.mediaId) >= 0;
        newest.setVisibility(showNewest ? Button.VISIBLE : Button.GONE);
        newest.setEnabled(ready);
        if (showNewest) newest.setText("Neu in der Mediathek · " + latest.mediaMetadata.title);
        sebiSoundtrack.setEnabled(ready && sebiSong != null);

        MediaItem item = hasTrack ? player.getCurrentMediaItem() : null;
        MediaItem displayed = item != null || visible.isEmpty() ? item : visible.get(0);
        track.setText(displayed == null ? "Schwafelpatie FM" : displayed.mediaMetadata.title);
        album.setText(displayed == null ? "" : displayed.mediaMetadata.albumTitle);
        boolean inAlbum = displayed != null && indexOf(visible, displayed.mediaId) >= 0;
        songs.setText(inAlbum ? displayed.mediaMetadata.title : "Song auswählen");
        showArtwork(displayed == null ? null : displayed.mediaMetadata.artworkUri);
        boolean playing = hasTrack && player.isPlaying();
        status.setTextColor(playing ? ACCENT : MUTED);
        statusDot.setColor(playing ? ACCENT : 0xff8c7e6e);
        if (hasTrack && player.getPlayerError() != null) status.setText("Stream unterbrochen. Erneut auf Abspielen tippen.");
        else if (playing) status.setText("Wird abgespielt");
        else if (requested) status.setText("Stream wird geladen …");
        else if (hasTrack) status.setText("Pausiert");
        else if (loadingCatalog && catalog == null) status.setText("Mediathek wird geladen …");
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
        elapsed.setText(formatTime(position));
        durationLabel.setText(formatTime(duration));
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
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
        Configuration config = getResources().getConfiguration();
        boolean wide = config.screenWidthDp >= 540 && config.screenHeightDp < 500;
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        body.setPadding(dp(14), dp(12), dp(14), dp(12));
        int width = wide ? ViewGroup.LayoutParams.MATCH_PARENT : config.screenWidthDp >= 600
                ? dp(520) : ViewGroup.LayoutParams.MATCH_PARENT;
        scroll.addView(body, new ScrollView.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        LinearLayout station = body;
        if (wide) {
            station = new LinearLayout(this);
            station.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams column = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            column.setMarginEnd(dp(12));
            body.addView(station, column);
        }

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_station);
        mark.setBackground(shape(RAISED, 10, 0xff8a663a));
        mark.setClipToOutline(true);
        LinearLayout.LayoutParams markSize = new LinearLayout.LayoutParams(dp(38), dp(38));
        markSize.setMarginEnd(dp(10));
        header.addView(mark, markSize);
        String name = "Schwafelpatie FM";
        SpannableString brand = new SpannableString(name);
        brand.setSpan(new ForegroundColorSpan(ACCENT), name.length() - 2, name.length(), 0);
        TextView heading = label("", 25, INK, true);
        heading.setText(brand);
        heading.setLetterSpacing(-.05f);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        station.addView(header);

        sebiSoundtrack = soundtrackButton();
        addWithTopMargin(station, sebiSoundtrack, 10);

        LinearLayout deck = new LinearLayout(this);
        deck.setOrientation(LinearLayout.VERTICAL);
        deck.setBackground(shape(SURFACE, 18, BORDER));
        deck.setPadding(dp(12), dp(12), dp(12), dp(8));
        if (wide) body.addView(deck, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        else addWithTopMargin(body, deck, 10);

        LinearLayout nowPlaying = new LinearLayout(this);
        nowPlaying.setGravity(Gravity.CENTER_VERTICAL);
        deck.addView(nowPlaying);

        cover = new ImageView(this);
        cover.setImageResource(R.drawable.ic_station);
        cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(shape(RAISED, 12, BORDER));
        cover.setClipToOutline(true);
        int coverSize = dp(config.screenHeightDp >= 650 ? 80 : 56);
        LinearLayout.LayoutParams art = new LinearLayout.LayoutParams(coverSize, coverSize);
        art.setMarginEnd(dp(10));
        nowPlaying.addView(cover, art);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        nowPlaying.addView(details, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout state = new LinearLayout(this);
        state.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = shape(0xff8c7e6e, 4, Color.TRANSPARENT);
        ImageView dot = new ImageView(this);
        dot.setImageDrawable(statusDot);
        LinearLayout.LayoutParams dotSize = new LinearLayout.LayoutParams(dp(7), dp(7));
        dotSize.setMarginEnd(dp(8));
        state.addView(dot, dotSize);
        status = label("", 11, MUTED, true);
        status.setAllCaps(true);
        status.setLetterSpacing(.11f);
        state.addView(status);
        details.addView(state);
        track = label("", 19, INK, true);
        track.setLetterSpacing(-.035f);
        track.setMaxLines(2);
        track.setEllipsize(TextUtils.TruncateAt.END);
        track.setPadding(0, dp(3), 0, 0);
        album = label("", 13, MUTED, false);
        album.setSingleLine(true);
        album.setEllipsize(TextUtils.TruncateAt.END);
        details.addView(track);
        details.addView(album);

        seek = new SeekBar(this);
        seek.setMax(SEEK_STEPS);
        seek.setContentDescription("Wiedergabeposition");
        seek.setPadding(dp(4), 0, dp(4), 0);
        seek.setProgressTintList(ColorStateList.valueOf(ACCENT));
        seek.setProgressBackgroundTintList(ColorStateList.valueOf(0xff5a4b3a));
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
        deck.addView(seek, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
        LinearLayout times = new LinearLayout(this);
        elapsed = label("0:00", 12, MUTED, false);
        durationLabel = label("0:00", 12, MUTED, false);
        times.addView(elapsed, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        times.addView(durationLabel);
        deck.addView(times);

        shuffle = transport(R.drawable.ic_shuffle, "Zufallswiedergabe", false, () -> player.setShuffleModeEnabled(!player.getShuffleModeEnabled()));
        previous = transport(R.drawable.ic_previous, "Vorheriger Song", false, () -> player.seekToPreviousMediaItem());
        play = transport(R.drawable.ic_play, "Abspielen", true, this::togglePlayback);
        next = transport(R.drawable.ic_next, "Nächster Song", false, () -> player.seekToNextMediaItem());
        repeat = transport(R.drawable.ic_repeat, "Aktuellen Song in Endlosschleife abspielen", false, () -> player.setRepeatMode(
                player.getRepeatMode() == Player.REPEAT_MODE_ONE ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE));
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setPadding(0, dp(4), 0, 0);
        for (ImageButton button : new ImageButton[]{shuffle, previous, play, next, repeat}) {
            int size = dp(button == play ? 52 : 48);
            controls.addView(button, wide ? new LinearLayout.LayoutParams(0, size, 1)
                    : new LinearLayout.LayoutParams(size, size));
        }
        deck.addView(controls);

        libraryDialog = buildLibraryDialog();
        libraryButton = field("Mediathek", () -> libraryDialog.show());
        addWithTopMargin(station, libraryButton, 10);
        return scroll;
    }

    private Button soundtrackButton() {
        String title = "Sebas ist schuld.";
        String action = "Soundtrack starten";
        String text = title + "\nWegen ihm mache ich KI-Musik.\n" + action;
        SpannableString copy = new SpannableString(text);
        copy.setSpan(new RelativeSizeSpan(1.4f), 0, title.length(), 0);
        copy.setSpan(new ForegroundColorSpan(ACCENT), 0, title.length(), 0);
        copy.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), 0);
        copy.setSpan(new StyleSpan(Typeface.BOLD), text.length() - action.length(), text.length(), 0);
        Button button = field("", this::playSebiSoundtrack);
        button.setSingleLine(false);
        button.setText(copy);
        button.setTextSize(14);
        button.setPadding(dp(14), dp(8), dp(12), dp(8));
        Drawable icon = getDrawable(R.drawable.ic_play).mutate();
        icon.setTint(ACCENT);
        button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x35ffc46c), shape(0xff49311e, 14, 0xffaa753a), null));
        return button;
    }

    private AlertDialog buildLibraryDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout library = new LinearLayout(this);
        library.setOrientation(LinearLayout.VERTICAL);
        library.setPadding(dp(20), 0, dp(20), dp(8));
        scroll.addView(library);
        albums = field("Alle Alben", this::chooseAlbum);
        songs = field("Song auswählen", this::chooseSong);
        retry = field("Mediathek aktualisieren", this::loadCatalog);
        catalogStatus = label("", 12, MUTED, false);
        newest = field("Neu in der Mediathek", () -> {
            libraryDialog.dismiss();
            playLibrarySong(catalog.newest);
        });
        newest.setSingleLine(false);
        addField(library, "Alben", albums);
        addField(library, "Song", songs);
        addWithTopMargin(library, newest, 8);
        library.addView(catalogStatus);
        addWithTopMargin(library, retry, 8);
        return new AlertDialog.Builder(this).setTitle("Mediathek").setView(scroll)
                .setPositiveButton("Schließen", null).create();
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

    private Button field(String title, Runnable action) {
        Button view = new Button(this);
        view.setAllCaps(false);
        view.setText(title);
        view.setTextColor(controlColors(INK));
        view.setTextSize(15);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(12), 0, dp(10), 0);
        view.setMinHeight(dp(46));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28ffc46c), shape(BACKGROUND, 8, BORDER), null));
        Drawable arrow = getDrawable(R.drawable.ic_chevron).mutate();
        arrow.setTint(MUTED);
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, arrow, null);
        view.setCompoundDrawablePadding(dp(6));
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private ImageButton transport(int icon, String description, boolean primary, Runnable action) {
        ImageButton view = new ImageButton(this);
        view.setContentDescription(description);
        view.setPadding(0, 0, 0, 0);
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setImageResource(icon);
        view.setImageTintList(controlColors(primary ? BACKGROUND : INK));
        StateListDrawable fill = new StateListDrawable();
        fill.addState(new int[]{android.R.attr.state_selected}, shape(ACCENT, 28, Color.TRANSPARENT));
        fill.addState(new int[0], shape(primary ? ACCENT : Color.TRANSPARENT, 28, Color.TRANSPARENT));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x50b88a47), fill, shape(Color.WHITE, 28, Color.TRANSPARENT)));
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private ColorStateList controlColors(int normal) {
        return new ColorStateList(new int[][]{
                new int[]{-android.R.attr.state_enabled},
                new int[]{android.R.attr.state_selected},
                new int[0]
        }, new int[]{0xff8c7e6e, BACKGROUND, normal});
    }

    private void addWithTopMargin(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        size.topMargin = dp(margin);
        parent.addView(child, size);
    }

    private void addField(LinearLayout library, String name, Button field) {
        TextView caption = label(name, 12, MUTED, true);
        caption.setPadding(0, dp(8), 0, dp(4));
        library.addView(caption);
        library.addView(field);
    }

    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
