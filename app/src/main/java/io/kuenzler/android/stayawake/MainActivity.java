package io.kuenzler.android.stayawake;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    CheckBox cbDebug;
    CheckBox cbPokemonGo;
    CheckBox cbIngress;
    CheckBox cbHideLauncher;
    CheckBox cbAll;

    SharedPreferences pref;

    boolean debug = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        pref = getSharedPreferences("user_settings", MODE_WORLD_READABLE);
        pref.edit().putLong("time", System.currentTimeMillis()).apply(); //for testing
        TextView textLink = (TextView) findViewById(R.id.tvLink);
        cbDebug = (CheckBox) findViewById(R.id.cbDebug);
        debug = pref.getBoolean("debug", false);
        cbDebug.setChecked(debug);
        cbPokemonGo = (CheckBox) findViewById(R.id.cbPokemonGo);
        cbPokemonGo.setChecked(pref.getBoolean("pokemonGo", false));
        cbIngress = (CheckBox) findViewById(R.id.cbIngress);
        cbIngress.setChecked(pref.getBoolean("ingress", false));
        cbHideLauncher = (CheckBox) findViewById(R.id.cbHideLauncherIcon);
        cbHideLauncher.setChecked(pref.getBoolean("hideLauncher", false));
        cbAll = (CheckBox) findViewById(R.id.cbAll);
        cbAll.setChecked(pref.getBoolean("systemwide", false));

        if (!isModuleActive()) {
            xposedAlert();
            TextView text2 = (TextView) findViewById(R.id.tv2);
            String message = "StayAwake is not active in Xposed Framework!\nThis can have more reasons:\n-Is the Xposed Framework installed?\n-Is the Xposed Installer App installed?\n" +
                    "-Is StayAwake activated in the Xposed Installer App?\n-Did you reboot after the activation?";
            text2.setText(message);
            cbPokemonGo.setEnabled(false);
            cbIngress.setEnabled(false);
        } else if (debug) {
            Toast.makeText(this, "[Debug] Xposed module is active", Toast.LENGTH_SHORT).show();
        }
        if (debug) {
            Log.i("StayAwake", String.valueOf(isModuleActive()));
        }
    }

    public void cbChanged(View view) {
        if (view instanceof CheckBox) {
            CheckBox currentCB = (CheckBox) view;
            boolean selected = currentCB.isChecked();
            String tag, preference;
            tag = (String) currentCB.getTag();
            if (tag == null) {
                tag = "";
            }
            switch (tag) {
                case "ingress":
                    preference = "ingress";
                    Toast.makeText(this, "StayAwake turned " + (selected ? "always on" : "off") + " in Ingress", Toast.LENGTH_SHORT).show();
                    break;
                case "pokemon":
                    preference = "pokemonGo";
                    Toast.makeText(this, "StayAwake turned " + (selected ? "always on" : "off") + " in PokémonGo", Toast.LENGTH_SHORT).show();
                    break;
                case "launcher":
                    preference = "hideLauncher";
                    PackageManager p = getPackageManager();
                    ComponentName componentName = new ComponentName(this, MainActivity.class);
                    int state = selected ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
                    p.setComponentEnabledSetting(componentName, state, PackageManager.DONT_KILL_APP);
                    Toast.makeText(this, "Icon hidden. Reboot may be required.", Toast.LENGTH_SHORT).show();
                    break;
                case "debug":
                    preference = "debug";
                    Toast.makeText(this, "Debug is turned " + (selected ? "on" : "off") + ".", Toast.LENGTH_SHORT).show();
                    break;
                case "all":
                    preference = "systemwide";
                    Toast.makeText(this, "StayAwake is turned " + (selected ? "on" : "off") + " systemwide.", Toast.LENGTH_SHORT).show();
                    break;
                default:
                    preference = null;
            }
            if (preference != null) {
                pref.edit().putBoolean(preference, selected).commit();
                if (debug) {
                    Toast.makeText(this, "Putting " + preference + " as " + selected, Toast.LENGTH_SHORT).show();
                }
            }
        }
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
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    public boolean isModuleActive() {
        return false;
    }
}
