package io.kuenzler.android.stayawake;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Xposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private final static int UP = 0;
    private final static int DOWN = 1;

    private static Activity currentActivity;
    private static boolean flagKeepScreenOn;


    @Override
    public void initZygote(StartupParam param) throws Throwable {

        //set current activity for later reference
        findAndHookMethod(android.app.Instrumentation.class, "newActivity", ClassLoader.class, String.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                currentActivity = (Activity) param.getResult();
            }
        });

        //reset the flag on screen rotation -> onConfigurationChanged(Configuration config)
        findAndHookMethod(Activity.class, "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                //screen was rotated, call again
                Toast.makeText(currentActivity, "rotation change, resetting keepScreenOn", Toast.LENGTH_SHORT).show();
                setFlagKeepScreenOn(flagKeepScreenOn);
            }
        });

        //listen for KeyDown events
        findAndHookMethod(Activity.class, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("KeyDown detected - " + currentActivity.getPackageName());

                //check param and set keyCode
                if (!(param.args[0] instanceof Integer)) {
                    param.setResult(false);
                }
                int keyCode = (int) param.args[0];

                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    //volume down: set flag FLAG_KEEP_SCREEN_ON
                    param.setResult(true);
                    XposedBridge.log("key down consumed, screen should stay on");
                    setFlagKeepScreenOn(true);
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    //volume up: clear flag FLAG_KEEP_SCREEN_ON
                    param.setResult(true);
                    XposedBridge.log("key up consumed, screen should stay off");
                    setFlagKeepScreenOn(false);
                } else if (keyCode == KeyEvent.KEYCODE_BACK && currentActivity.getPackageName().contains("io.kuenzler.android.stayawake")) {
                    //TODO: debug msg
                    param.setResult(true);
                    Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * Returns whether the FLAG_KEEP_SCREEN_ON flag is set in WindowManager
     *
     * @return true if set, false if not set
     */
    public boolean isFlagKeepScreenOn() {
        int flags, flag;
        flags = currentActivity.getWindow().getAttributes().flags;
        flag = flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        if (flag == 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Sets the FLAG_KEEP_SCREEN_ON flag if given parameter is true, clears otherwise
     *
     * @param keepScreenOn set flag if true, clear if false
     * @return true if flag is set, flase if not set
     */
    private boolean setFlagKeepScreenOn(boolean keepScreenOn) {
        flagKeepScreenOn = keepScreenOn;
        if (keepScreenOn) {
            currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        //TODO: debug msg
        Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn(), Toast.LENGTH_SHORT).show();
        return isFlagKeepScreenOn();
    }


    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        //check if module is enabled, used in ui
        if (lpparam.packageName.equals("io.kuenzler.android.stayawake")) {
            findAndHookMethod("io.kuenzler.android.stayawake.MainActivity", lpparam.classLoader, "isModuleActive", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    param.setResult(true);
                    XposedBridge.log("Xposed Module in \"StayAwake\" is enabled");
                }
            });
        }
    }
}