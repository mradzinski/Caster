package com.mradzinski.caster;

import android.util.Log;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadOptions;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class CasterPlayer {
    private static final String TAG = "Caster";

    private RemoteMediaClient remoteMediaClient;
    private OnMediaLoadedListener onMediaLoadedListener;

    //Needed for NoOp instance
    CasterPlayer() { /* no-op */ }

    CasterPlayer(OnMediaLoadedListener onMediaLoadedListener) {
        this.onMediaLoadedListener = onMediaLoadedListener;
    }

    void setRemoteMediaClient(RemoteMediaClient remoteMediaClient) {
        this.remoteMediaClient = remoteMediaClient;
    }

    public RemoteMediaClient getRemoteMediaClient() { return this.remoteMediaClient; }

    /**
     * Plays the current media file if it is paused
     */
    public void play() {
        if (isPaused()) {
            remoteMediaClient.play();
        } else {
            Log.i(TAG, "Unable to play. Either remoteMediaClient is null or " +
                    "the curret media file isn't paused");
        }
    }

    /**
     * Pauses the current media file if it is playing
     */
    public void pause() {
        if (isPlaying()) {
            remoteMediaClient.pause();
        } else {
            Log.i(TAG, "Unable to pause. Either remoteMediaClient is null or " +
                    "the curret media file isn't playing");
        }
    }

    /**
     * Seeks the current media file
     *
     * @param time the number of milliseconds to seek by
     */
    public void seek(long time) {
        if (remoteMediaClient != null) {
            remoteMediaClient.seek(time);
        } else {
            Log.i(TAG, "Unable to seek. remoteMediaClient is null.");
        }
    }

    /**
     * Tries to play or pause the current media file, depending of the current state
     */
    public void togglePlayPause() {
        if (remoteMediaClient != null) {
            if (remoteMediaClient.isPlaying()) {
                remoteMediaClient.pause();
            } else if (remoteMediaClient.isPaused()) {
                remoteMediaClient.play();
            }
        } else {
            Log.i(TAG, "Unable to toggle play/pause. remoteMediaClient is null.");
        }
    }

    /**
     * Checks if the media file is playing
     *
     * @return true if the media file is playing, false otherwise
     */
    public boolean isPlaying() {
        return remoteMediaClient != null && remoteMediaClient.isPlaying();
    }

    /**
     * Checks if the media file is paused
     *
     * @return true if the media file is paused, false otherwise
     */
    public boolean isPaused() {
        return remoteMediaClient != null && remoteMediaClient.isPaused();
    }

    /**
     * Checks if the media file is buffering
     *
     * @return true if the media file is buffering, false otherwise
     */
    public boolean isBuffering() {
        return remoteMediaClient != null && remoteMediaClient.isBuffering();
    }

    /**
     * Gets the current playing media URL.
     *
     * @return The current playing media URL or null if no media has been enqueued to be played.
     */
    public @Nullable String getCurrentPlayingMediaUrl() {
        if (remoteMediaClient == null) return null;

        try {
            return remoteMediaClient.getCurrentItem().getMedia().getContentId();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Tries to load the media file and play it in the {@link ExpandedControlsActivity}
     *
     * @param mediaData Information about the media
     * @return true if attempt was successful, false otherwise
     * @see MediaData
     */
    @MainThread
    @SuppressWarnings("UnusedReturnValue")
    public boolean loadMediaAndPlay(@NonNull MediaData mediaData) {
        return loadMediaAndPlay(mediaData.createMediaInfo(), mediaData.isAutoPlay(),
                mediaData.getPosition(), mediaData.getPlaybackRate());
    }

    /**
     * Tries to load the media file and play it in the {@link ExpandedControlsActivity}
     *
     * @param mediaInfo Information about the media
     * @return true if attempt was successful, false otherwise
     * @see MediaInfo
     */
    @MainThread
    public boolean loadMediaAndPlay(@NonNull MediaInfo mediaInfo) {
        return loadMediaAndPlay(mediaInfo, true, 0, MediaData.PLAYBACK_RATE_NORMAL);
    }

    /**
     * Tries to load the media file and play it in the {@link ExpandedControlsActivity}
     *
     * @param mediaInfo Information about the media
     * @param autoPlay True if the media file should start automatically
     * @param position Start position of video in milliseconds
     * @return true if attempt was successful, false otherwise
     * @see MediaInfo
     */
    @MainThread
    public boolean loadMediaAndPlay(@NonNull MediaInfo mediaInfo, boolean autoPlay, long position, double rate) {
        return playMediaBaseMethod(mediaInfo, autoPlay, position, rate, false);
    }

    /**
     * Tries to load the media file and play in background
     *
     * @param mediaData Information about the media
     * @return true if attempt was successful, false otherwise
     * @see MediaData
     */
    @MainThread
    public boolean loadMediaAndPlayInBackground(@NonNull MediaData mediaData) {
        return loadMediaAndPlayInBackground(mediaData.createMediaInfo(), mediaData.isAutoPlay(),
                mediaData.getPosition(), mediaData.getPlaybackRate());
    }

    /**
     * Tries to load the media file and play in background
     *
     * @param mediaInfo Information about the media
     * @return true if attempt was successful, false otherwise
     * @see MediaInfo
     */
    @MainThread
    public boolean loadMediaAndPlayInBackground(@NonNull MediaInfo mediaInfo) {
        return loadMediaAndPlayInBackground(mediaInfo, true, 0, MediaData.PLAYBACK_RATE_NORMAL);
    }

    /**
     * Tries to load the media file and play in background
     *
     * @param mediaInfo Information about the media
     * @param autoPlay True if the media file should start automatically
     * @param position Start position of video in milliseconds
     * @return true if attempt was successful, false otherwise
     * @see MediaInfo
     */
    @MainThread
    public boolean loadMediaAndPlayInBackground(@NonNull MediaInfo mediaInfo, boolean autoPlay, long position, double rate) {
        return playMediaBaseMethod(mediaInfo, autoPlay, position, rate, true);
    }

    private boolean playMediaBaseMethod(MediaInfo mediaInfo, boolean autoPlay, long position, double rate, boolean inBackground) {
        if (remoteMediaClient == null) return false;
        if (!inBackground) remoteMediaClient.registerCallback(createRemoteMediaClientListener());

        MediaLoadOptions options = new MediaLoadOptions.Builder()
                .setAutoplay(autoPlay)
                .setPlayPosition(position)
                .setPlaybackRate(rate)
                .build();

        remoteMediaClient.load(mediaInfo, options);

        return true;
    }

    private RemoteMediaClient.Callback createRemoteMediaClientListener() {
        return new RemoteMediaClient.Callback() {
            @Override
            public void onStatusUpdated() {
                onMediaLoadedListener.onMediaLoaded();
                remoteMediaClient.unregisterCallback(this);
            }

            @Override
            public void onMetadataUpdated() {
                //no-op
            }

            @Override
            public void onQueueStatusUpdated() {
                //no-op
            }

            @Override
            public void onPreloadStatusUpdated() {
                //no-op
            }

            @Override
            public void onSendingRemoteMediaRequest() {
                //no-op
            }

            @Override
            public void onAdBreakStatusUpdated() {
                //no-op
            }
        };
    }

    interface OnMediaLoadedListener {
        void onMediaLoaded();
    }
}
