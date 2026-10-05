package com.beetle.conference;

import android.app.Application;

import com.beetle.conference.room.RoomSdk;

/** Application entry point for process-wide conferencing SDK setup. */
public final class ConferenceApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        RoomSdk.initialize(this);
    }
}
