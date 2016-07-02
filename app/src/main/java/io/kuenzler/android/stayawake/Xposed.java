package io.kuenzler.android.stayawake;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;

public class Xposed implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public static final int VOL_DOWN = KeyEvent.KEYCODE_VOLUME_DOWN;
    public static final int VOL_UP = KeyEvent.KEYCODE_VOLUME_UP;
    public static final int HOME = KeyEvent.KEYCODE_HOME; // not working
    public static final int BACK = KeyEvent.KEYCODE_BACK;
    public static final int MENU = KeyEvent.KEYCODE_MENU;
    public static final int CAMERA = KeyEvent.KEYCODE_CAMERA;
    public static final int POWER = KeyEvent.KEYCODE_POWER; // not working
    public static final int RECENT = KeyEvent.KEYCODE_APP_SWITCH; //not working
    public static final int SEARCH = KeyEvent.KEYCODE_SEARCH;

    public static final int LONG_PRESS_TIMEOUT = 1100; //ViewConfiguration.getLongPressTimeout();
    public static final int SHORT_PRESS_TIMEOUT = 210;

    public static final int TYPE_SYSTEM = 0;
    public static final int TYPE_APP = 1;

    public static final int METHOD_LONG_PRESS = 0;
    public static final int METHOD_TWO_KEYS = 1;
    public static final int METHOD_TOUCH = 4;
    public static final int METHOD_TEST = 2;
    public static final int METHOD_TIMETEST = 3;
    public static final int METHOD_THREE_KEYS = 5;

    private static Activity currentActivity;
    private static boolean flagKeepScreenOn, systemwideScreenOn, isTouch;

    private static String applicationLabel;

    //private static XSharedPreferences pref;

    //private static boolean active = true;
    private static boolean[] activeKeyPressed = new boolean[3];
    private static int[] activeKeys = new int[3];
    private static long[] lastKeyDown = new long[3];

    private static long lastDown = 0L;
    private static long lastUp = 0L;

    private static int currentMethode = -1;
    private static long lastUpdate = 0L;

    private static boolean debug = false;

    private static HashMap<String, View.OnKeyListener> listeners;

    @Override
    public void initZygote(StartupParam param) throws Throwable {
        //set current activity for later reference
        findAndHookMethod(android.app.Instrumentation.class, "newActivity", ClassLoader.class, String.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                currentActivity = (Activity) param.getResult();
                systemwideScreenOn = false;
                flagKeepScreenOn = false;
                //pref = new XSharedPreferences("io.kuenzler.android.stayawake", "user_settings");
                currentMethode = METHOD_TWO_KEYS; //change to prefs in next release
                readPrefs();
            }
        });

        //reload preferences at app resume
        findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                //readPrefs();
                setApplicationLabel();
                if (systemwideScreenOn) {
                    //   setFlagKeepScreenOn(systemwideScreenOn, TYPE_APP);
                }
                if (debug) {
                    showToast("on resume, systemwide: " + String.valueOf(systemwideScreenOn) + ", app: " + String.valueOf(flagKeepScreenOn));
                }
            }
        });

        //listen for KeyDown events
        findAndHookMethod(Activity.class, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // readPrefs();
                //check param and set keyCode
                if (!(param.args[0] instanceof Integer)) {
                    param.setResult(false);
                }
                int keyCode = (int) param.args[0];
                KeyEvent evt = (KeyEvent) param.args[1];
                XposedBridge.log("KeyDown detected - " + keyCode + " - " + applicationLabel);


                if (currentMethode == METHOD_LONG_PRESS) {
                    // long press stuff
                    for (int i = 0; i <= 2; i++) {
                        //save last key down time for long press
                        if (keyCode == activeKeys[i]) {
                            lastKeyDown[i] = System.currentTimeMillis();
                            evt.startTracking();
                        }
                    }

                } else if (currentMethode == METHOD_TWO_KEYS) {
                    // two key stuff
                    if (keyCode == activeKeys[1]) {
                        if (activeKeyPressed[2]) {
                            activeKeyPressed[2] = false;
                            setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            param.setResult(true);
                        } else {
                            activeKeyPressed[1] = true;
                            param.setResult(false);
                        }
                    } else if (keyCode == activeKeys[2]) {
                        if (activeKeyPressed[1]) {
                            activeKeyPressed[1] = false;
                            setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            param.setResult(true);
                        } else {
                            activeKeyPressed[2] = true;
                            param.setResult(false);
                        }
                    }
                } else if (currentMethode == METHOD_THREE_KEYS) {
                    // two key stuff
                    if (keyCode == activeKeys[0]) {
                        if (activeKeyPressed[1]) {
                            activeKeyPressed[1] = false;
                            setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            param.setResult(true);
                        } else if (activeKeyPressed[2]) {
                            activeKeyPressed[2] = false;
                            setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                            param.setResult(true);
                        } else {
                            activeKeyPressed[0] = true;
                            param.setResult(false);
                        }
                    } else if (keyCode == activeKeys[1]) {
                        if (activeKeyPressed[0]) {
                            activeKeyPressed[0] = false;
                            setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            param.setResult(true);
                        } else {
                            activeKeyPressed[1] = true;
                            param.setResult(false);
                        }
                    } else if (keyCode == activeKeys[2]) {
                        if (activeKeyPressed[0]) {
                            activeKeyPressed[0] = false;
                            setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                            param.setResult(true);
                        } else {
                            activeKeyPressed[2] = true;
                            param.setResult(false);
                        }
                    }
                } else if (currentMethode == METHOD_TOUCH) {
                    // two key stuff
                    if (keyCode == activeKeys[0]) {
                        activeKeyPressed[0] = true;
                        if (isTouch) {
                            param.setResult(true);
                        }
                    } else if (keyCode == activeKeys[1]) {
                        activeKeyPressed[1] = true;
                        if (isTouch) {
                            param.setResult(true);
                        }
                    } else if (keyCode == activeKeys[2]) {
                        activeKeyPressed[2] = true;
                        if (isTouch) {
                            param.setResult(true);
                        }
                    }

                } else if (currentMethode == METHOD_TEST) {
                    Toast.makeText(currentActivity, "test", Toast.LENGTH_SHORT).show();
                    // test stuff
                    if (keyCode == activeKeys[0]) {
                        //ignore
                    } else if (keyCode == activeKeys[1] && isTouch) {

                        setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                        param.setResult(true);

                    } else if (keyCode == activeKeys[2] && isTouch) {
                        setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                        param.setResult(true);
                    }
                } else if (currentMethode == METHOD_TIMETEST) {
                    // test stuff
                    long distance = 0L;
                    long current = System.currentTimeMillis();

                    if (keyCode == activeKeys[0]) {
                        //ignore
                    } else if (keyCode == activeKeys[1]) {
                        distance = current - lastUp;
                        lastDown = current;
                    } else if (keyCode == activeKeys[2]) {
                        distance = current - lastDown;
                        lastUp = current;
                    }
                    if (distance < 300) {
                        Toast.makeText(currentActivity, "distance: " + distance, Toast.LENGTH_SHORT).show();
                    }

                } else {
                    //wrong method
                    //throw new IllegalStateException("No method set");
                    //ignore for now
                }

                //debug output
                if (debug && keyCode == BACK && currentActivity.getPackageName().contains("io.kuenzler.android.stayawake")) {
                    //TODO: debug msg
                    param.setResult(false);
                    Toast.makeText(currentActivity, "back key, systemwide: " + String.valueOf(systemwideScreenOn) + ", app: " + String.valueOf(flagKeepScreenOn), Toast.LENGTH_SHORT).show();
                }
            }
        });


        //listen for KeyUp events
        findAndHookMethod(Activity.class, "onKeyUp", int.class, KeyEvent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                        if (!(param.args[0] instanceof Integer)) {
                            param.setResult(false);
                        }
                        int keyCode = (int) param.args[0];
                        XposedBridge.log("KeyUp detected - " + keyCode + " .. " + applicationLabel);

                        if (currentMethode == METHOD_LONG_PRESS) {
                            //long press stuff
                            if (keyCode == activeKeys[0] && lastKeyDown[0] != -1 && (System.currentTimeMillis() - lastKeyDown[0] > LONG_PRESS_TIMEOUT)) {
                                // not used
                            } else if (keyCode == activeKeys[1] && lastKeyDown[1] != -1 && (System.currentTimeMillis() - lastKeyDown[1] > LONG_PRESS_TIMEOUT)) {
                                lastKeyDown[1] = -1;
                                Toast.makeText(currentActivity, "woopwoop long key press", Toast.LENGTH_SHORT).show();
                                // setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            } else if (keyCode == activeKeys[2] && lastKeyDown[2] != -1 && (System.currentTimeMillis() - lastKeyDown[2] > LONG_PRESS_TIMEOUT)) {
                                lastKeyDown[1] = -1;
                                Toast.makeText(currentActivity, "woopwoop long key press", Toast.LENGTH_SHORT).show();
                                // setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                            }

                        } else if (currentMethode == METHOD_TWO_KEYS || currentMethode == METHOD_TOUCH || currentMethode == METHOD_THREE_KEYS) {
                            //two key stuff
                            for (int i = 0; i <= 2; i++) {
                                //"release" key
                                if (keyCode == activeKeys[i]) {
                                    activeKeyPressed[i] = false;
                                    param.setResult(true);
                                }
                            }
                        } else if (currentMethode == METHOD_TEST) {
                            //ignore
                        }
                    }
                }
        );
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
        if (type == TYPE_SYSTEM) {
            systemwideScreenOn = flagKeepScreenOn = keepScreenOn;
            if (keepScreenOn) {
                currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            setSystemwide(keepScreenOn);
            showToast("KEEP_SCREEN_ON is " + isFlagKeepScreenOn() + " systemwide");
        } else if (type == TYPE_APP) {
            flagKeepScreenOn = keepScreenOn;
            if (keepScreenOn) {
                currentActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                currentActivity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            if (flagKeepScreenOn) {
                showToast("[StayAwake enabled]\n" + applicationLabel + " will stay awake");
            } else {
                showToast("[StayAwake disabled]\n" + applicationLabel + " will use default screen timeout.");
            }
        } else {
            // should not happen
        }
        return isFlagKeepScreenOn();
    }


    /**
     * refresh preferences
     */
    private void readPrefs() {
        activeKeys[0] = BACK;
        activeKeys[1] = VOL_DOWN;
        activeKeys[2] = VOL_UP;

        //not used for now

        // if (pref.hasFileChanged()) {
        // pref.reload();
        // }

        //activeKeys[0] = pref.getInt("key1", BACK);
        //activeKeys[1] = pref.getInt("key2", VOL_DOWN);
        //activeKeys[2] = pref.getInt("key3", VOL_UP);

        //active = pref.getBoolean("enabled", true);
        //systemwideScreenOn = pref.getBoolean("systemwide", false);
        //lastUpdate = System.currentTimeMillis();
    }

    /**
     * Show given text as short toast
     *
     * @param message Message to show
     */
    private void showToast(String message) {
        Toast toast = Toast.makeText(currentActivity, message, Toast.LENGTH_SHORT);
        TextView v = (TextView) toast.getView().findViewById(android.R.id.message);
        if (v != null) v.setGravity(Gravity.CENTER);
        XposedBridge.log("Showing Toast: " + message);
        toast.show();
    }

    /**
     * Set application label.
     * Set application label, packagename if not available, "App" if nothing available
     */
    private void setApplicationLabel() {
        PackageManager pm;
        ApplicationInfo ai;
        try {
            pm = currentActivity.getPackageManager();
            ai = pm.getApplicationInfo(currentActivity.getPackageName(), 0);
            applicationLabel = (String) (ai != null ? pm.getApplicationLabel(ai) : currentActivity.getPackageName());
        } catch (PackageManager.NameNotFoundException | NullPointerException e) {
            XposedBridge.log(e);
            applicationLabel = currentActivity.getPackageName();
        }
        if (applicationLabel == null) {
            applicationLabel = "App";
        }
    }


    /**
     * Send intent to Stayawake PrefManager to set systemwide screen-on preference
     *
     * @param systemwide true: set sytemwide true, false: set it false
     */
    private void setSystemwide(boolean systemwide) {
        //not used for now
        //Intent toggle_system = new Intent("stayawake.intent.action.TOGGLE_SYSTEM");
        //toggle_system.putExtra("systemwide", systemwide);
        //currentActivity.sendBroadcast(toggle_system);
    }

    /**
     * Tell if screen is curerntly on
     *
     * @return true if screen is on, false if off
     */
    private boolean isScreenOn() {
        PowerManager powerManager = (PowerManager) currentActivity.getSystemService(Activity.POWER_SERVICE);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH && powerManager.isInteractive() || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH && powerManager.isScreenOn();
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