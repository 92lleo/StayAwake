package io.kuenzler.android.stayawake;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Created by leonhard on 21.06.2016.
 */
public class PrefManager extends BroadcastReceiver {

    public static final String TOGGLE_SYSTEM = "stayawake.intent.action.TOGGLE_SYSTEM";
    public static final String BOOT_COMPLETED = "android.intent.action.BOOT_COMPLETED";
    public static final String QUICK_BOOT = "android.intent.action.QUICKBOOT_POWERON";


    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences pref = context.getSharedPreferences("user_settings", Context.MODE_WORLD_READABLE);
        //String action = intent.getAction();
        //Bundle extras = intent.getExtras();

        //if (TOGGLE_SYSTEM.equals(action)) {
        //    boolean systemwide;
        //    if (!extras.isEmpty() && extras.containsKey("systemwide")) {
        //        systemwide = extras.getBoolean("systemwide");
        //        Log.i("STAYAWAKE", "go!");
        //    } else {
        //        systemwide = false;
        //
        //    }
        //    pref.edit().putBoolean("systemwide", systemwide).commit();
        //} else if (BOOT_COMPLETED.equals(action) || QUICK_BOOT.equals(action)) {
        //set systemwide disabled after reboot
        //if (pref.getBoolean("systemwide", true)) {
        pref.edit().putBoolean("systemwide", false).commit();
        //}
        //Toast.makeText(context, "Boot: set systemwide to false in prefs", Toast.LENGTH_SHORT).show();
        //XposedBridge.log("Boot: set systemwide to false in prefs");
        //}
    }
}