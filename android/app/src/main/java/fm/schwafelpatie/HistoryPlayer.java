package fm.schwafelpatie;

import android.os.Bundle;
import android.os.Handler;

import androidx.media3.common.C;
import androidx.media3.common.ForwardingSimpleBasePlayer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.ShuffleOrder;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

@UnstableApi
final class HistoryPlayer extends ForwardingSimpleBasePlayer {
    static final String FINISH_CURRENT = "fm.schwafelpatie.finishCurrent";
    private final ExoPlayer playback;
    private final Deque<String> history = new ArrayDeque<>();
    private String currentId;
    private String returningTo;
    private int repeatMode = Player.REPEAT_MODE_OFF;

    HistoryPlayer(ExoPlayer playback) {
        super(playback);
        this.playback = playback;
        Handler handler = new Handler(playback.getApplicationLooper());
        playback.addListener(new Player.Listener() {
            @Override
            public void onMediaItemTransition(MediaItem item, int reason) {
                String nextId = item == null ? null : item.mediaId;
                if (Objects.equals(nextId, currentId)) return;
                if (currentId != null && !Objects.equals(nextId, returningTo)) {
                    if (history.size() == 200) history.removeFirst();
                    history.addLast(currentId);
                }
                currentId = nextId;
                returningTo = null;
                invalidateState();
                // Remove a song that was kept only to finish playing after an album change.
                handler.post(HistoryPlayer.this::removeFinishedSong);
            }
        });
    }

    static boolean isFinishing(MediaItem item) {
        Bundle extras = item == null ? null : item.mediaMetadata.extras;
        return extras != null && extras.getBoolean(FINISH_CURRENT);
    }

    @Override
    protected State getState() {
        State state = super.getState();
        boolean canGoBack = previousIndex(false) != C.INDEX_UNSET;
        return state.buildUpon().setRepeatMode(repeatMode).setAvailableCommands(
                state.availableCommands.buildUpon()
                        .remove(COMMAND_SEEK_TO_PREVIOUS).remove(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        .addIf(COMMAND_SEEK_TO_PREVIOUS, canGoBack)
                        .addIf(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, canGoBack)
                        .build()).build();
    }

    @Override
    protected ListenableFuture<?> handleSeek(int mediaItemIndex, long positionMs, int seekCommand) {
        if (seekCommand != COMMAND_SEEK_TO_PREVIOUS && seekCommand != COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) {
            return super.handleSeek(mediaItemIndex, positionMs, seekCommand);
        }
        int index = previousIndex(true);
        if (index != C.INDEX_UNSET) {
            returningTo = playback.getMediaItemAt(index).mediaId;
            playback.seekToDefaultPosition(index);
        }
        return Futures.immediateVoidFuture();
    }

    @Override
    protected ListenableFuture<?> handleSetMediaItems(List<MediaItem> items, int startIndex, long startPositionMs) {
        ListenableFuture<?> result = super.handleSetMediaItems(items, startIndex, startPositionMs);
        if (playback.getShuffleModeEnabled()) shuffleFromCurrent();
        updateRepeatMode();
        return result;
    }

    @Override
    protected ListenableFuture<?> handleSetShuffleModeEnabled(boolean enabled) {
        if (enabled) shuffleFromCurrent();
        return super.handleSetShuffleModeEnabled(enabled);
    }

    @Override
    protected ListenableFuture<?> handleSetRepeatMode(int mode) {
        repeatMode = mode;
        updateRepeatMode();
        return Futures.immediateVoidFuture();
    }

    private int previousIndex(boolean consume) {
        for (Iterator<String> iterator = history.descendingIterator(); iterator.hasNext();) {
            String id = iterator.next();
            if (consume) iterator.remove();
            if (id.equals(currentId)) continue;
            for (int index = 0; index < playback.getMediaItemCount(); index++) {
                MediaItem item = playback.getMediaItemAt(index);
                if (id.equals(item.mediaId) && !isFinishing(item)) return index;
            }
        }
        return C.INDEX_UNSET;
    }

    private void shuffleFromCurrent() {
        int size = playback.getMediaItemCount();
        if (size < 2) return;
        int current = playback.getCurrentMediaItemIndex();
        List<Integer> upcoming = new ArrayList<>(size - 1);
        for (int index = 0; index < size; index++) if (index != current) upcoming.add(index);
        Collections.shuffle(upcoming);
        int[] order = new int[size];
        order[0] = current;
        for (int index = 1; index < size; index++) order[index] = upcoming.get(index - 1);
        playback.setShuffleOrder(new ShuffleOrder.DefaultShuffleOrder(order, System.nanoTime()));
    }

    private void removeFinishedSong() {
        if (playback.getCurrentMediaItem() == null || isFinishing(playback.getCurrentMediaItem())) return;
        for (int index = 0; index < playback.getMediaItemCount(); index++) {
            if (isFinishing(playback.getMediaItemAt(index))) {
                playback.removeMediaItem(index);
                break;
            }
        }
        updateRepeatMode();
    }

    private void updateRepeatMode() {
        playback.setRepeatMode(isFinishing(playback.getCurrentMediaItem()) ? Player.REPEAT_MODE_OFF : repeatMode);
    }
}
