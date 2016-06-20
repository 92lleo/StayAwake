package io.kuenzler.android.stayawake;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!isModuleActive()) {
            xposedAlert();
        } else {
            Toast.makeText(this, "[Debug] Xposed module is active", Toast.LENGTH_SHORT).show();
        }
        Log.i("StayAwake", String.valueOf(isModuleActive()));

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
                .show();
    }

    public boolean isModuleActive() {
        return false;
    }
}
