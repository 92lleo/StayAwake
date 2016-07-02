package io.kuenzler.android.stayawake;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SharedPreferences pref = getSharedPreferences("user_settings", MODE_WORLD_READABLE);
        pref.edit().putLong("time", System.currentTimeMillis()).apply(); //for testing
        TextView textLink = (TextView) findViewById(R.id.tvLink);
        if (!isModuleActive()) {
            xposedAlert();
            TextView text2 = (TextView) findViewById(R.id.tv2);
            String message = "StayAwake is not active in Xposed Framework!\nThis can have more reasons:\n-Is the Xposed Framework installed?\n-Is the Xposed Installer App installed?\n" +
                    "-Is StayAwake activated in the Xposed Installer App?\n-Did you reboot after the activation?";
            text2.setText(message);
        } else {
            Toast.makeText(this, "[Debug] Xposed module is active", Toast.LENGTH_SHORT).show();
        }

        Log.i("StayAwake", String.valueOf(isModuleActive()));

    }

    public void showWebsite(View view) {
        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://www.kuenzler.io"));
        startActivity(browserIntent);
    }

    private void xposedAlert() {
        new AlertDialog.Builder(this)
                .setTitle("Xposed Error")
                .setMessage("This Xposed module is not activated or Xposed is not installed. " +
                        "You can't use this app without Xposed.\n\nDo you want to close it?")
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                })
                .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // do nothing
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                //.setKey
                .show();
    }

    public boolean isModuleActive() {
        return false;
    }
}
