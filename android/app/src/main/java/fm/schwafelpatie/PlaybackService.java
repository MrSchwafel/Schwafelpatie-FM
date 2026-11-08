package fm.schwafelpatie;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.io.File;

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
        ExoPlayer player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(this).setDataSourceFactory(cachedAudio))
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .build();
        Intent openPlayer = new Intent(this, PlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent sessionActivity = PendingIntent.getActivity(
                this, 0, openPlayer, PendingIntent.FLAG_IMMUTABLE);
        session = new MediaSession.Builder(this, new HistoryPlayer(player))
                .setSessionActivity(sessionActivity)
                .build();
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
