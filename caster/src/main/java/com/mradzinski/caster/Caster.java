package com.mradzinski.caster;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.app.MediaRouteButton;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.android.gms.cast.CastMediaControlIntent;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.lang.ref.WeakReference;

/**
 * Core class of Caster. It manages buttons/widgets and gives access to the media player.
 */
public class Caster implements CasterPlayer.OnMediaLoadedListener {
    private final static String TAG = "Caster";
    static String receiverId = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;
    static CastOptions customCastOptions;

    private SessionManagerListener<CastSession> sessionManagerListener;
    private OnConnectChangeListener onConnectChangeListener;
    private OnCastSessionUpdatedListener onCastSessionUpdatedListener;
    private OnCastSessionStateChanged onCastSessionStateChanged;
    private OnCastSessionProgressUpdateListener onCastSessionProgressUpdateListener;

    private CastSession castSession;
    private CasterPlayer casterPlayer;
    private WeakReference<Activity> activity;
    private IntroductoryOverlay introductionOverlay;

    private boolean deliveredFinishStatus = false;
    private boolean deliveredPlayingVideo = false;
    private boolean deliveredPausedVideo = false;

    private RemoteMediaClient.ProgressListener progressListener = new RemoteMediaClient.ProgressListener() {
        @Override
        public void onProgressUpdated(long progressMs, long durationMs) {
            if (onCastSessionProgressUpdateListener != null) {
                onCastSessionProgressUpdateListener.onProgressUpdated(progressMs, durationMs);
            }
        }
    };

    private RemoteMediaClient.Callback mediaListener = new RemoteMediaClient.Callback() {
        @Override
        public void onStatusUpdated() {
            RemoteMediaClient client = null;
            MediaStatus mediaStatus = null;

            try {
                client = castSession.getRemoteMediaClient();
                if (client != null) mediaStatus = client.getMediaStatus();
            } catch (Exception ignored) {}

            if (client != null && mediaStatus != null) {
                int playerState = mediaStatus.getPlayerState();
                int clientIdleReason = client.getIdleReason();

                if (playerState == MediaStatus.PLAYER_STATE_BUFFERING) return;

                if (playerState == MediaStatus.PLAYER_STATE_IDLE && clientIdleReason == MediaStatus.IDLE_REASON_FINISHED) {
                    if (onCastSessionStateChanged != null && !deliveredFinishStatus) {
                        onCastSessionStateChanged.onCastSessionFinished();
                        deliveredFinishStatus = true;
                        deliveredPlayingVideo = false;
                        deliveredPausedVideo = false;
                    }
                }

                if (playerState == MediaStatus.PLAYER_STATE_PLAYING) {
                    if (onCastSessionStateChanged != null && !deliveredPlayingVideo) {
                        onCastSessionStateChanged.onCastSessionPlaying();
                        deliveredFinishStatus = false;
                        deliveredPlayingVideo = true;
                        deliveredPausedVideo = false;
                    }
                }

                if (playerState == MediaStatus.PLAYER_STATE_PAUSED) {
                    if (onCastSessionStateChanged != null && !deliveredPausedVideo) {
                        onCastSessionStateChanged.onCastSessionPaused();
                        deliveredFinishStatus = false;
                        deliveredPlayingVideo = false;
                        deliveredPausedVideo = true;
                    }
                }
            }
        }

        @Override public void onMetadataUpdated() {}
        @Override public void onQueueStatusUpdated() {}
        @Override public void onPreloadStatusUpdated() {}
        @Override public void onSendingRemoteMediaRequest() {}
        @Override public void onAdBreakStatusUpdated() {}
    };

    /**
     * Sets the custom receiver ID. Should be used in the {@link Application} class.
     *
     * @param receiverId the custom receiver ID, e.g. Styled Media Receiver - with custom logo and background
     */
    public static void configure(@NonNull String receiverId) {
        Caster.receiverId = receiverId;
    }

    /**
     * Sets the custom CastOptions, should be used in the {@link Application} class.
     *
     * @param castOptions the custom CastOptions object, must include a receiver ID
     */
    public static void configure(@NonNull CastOptions castOptions) {
        Caster.customCastOptions = castOptions;
    }

