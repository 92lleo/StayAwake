package io.kuenzler.android.stayawake;

import android.app.Activity;
import android.content.Intent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.Toast;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Xposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public static final int VOL_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN;
    public static final int VOL_UP = KeyEvent.KEYCODE_VOLUME_UP;
    public static final int HOME = KeyEvent.KEYCODE_HOME;
    public static final int BACK = KeyEvent.KEYCODE_BACK;
    public static final int MENU = KeyEvent.KEYCODE_MENU;
    public static final int CAMERA = KeyEvent.KEYCODE_CAMERA;
    public static final int POWER = KeyEvent.KEYCODE_POWER;

    public static final int SYSTEM = 0;
    public static final int APP = 1;

    private static Activity currentActivity;
    private static boolean flagKeepScreenOn, systemwideScreenOn, isTouch;

    private static XSharedPreferences pref;

    //private static boolean active = true;
    private static boolean[] activeKeyPressed = new boolean[3];
    private static int[] activeKeys = new int[3];

    private static long lastUpdate = 0L;


    @Override
    public void initZygote(StartupParam param) throws Throwable {

        //set current activity for later reference
        findAndHookMethod(android.app.Instrumentation.class, "newActivity", ClassLoader.class, String.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                currentActivity = (Activity) param.getResult();
                pref = new XSharedPreferences("io.kuenzler.android.stayawake", "user_settings");
                readPrefs();
            }
        });

        //reload preferences at app resume
        findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                readPrefs();
                if (systemwideScreenOn) {
                    setFlagKeepScreenOn(systemwideScreenOn, APP);
                }
                Toast.makeText(currentActivity, "on resume, systemwide: " + String.valueOf(systemwideScreenOn) + ", app: " + String.valueOf(flagKeepScreenOn), Toast.LENGTH_SHORT).show();
            }
        });

        //check wheter the user is touching or not
        findAndHookMethod(Activity.class, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                readPrefs();
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

        //listen for KeyDown events
        findAndHookMethod(Activity.class, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                readPrefs();
                XposedBridge.log("KeyDown detected - " + currentActivity.getPackageName());

                //check param and set keyCode
                if (!(param.args[0] instanceof Integer)) {
                    param.setResult(false);
                }
                int keyCode = (int) param.args[0];
                if (keyCode == activeKeys[0]) {
                    if (activeKeyPressed[1]) {
                        activeKeyPressed[1] = false;
                        setFlagKeepScreenOn(!flagKeepScreenOn, APP);
                        param.setResult(true);
                    } else if (activeKeyPressed[2]) {
                        activeKeyPressed[2] = false;
                        setFlagKeepScreenOn(!systemwideScreenOn, SYSTEM);
                        param.setResult(true);
                    } else {
                        activeKeyPressed[0] = true;
                        param.setResult(false);
                    }
                } else if (keyCode == activeKeys[1]) {
                    if (activeKeyPressed[0]) {
                        activeKeyPressed[0] = false;
                        setFlagKeepScreenOn(!flagKeepScreenOn, APP);
                        param.setResult(true);
                    } else {
                        activeKeyPressed[1] = true;
                        param.setResult(false);
                    }
                } else if (keyCode == activeKeys[2]) {
                    if (activeKeyPressed[0]) {
                        activeKeyPressed[0] = false;
                        setFlagKeepScreenOn(!systemwideScreenOn, SYSTEM);
                        param.setResult(true);
                    } else {
                        activeKeyPressed[2] = true;
                        param.setResult(false);
                    }
                } else if (keyCode == BACK && currentActivity.getPackageName().contains("io.kuenzler.android.stayawake")) {
                    //TODO: debug msg
                    param.setResult(false);
                    Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        //listen for KeyUp events
        findAndHookMethod(Activity.class, "onKeyUp", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                readPrefs();
                XposedBridge.log("KeyUp detected - " + currentActivity.getPackageName());
                if (!(param.args[0] instanceof Integer)) {
                    param.setResult(false);
                }
                int keyCode = (int) param.args[0];
                if (keyCode == activeKeys[0]) {
                    activeKeyPressed[0] = true;
                } else if (keyCode == activeKeys[1]) {
                    activeKeyPressed[1] = true;
                } else if (keyCode == activeKeys[2]) {
                    activeKeyPressed[2] = false;
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
    private boolean setFlagKeepScreenOn(boolean keepScreenOn, int type) {
        if (type == SYSTEM) {
            systemwideScreenOn = flagKeepScreenOn = keepScreenOn;
            if (keepScreenOn) {
                currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            setSystemwide(keepScreenOn);
            Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn() + " systemwide", Toast.LENGTH_SHORT).show();
            return isFlagKeepScreenOn();
        } else if (type == APP) {
            flagKeepScreenOn = keepScreenOn;
            if (keepScreenOn) {
                currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            //TODO: debug msg
            Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn() + " for this app", Toast.LENGTH_SHORT).show();
            return isFlagKeepScreenOn();
        } else {
            // should not happen
            return isFlagKeepScreenOn();
        }
    }


    /**
     * refresh preferences
     */
    private void readPrefs() {
        // if (pref.hasFileChanged()) {
        pref.reload();
        // }
        if (!(System.currentTimeMillis() - lastUpdate > 2000)) {
            return;
        }
        activeKeys[0] = pref.getInt("key1", BACK);
        activeKeys[1] = pref.getInt("key2", VOL_DOWN);
        activeKeys[2] = pref.getInt("key3", VOL_UP);
        //active = pref.getBoolean("enabled", true);
        systemwideScreenOn = pref.getBoolean("systemwide", false);
        lastUpdate = System.currentTimeMillis();
    }

    /**
     * Send intent to Stayawake PrefManager to set systemwide screen-on preference
     *
     * @param systemwide true: set sytemwide true, false: set it false
     */
    private void setSystemwide(boolean systemwide) {
        Intent toggle_system = new Intent("stayawake.intent.action.TOGGLE_SYSTEM");
        toggle_system.putExtra("systemwide", systemwide);
        currentActivity.sendBroadcast(toggle_system);
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