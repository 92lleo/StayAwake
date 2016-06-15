package io.kuenzler.android.stayawake;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("StayAwake", String.valueOf(isModuleActive()));
        Toast.makeText(this, String.valueOf(isModuleActive()), Toast.LENGTH_SHORT).show();
    }

    public boolean isModuleActive(){
        return false;
    }
}
