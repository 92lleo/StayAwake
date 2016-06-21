package io.kuenzler.android.stayawake;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * Created by leonhard on 21.06.2016.
 */
@SuppressLint("WorldReadableFiles")
@SuppressWarnings("deprecation")
public class PrefManager extends BroadcastReceiver {

    public static final String TOGGLE_SYSTEM = "stayawake.intent.action.TOGGLE_SYSTEM";


    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("STAYAWAKE", "recieved");
        SharedPreferences pref = context.getSharedPreferences("user_settings", Context.MODE_WORLD_READABLE);
        pref.edit().putLong("time", System.currentTimeMillis());

        String action = intent.getAction();

        Bundle extras = intent.getExtras();

        if (TOGGLE_SYSTEM.equals(action)) {
            Log.i("STAYAWAKE", "togglesytem");
            boolean systemwide;
            if (!extras.isEmpty() && extras.containsKey("systemwide")) {
                systemwide = extras.getBoolean("systemwide");
                Log.i("STAYAWAKE", "go!");
            } else {
                systemwide = false;
                //read prefs
            }
            pref.edit().putBoolean("systemwide", systemwide).commit();
        }
    }
}