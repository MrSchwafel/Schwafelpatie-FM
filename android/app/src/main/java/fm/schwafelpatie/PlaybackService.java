package fm.schwafelpatie;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSourceBitmapLoader;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.extractor.DefaultExtractorsFactory;
import androidx.media3.extractor.mp3.Mp3Extractor;
import androidx.media3.session.CacheBitmapLoader;
import androidx.media3.session.CommandButton;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@UnstableApi
public final class PlaybackService extends MediaSessionService {
    private static final long CACHE_LIMIT = 256L * 1024 * 1024;
    private SimpleCache audioCache;
    private MediaSession session;

    @Override
    public void onCreate() {
        super.onCreate();
        audioCache = new SimpleCache(new File(getCacheDir(), "audio"),
                new LeastRecentlyUsedCacheEvictor(CACHE_LIMIT), new StandaloneDatabaseProvider(this));
        CacheDataSource.Factory cachedAudio = new CacheDataSource.Factory()
                .setCache(audioCache)
                .setUpstreamDataSourceFactory(new DefaultDataSource.Factory(this))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        // The catalog already supplies the chosen artwork to the player and the media session.
        DefaultExtractorsFactory extractors = new DefaultExtractorsFactory()
                .setMp3ExtractorFlags(Mp3Extractor.FLAG_DISABLE_ARTWORK_METADATA);
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(cachedAudio, extractors))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        Intent openPlayer = new Intent(this, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this, 0, openPlayer, PendingIntent.FLAG_IMMUTABLE);
        Player controls = new HistoryPlayer(player);
        session = new MediaSession.Builder(this, controls)
                .setSessionActivity(sessionActivity)
                .setBitmapLoader(new CacheBitmapLoader(new DataSourceBitmapLoader.Builder(this)
                        .setMaximumOutputDimension(768).build()))
                .setMediaButtonPreferences(mediaButtons(controls))
                .build();
        controls.addListener(new Player.Listener() {
            @Override
            public void onEvents(Player source, Player.Events events) {
                if (session != null && events.containsAny(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED, Player.EVENT_REPEAT_MODE_CHANGED)) {
                    session.setMediaButtonPreferences(mediaButtons(source));
                }
            }
        });
    }

    private List<CommandButton> mediaButtons(Player player) {
        boolean shuffle = player.getShuffleModeEnabled();
        boolean repeat = player.getRepeatMode() == Player.REPEAT_MODE_ONE;
        return Arrays.asList(
                new CommandButton.Builder(shuffle ? CommandButton.ICON_SHUFFLE_ON : CommandButton.ICON_SHUFFLE_OFF)
                        .setDisplayName(shuffle ? "Shuffle ausschalten" : "Shuffle einschalten")
                        .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build(),
                new CommandButton.Builder(repeat ? CommandButton.ICON_REPEAT_ONE : CommandButton.ICON_REPEAT_OFF)
                        .setDisplayName(repeat ? "Titelwiederholung ausschalten" : "Titelwiederholung einschalten")
                        .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, repeat ? Player.REPEAT_MODE_OFF : Player.REPEAT_MODE_ONE)
                        .setSlots(CommandButton.SLOT_OVERFLOW)
                        .build());
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) {
        return session;
    }

    @Override
    public void onDestroy() {
        if (session != null) {
            session.getPlayer().release();
            session.release();
            session = null;
        }
        if (audioCache != null) {
            audioCache.release();
            audioCache = null;
        }
        super.onDestroy();
    }
}