    /**
     * Creates the Caster object.
     *
     * @param activity {@link Activity} in which Caster object is created
     * @return the Caster object
     */
    public static Caster create(@NonNull Activity activity) {
        int playServicesState = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity);
        if (playServicesState == ConnectionResult.SUCCESS) {
            return new Caster(activity);
        } else {
            Log.w(Caster.TAG, "Google Play services not found on a device, Caster won't work.");
            return new CasterNoOp();
        }
    }

    //Needed for NoOp instance
    Caster() {/* no-op */ }

    private Caster(@NonNull Activity activity) {
        this.activity = new WeakReference<>(activity);
        sessionManagerListener = createSessionManagerListener();
        casterPlayer = new CasterPlayer(this);

        activity.getApplication().registerActivityLifecycleCallbacks(createActivityCallbacks());

        CastContext.getSharedInstance(activity).addCastStateListener(createCastStateListener());
    }

    /**
     * Gives access to {@link CasterPlayer}, which allows to control the media files.
     *
     * @return the instance of {@link CasterPlayer}
     */
    public CasterPlayer getPlayer() {
        return casterPlayer;
    }

    /**
     * Checks if a Google Cast device is connected.
     *
     * @return true if a Google Cast is connected, false otherwise
     */
    public boolean isConnected() {
        return castSession != null;
    }

    /**
     * Adds the discovery menu item on a toolbar and creates Introduction Overlay
     * Should be used in {@link Activity#onCreateOptionsMenu(Menu)}.
     *
     * @param menu Menu in which MenuItem should be added
     */
    @UiThread
    public void addMediaRouteMenuItem(@NonNull Menu menu) {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        theActivity.getMenuInflater().inflate(R.menu.caster_discovery, menu);
        setUpMediaRouteMenuItem(menu);
        MenuItem menuItem = menu.findItem(R.id.caster_media_route_menu_item);
        introductionOverlay = createIntroductionOverlay(menuItem);
    }

    /**
     * Makes {@link MediaRouteButton} react to discovery events.
     * Must be run on UiThread.
     *
     * @param mediaRouteButton Button to be set up
     */
    @UiThread
    public void setUpMediaRouteButton(@NonNull MediaRouteButton mediaRouteButton) {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        CastButtonFactory.setUpMediaRouteButton(theActivity, mediaRouteButton);
    }

    /**
     * Adds the Mini Controller at the bottom of Activity's layout.
     * Must be run on UiThread.
     *
     * @return the Caster instance
     */
    @UiThread
    public Caster withMiniController() {
        addMiniController();
        return this;
    }

    /**
     * Adds the Mini Controller at the bottom of Activity's layout
     * Must be run on UiThread.
     */
    @UiThread
    public void addMiniController() {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        ViewGroup contentView = theActivity.findViewById(android.R.id.content);
        View rootView = contentView.getChildAt(0);
        LinearLayout linearLayout = new LinearLayout(theActivity);
        LinearLayout.LayoutParams linearLayoutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(linearLayoutParams);

        contentView.removeView(rootView);

        ViewGroup.LayoutParams oldRootParams = rootView.getLayoutParams();
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(oldRootParams.width, 0, 1f);
        rootView.setLayoutParams(rootParams);

        linearLayout.addView(rootView);
        theActivity.getLayoutInflater().inflate(R.layout.mini_controller, linearLayout, true);
        theActivity.setContentView(linearLayout);
    }

    /**
     * Sets {@link OnConnectChangeListener}
     *
     * @param onConnectChangeListener Connect change callback
     */
    public void setOnConnectChangeListener(@Nullable OnConnectChangeListener onConnectChangeListener) {
        this.onConnectChangeListener = onConnectChangeListener;
    }

    /**
     * Sets {@link OnCastSessionUpdatedListener}
     *
     * @param onCastSessionUpdatedListener Cast session updated callback
     */
    public void setOnCastSessionUpdatedListener(@Nullable OnCastSessionUpdatedListener onCastSessionUpdatedListener) {
        this.onCastSessionUpdatedListener = onCastSessionUpdatedListener;
    }

    public void setOnCastSessionProgressUpdateListener(@Nullable OnCastSessionProgressUpdateListener onCastSessionProgressUpdateListener) {
        this.onCastSessionProgressUpdateListener = onCastSessionProgressUpdateListener;
    }

    public void setOnCastSessionStateChanged(@Nullable OnCastSessionStateChanged onCastSessionStateChanged) {
        this.onCastSessionStateChanged = onCastSessionStateChanged;
    }

    private void setUpMediaRouteMenuItem(Menu menu) {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        CastButtonFactory.setUpMediaRouteButton(theActivity, menu, R.id.caster_media_route_menu_item);
    }

    @NonNull
    private CastStateListener createCastStateListener() {
        return new CastStateListener() {
            @Override
            public void onCastStateChanged(int state) {
                if (state != CastState.NO_DEVICES_AVAILABLE && introductionOverlay != null) {
                    showIntroductionOverlay();
                }
            }
        };
    }

    private void showIntroductionOverlay() {
        if (introductionOverlay != null) introductionOverlay.show();
    }

    private SessionManagerListener<CastSession> createSessionManagerListener() {
        final Activity theActivity = activity.get();

        return new SessionManagerListener<CastSession>() {
            @Override
            public void onSessionStarted(CastSession castSession, String s) {
                if (theActivity == null) return;

                theActivity.invalidateOptionsMenu();
                onConnected(castSession);
            }

            @Override
            public void onSessionEnded(CastSession castSession, int i) {
                if (theActivity == null) return;

                theActivity.invalidateOptionsMenu();
                onDisconnected();
            }

            @Override
            public void onSessionResumed(CastSession castSession, boolean b) {
                if (theActivity == null) return;

                theActivity.invalidateOptionsMenu();
                onConnected(castSession);
            }

            @Override
            public void onSessionStarting(CastSession castSession) {
                //no-op
            }

            @Override
            public void onSessionStartFailed(CastSession castSession, int i) {
                //no-op
            }

            @Override
            public void onSessionEnding(CastSession castSession) {
                //no-op
            }

            @Override
            public void onSessionResuming(CastSession castSession, String s) {
                //no-op
            }

            @Override
            public void onSessionResumeFailed(CastSession castSession, int i) {
                //no-op
            }

            @Override
            public void onSessionSuspended(CastSession castSession, int i) {
                //no-op
            }
        };
    }

    private void onConnected(CastSession castSession) {
        this.castSession = castSession;

        casterPlayer.setRemoteMediaClient(castSession.getRemoteMediaClient());

        if (onConnectChangeListener != null) onConnectChangeListener.onConnected();
        if (onCastSessionUpdatedListener != null) onCastSessionUpdatedListener.onCastSessionUpdated(castSession);
        if (onCastSessionProgressUpdateListener != null) castSession.getRemoteMediaClient().addProgressListener(progressListener, 1000);
        if (onCastSessionStateChanged != null) castSession.getRemoteMediaClient().registerCallback(mediaListener);
    }

    private void onDisconnected() {
        if (onCastSessionProgressUpdateListener != null) {
            try {
                this.castSession.getRemoteMediaClient().removeProgressListener(progressListener);
            } catch (Exception ignored){}
        }

        if (onCastSessionStateChanged != null) {
            try {
                this.castSession.getRemoteMediaClient().unregisterCallback(mediaListener);
            } catch (Exception ignored){}
        }

        this.castSession = null;
        if (onConnectChangeListener != null) onConnectChangeListener.onDisconnected();
        if (onCastSessionUpdatedListener != null) onCastSessionUpdatedListener.onCastSessionUpdated(null);
    }

    private Application.ActivityLifecycleCallbacks createActivityCallbacks() {
        final Activity theActivity = activity.get();

        return new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                //no-op
            }

            @Override
            public void onActivityStarted(Activity activity) {
                //no-op
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (theActivity == null) return;

                if (theActivity == activity) {
                    handleCurrentCastSession();
                    registerSessionManagerListener();
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                if (theActivity == null) return;

                if (theActivity == activity) unregisterSessionManagerListener();
            }

            @Override
            public void onActivityStopped(Activity activity) {
                //no-op
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                //no-op
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                if (theActivity == null) return;

                if (theActivity == activity) {
                    activity.getApplication().unregisterActivityLifecycleCallbacks(this);
                }
            }
        };
    }

    private IntroductoryOverlay createIntroductionOverlay(MenuItem menuItem) {
        Activity theActivity = activity.get();
        if (theActivity == null) return null;

        return new IntroductoryOverlay.Builder(theActivity, menuItem)
                .setTitleText(R.string.caster_introduction_text)
                .setSingleTime()
                .build();
    }

    private void registerSessionManagerListener() {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        CastContext.getSharedInstance(theActivity).getSessionManager().addSessionManagerListener(sessionManagerListener, CastSession.class);
    }

    private void unregisterSessionManagerListener() {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        CastContext.getSharedInstance(theActivity).getSessionManager().removeSessionManagerListener(sessionManagerListener, CastSession.class);
    }

    private void handleCurrentCastSession() {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        CastSession newCastSession = CastContext.getSharedInstance(theActivity).getSessionManager().getCurrentCastSession();
        if (castSession == null) {
            if (newCastSession != null) {
                onConnected(newCastSession);
            }
        } else {
            if (newCastSession == null) {
                onDisconnected();
            } else if (newCastSession != castSession) {
                onConnected(newCastSession);
            }
        }
    }

    @Override
    public void onMediaLoaded() {
        if (onCastSessionStateChanged != null) onCastSessionStateChanged.onCastSessionBegan();
        startExpandedControlsActivity();
    }

    private void startExpandedControlsActivity() {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        Intent intent = new Intent(theActivity, ExpandedControlsActivity.class);
        theActivity.startActivity(intent);
    }

    public interface OnConnectChangeListener {
        void onConnected();
        void onDisconnected();
    }

    public interface OnCastSessionUpdatedListener {
        void onCastSessionUpdated(CastSession castSession);
    }

    public interface OnCastSessionProgressUpdateListener {
        void onProgressUpdated(long progressMs, long durationMs);
    }

    public interface OnCastSessionStateChanged {
        void onCastSessionBegan();
        void onCastSessionFinished();
        void onCastSessionPlaying();
        void onCastSessionPaused();
    }
}
