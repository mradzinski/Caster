package com.mradzinski.caster;

import android.view.Menu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.mediarouter.app.MediaRouteButton;

class CasterNoOp extends Caster {
    private CasterPlayer casterPlayer;

    CasterNoOp() {
        casterPlayer = new CasterPlayerNoOp();
    }

    @Override
    public CasterPlayer getPlayer() {
        return casterPlayer;
    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public void addMediaRouteMenuItem(@NonNull Menu menu, Boolean withIntroductionOverlay) {
        //no-op
    }

    @Override
    public void setupMediaRouteButton(@NonNull MediaRouteButton mediaRouteButton, Boolean withIntroduction) {
        //no-op
    }

    @Override
    public void addMiniController() {
        //no-op
    }

    @Override
    public void addMiniController(int miniControllerLayout) {
        //no-op
    }

    @Override
    public void setOnConnectChangeListener(@Nullable OnConnectChangeListener onConnectChangeListener) {
        //no-op
    }

    @Override
    public void setOnCastSessionUpdatedListener(@Nullable OnCastSessionUpdatedListener onCastSessionUpdatedListener) {
        //no-op
    }
}
