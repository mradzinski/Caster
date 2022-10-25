package com.mradzinski.caster;

import android.os.Bundle;
import android.view.Menu;

import androidx.annotation.CallSuper;
import androidx.annotation.IdRes;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Extensible {@link AppCompatActivity}, which helps with setting widgets
 */
public abstract class CasterActivity extends AppCompatActivity {
    protected Caster caster;

    @CallSuper
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        caster = Caster.create(this);
    }

    @CallSuper
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        if (findViewById(getMiniControllerLayoutId()) == null) {
            caster.addMiniController();
        }

        caster.addMediaRouteMenuItem(menu, true);

        return true;
    }

    public @IdRes int getMiniControllerLayoutId() {
        return R.id.caster_mini_controller;
    }
}
