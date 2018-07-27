package com.mradzinski.caster;

import android.view.Menu;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity;

/**
 * Fullscreen media controls
 */
public class ExpandedControlsActivity extends ExpandedControllerActivity {

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.caster_discovery, menu);
        CastButtonFactory.setUpMediaRouteButton(this, menu, R.id.caster_media_route_menu_item);
        return true;
    }
}
