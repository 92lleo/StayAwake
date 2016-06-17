package io.kuenzler.android.stayawake;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Xposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    private static Activity currentActivity;
    private static boolean flagKeepScreenOn, isTouch;

    private static boolean upPressed = false;
    private static boolean downPressed = false;

    @Override
    public void initZygote(StartupParam param) throws Throwable {

        //set current activity for later reference
        findAndHookMethod(android.app.Instrumentation.class, "newActivity", ClassLoader.class, String.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                currentActivity = (Activity) param.getResult();
            }
        });

        //check wheter the user is touching or not
        findAndHookMethod(Activity.class, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if (!(param.args[0] instanceof MotionEvent)) {
                    param.setResult(true);
                    return;
                }

                int eventAction = ((MotionEvent) param.args[0]).getAction();
                switch (eventAction) {
                    case MotionEvent.ACTION_DOWN:
                        isTouch = true;
                        break;
                    case MotionEvent.ACTION_UP:
                        isTouch = false;
                        break;
                }
                param.setResult(true);
            }
        });

        /*
        has unexpected behaviour
        //reset the flag on screen rotation -> onConfigurationChanged(Configuration config)
        findAndHookMethod(Activity.class, "onConfigurationChanged", Configuration.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                //screen was rotated, call again
                Toast.makeText(currentActivity, "rotation change, resetting keepScreenOn", Toast.LENGTH_SHORT).show();
                setFlagKeepScreenOn(flagKeepScreenOn);
            }
        });*/

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
                    if (upPressed) {
                        upPressed = false;
                        setFlagKeepScreenOn(!flagKeepScreenOn);
                        param.setResult(true);
                    } else {
                        downPressed = true;
                        param.setResult(false);
                    }
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (downPressed) {
                        downPressed = false;
                        setFlagKeepScreenOn(!flagKeepScreenOn);
                        param.setResult(true);
                    } else {
                        upPressed = true;
                        param.setResult(false);
                    }
                } else if (keyCode == KeyEvent.KEYCODE_BACK && currentActivity.getPackageName().contains("io.kuenzler.android.stayawake")) {
                    //TODO: debug msg
                    param.setResult(false);
                    Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn(), Toast.LENGTH_SHORT).show();
                }


            }
        });

        //listen for KeyDown events
        findAndHookMethod(Activity.class, "onKeyUp", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("KeyUp detected - " + currentActivity.getPackageName());

                if (!(param.args[0] instanceof Integer)) {
                    param.setResult(false);
                }
                int keyCode = (int) param.args[0];
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    downPressed = false;
                } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    upPressed = false;
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