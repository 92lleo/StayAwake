package io.kuenzler.android.stayawake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/**
 * Created by leonhard on 21.06.2016.
 */
public class PrefManager extends BroadcastReceiver {

    public static final String TOGGLE_SYSTEM = "stayawake.intent.action.TOGGLE_SYSTEM";
    public static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";


    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences("user_settings", Context.MODE_WORLD_READABLE);
        pref.edit().putLong("time", System.currentTimeMillis());

        String action = intent.getAction();
        Bundle extras = intent.getExtras();

        if (TOGGLE_SYSTEM.equals(action)) {
            boolean systemwide;
            if (!extras.isEmpty() && extras.containsKey("systemwide")) {
                systemwide = extras.getBoolean("systemwide");
                Log.i("STAYAWAKE", "go!");
            } else {
                systemwide = false;
                //read prefs
            }
            pref.edit().putBoolean("systemwide", systemwide).commit();
        } else if (BOOT_COMPLETED.equals(action)) {
            //set systemwide disabled after reboot
            if (pref.getBoolean("systemwide", false)) {
                pref.edit().putBoolean("systemwide", false).commit();
            }

            XposedBridge.log("Boot: set systemwide to false in prefs");
        }
    }
}