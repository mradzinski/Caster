package com.mradzinski.casterexample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.MediaRouteButton;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.Button;

import com.mradzinski.caster.Caster;
import com.mradzinski.caster.MediaData;

public class MainActivity extends AppCompatActivity {
    private static final String VIMEO_URL = "Your M3U URL goes here :)";

    private Button playButton;
    private Button resumeButton;
    private Caster caster;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        caster = Caster.create(this).withMiniController();
        setUpPlayButton();
        setUpMediaRouteButton();
    }

    private void setUpPlayButton() {
        playButton = findViewById(R.id.button_play);
        resumeButton = findViewById(R.id.button_resume);

        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                caster.getPlayer().loadMediaAndPlay(createSampleMediaData());
            }
        });

        resumeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (caster.getPlayer().isPaused()) {
                    caster.getPlayer().togglePlayPause();
                }
            }
        });

        caster.setOnConnectChangeListener(new Caster.OnConnectChangeListener() {
            @Override
            public void onConnected() {
                playButton.setEnabled(true);
            }

            @Override
            public void onDisconnected() {
                playButton.setEnabled(false);
                resumeButton.setEnabled(false);
            }
        });

        caster.setOnCastSessionStateChanged(new Caster.OnCastSessionStateChanged() {
            @Override
            public void onCastSessionBegan() {
                playButton.setEnabled(false);
                resumeButton.setEnabled(false);
                Log.e("Caster", "Began playing video");
            }

            @Override
            public void onCastSessionFinished() {
                playButton.setEnabled(true);
                resumeButton.setEnabled(false);
                Log.e("Caster", "Finished playing video");
            }

            @Override
            public void onCastSessionPlaying() {
                String playingURL = caster.getPlayer().getCurrentPlayingMediaUrl();

                if (playingURL != null && playingURL.equals(VIMEO_URL)) {
                    playButton.setEnabled(false);
                } else {
                    playButton.setEnabled(true);
                }

                resumeButton.setEnabled(false);
                Log.e("Caster", "Playing video");
            }

            @Override
            public void onCastSessionPaused() {
                playButton.setEnabled(false);
                resumeButton.setEnabled(true);
                Log.e("Caster", "Paused video");
            }
        });
    }

    private void setUpMediaRouteButton() {
        MediaRouteButton mediaRouteButton = findViewById(R.id.media_route_button);
        caster.setUpMediaRouteButton(mediaRouteButton);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        caster.addMediaRouteMenuItem(menu);
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    private static MediaData createSampleMediaData() {
        return new MediaData.Builder(VIMEO_URL)
                .setStreamType(MediaData.STREAM_TYPE_BUFFERED)
                .setContentType("application/x-mpegURL")
                .setMediaType(MediaData.MEDIA_TYPE_MOVIE)
                .setTitle("two birds, many stones.")
                .setDescription("Isaac searches for Rebekah to retrieve Arachnid's stolen XP.")
                .setThumbnailUrl("https://dg8ynglluh5ez.cloudfront.net/151/1517168873360394134/square_thumbnail.jpg")
                .setPlaybackRate(MediaData.PLAYBACK_RATE_NORMAL)
                .setAutoPlay(true)
                .build();
    }
}
