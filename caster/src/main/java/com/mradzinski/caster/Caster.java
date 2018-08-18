package com.mradzinski.caster;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.LayoutRes;
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
import com.google.android.gms.cast.LaunchOptions;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Core class of Caster. It manages buttons/widgets and gives access to the media player.
 */
public class Caster implements CasterPlayer.OnMediaLoadedListener {
    private final static String TAG = "Caster";

    static String receiverId = CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID;

    protected static CastOptions customCastOptions;
    protected static LaunchOptions customLaunchOptions;
    protected static ExpandedControlsStyle expandedControlsStyle;

    private SessionManagerListener<CastSession> sessionManagerListener;
    private OnConnectChangeListener onConnectChangeListener;
    private OnCastSessionUpdatedListener onCastSessionUpdatedListener;
    private OnCastSessionStateChanged onCastSessionStateChanged;
    private OnCastSessionProgressUpdateListener onCastSessionProgressUpdateListener;

    private CastSession castSession;
    private CasterPlayer casterPlayer;
    private WeakReference<Activity> activity;
    private List<IntroductoryOverlay> introductionOverlays = new ArrayList<>();

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
     * Set the custom LaunchOptions. This is a shorthand for setting the full CastOptions object and
     * will be overriden by them if configured.
     *
     * @param launchOptions the custom LaunchOptions object.
     */
    public static void configure(@NonNull LaunchOptions launchOptions) {
        Caster.customLaunchOptions = launchOptions;
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
     * Gives access to {@link CastSession}, which allows to controll and get info of the current casting session
     *
     * @return the instance of {@link CastSession} handled by Caster or null if Chromecast is disconnected.
     */
    @Nullable  public CastSession getCastSession() {
        return castSession;
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
     * Adds the discovery menu item on a toolbar.
     * Should be used in {@link Activity#onCreateOptionsMenu(Menu)}. Optionally,
     * you can decide if an {@link IntroductoryOverlay} should be shown the first time the user
     * sees this button and a Chromecast device is discoverable.
     *
     * <p><b>Must be run on UiThread.</b></p>
     *
     * @param menu Menu in which MenuItem should be added
     * @param withIntroductionOverlay True if an {@link IntroductoryOverlay} should be
     *                                prepared and displayed for this button if needed. False otherwise.
     */
    @UiThread
    public void addMediaRouteMenuItem(@NonNull Menu menu, Boolean withIntroductionOverlay) {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        theActivity.getMenuInflater().inflate(R.menu.caster_discovery, menu);

        setUpMediaRouteMenuItem(menu);

        MenuItem menuItem = menu.findItem(R.id.caster_media_route_menu_item);

        if (withIntroductionOverlay) introductionOverlays.add(createIntroductionOverlay(menuItem));
    }

    /**
     * Makes a {@link MediaRouteButton} react to discovery events. You can have as many
     * {@link MediaRouteButton} as neededon your XML, but normally one is enough. Optionally,
     * you can decide if an {@link IntroductoryOverlay} should be shown the first time the user
     * sees this button and a Chromecast device is discoverable.
     *
     * <p><b>Must be run on UiThread.</b></p>
     *
     * @param mediaRouteButton Your MediaRouteButton view
     * @param withIntroductionOverlay True if an {@link IntroductoryOverlay} should be
     *                                prepared and displayed for this button if needed. False otherwise.
     */
    @UiThread
    public void setupMediaRouteButton(@NonNull MediaRouteButton mediaRouteButton, Boolean withIntroductionOverlay) {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        CastButtonFactory.setUpMediaRouteButton(theActivity, mediaRouteButton);

        if (withIntroductionOverlay) introductionOverlays.add(createIntroductionOverlay(mediaRouteButton));
    }

    /**
     * Adds the standard Mini Controller at the bottom of Activity's layout.
     * <b>Must be run on UiThread.</b>
     */
    public void addMiniController() {
        addMiniController(R.layout.mini_controller);
    }

    /**
     * Adds the Mini Controller at the bottom of Activity's layout.
     * <b>Must be run on UiThread.</b>
     *
     * @param miniControllerLayout A custom MiniController fragment layout.
     */
    @UiThread
    public void addMiniController(@LayoutRes int miniControllerLayout) {
        Activity theActivity = activity.get();
        if (theActivity == null) return;

        ViewGroup contentView = theActivity.findViewById(android.R.id.content);

        View rootView = contentView.getChildAt(0);

        LinearLayout linearLayout = new LinearLayout(theActivity);
        LinearLayout.LayoutParams linearLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);

        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setLayoutParams(linearLayoutParams);

        contentView.removeView(rootView);

        ViewGroup.LayoutParams oldRootParams = rootView.getLayoutParams();
        LinearLayout.LayoutParams rootParams = new LinearLayout.LayoutParams(oldRootParams.width, 0, 1f);

        rootView.setLayoutParams(rootParams);
        linearLayout.addView(rootView);

        theActivity.getLayoutInflater().inflate(miniControllerLayout, linearLayout, true);

        theActivity.setContentView(linearLayout);
    }

    /**
     * Sets the expaned controls style
     * @param style An instance of ExpandedControlsStyle class
     */
    public void setExpandedPlayerStyle(ExpandedControlsStyle style) {
        Caster.expandedControlsStyle = style;
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

    /**
     * Sets {@link OnCastSessionProgressUpdateListener}
     *
     * @param onCastSessionProgressUpdateListener An instance of {@link OnCastSessionProgressUpdateListener}
     */
    public void setOnCastSessionProgressUpdateListener(@Nullable OnCastSessionProgressUpdateListener onCastSessionProgressUpdateListener) {
        this.onCastSessionProgressUpdateListener = onCastSessionProgressUpdateListener;
    }

    /**
     * Sets {@link OnCastSessionStateChanged}
     *
     * @param onCastSessionStateChanged  An instance of {@link OnCastSessionStateChanged}
     */
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
                if (state != CastState.NO_DEVICES_AVAILABLE && introductionOverlays.size() > 0) {
                    showIntroductionOverlay();
                }
            }
        };
    }

    private void showIntroductionOverlay() {
        if (introductionOverlays.size() > 0) {
            for (IntroductoryOverlay io : introductionOverlays) {
                io.show();
            }
        }
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
        if (onCastSessionUpdatedListener != null) onCastSessionUpdatedListener.onCastSessionUpdated(castSession, true);
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

        if (onConnectChangeListener != null) onConnectChangeListener.onDisconnected();
        if (onCastSessionUpdatedListener != null) onCastSessionUpdatedListener.onCastSessionUpdated(castSession, false);

        this.castSession = null;
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

    private IntroductoryOverlay createIntroductionOverlay(MediaRouteButton button) {
        Activity theActivity = activity.get();
        if (theActivity == null) return null;

        return new IntroductoryOverlay.Builder(theActivity, button)
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
        void onCastSessionUpdated(@NonNull CastSession castSession, Boolean isConnected);
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
