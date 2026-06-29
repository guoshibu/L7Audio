package com.aug32.l7audio;

import android.app.Application;

import com.aug32.l7audio.domain.audio.AudioServiceLocator;

public class L7AudioApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AudioServiceLocator.getInstance().init(this);
    }
}
