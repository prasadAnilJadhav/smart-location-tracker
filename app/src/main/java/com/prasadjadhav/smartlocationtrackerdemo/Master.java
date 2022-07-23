package com.prasadjadhav.smartlocationtrackerdemo;

import android.app.Application;
import android.content.Context;
import android.widget.Toast;

import org.jetbrains.annotations.NotNull;

public class Master extends Application {
    static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

    public static void toast(@NotNull String msg) {
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }
}
