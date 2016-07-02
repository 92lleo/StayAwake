package io.kuenzler.android.stayawake;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Build;
import android.os.PowerManager;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.util.HashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookConstructor;
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

    private static XSharedPreferences pref;

    //private static boolean active = true;
    private static boolean[] activeKeyPressed = new boolean[3];
    private static int[] activeKeys = new int[3];
    private static long[] lastKeyDown = new long[3];

    private static long lastDown = 0L;
    private static long lastUp = 0L;

    private static int currentMethode = -1;
    private static long lastUpdate = 0L;

    private static HashMap<String, View.OnKeyListener> listeners;

    @Override
    public void initZygote(StartupParam param) throws Throwable {
        //set current activity for later reference
        findAndHookMethod(android.app.Instrumentation.class, "newActivity", ClassLoader.class, String.class, Intent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                currentActivity = (Activity) param.getResult();

                pref = new XSharedPreferences("io.kuenzler.android.stayawake", "user_settings");
                //currentMethode = METHOD_TWO_KEYS; //TODO: prefs

                readPrefs();
            }
        });


        XC_MethodHook viewHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                final View view = (View) param.thisObject;
                view.setOnKeyListener(new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (!event.isLongPress()) {
                            return false;
                        }
                        if (keyCode == activeKeys[1]) {
                            XposedBridge.log("key 1 on view " + view.toString());
                            setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                            return true;
                        } else if (keyCode == activeKeys[2]) {
                            XposedBridge.log("key 2 on view " + view.toString());
                            setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            return true;
                        }
                        return false;
                    }
                });
            }
        };
        findAndHookConstructor(View.class, Context.class, viewHook);
        findAndHookConstructor(View.class, Context.class, AttributeSet.class, viewHook);
        findAndHookConstructor(View.class, Context.class, AttributeSet.class, int.class, viewHook);


        findAndHookMethod(View.class, "onFocusChanged", boolean.class, int.class, Rect.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                final View view = (View) param.thisObject;

                View.OnKeyListener okl = new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (!event.isLongPress()) {
                            return false;
                        }
                        event.startTracking();
                        if (keyCode == activeKeys[1]) {
                            XposedBridge.log("key 1 on view " + view.toString());
                            setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                            return true;
                        } else if (keyCode == activeKeys[2]) {
                            XposedBridge.log("key 2 on view " + view.toString());
                            setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            return true;
                        }
                        return false;
                    }
                };

                boolean gainFocus = (boolean) param.args[0];

                if (gainFocus) {
                    view.setOnKeyListener(okl);
                } else {
                    view.setOnKeyListener(null);
                }
            }
        });

        //reload preferences at app resume
        findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                //readPrefs();
                if (systemwideScreenOn) {
                    //   setFlagKeepScreenOn(systemwideScreenOn, TYPE_APP);
                }
                showToast("on resume, systemwide: " + String.valueOf(systemwideScreenOn) + ", app: " + String.valueOf(flagKeepScreenOn));
            }
        });

        //check wheter the user is touching or not
        XC_MethodHook touchEvent = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                //readPrefs();
                if (!(param.args[0] instanceof MotionEvent)) {
                    param.setResult(true);
                    return;
                }

                int eventAction = ((MotionEvent) param.args[0]).getAction();
                switch (eventAction) {
                    case MotionEvent.ACTION_DOWN:
                        isTouch = true;
                        final long now = System.currentTimeMillis();
                        Toast.makeText(currentActivity, "touch " + "" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();

                        Runnable r = new Runnable() {
                            @Override
                            public void run() {
                                while (System.currentTimeMillis() - now < 180L && isTouch) {
                                    if (activeKeyPressed[1]) {
                                        Toast.makeText(currentActivity, "key 1" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                                        isTouch = false;
                                        //param.setResult(true);
                                    } else if (activeKeyPressed[2]) {
                                        Toast.makeText(currentActivity, "key 2" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                                        isTouch = false;
                                        //param.setResult(true);
                                    }
                                }
                            }
                        };
                        new Thread(r).start();

                        break;
                    case MotionEvent.ACTION_UP:
                        isTouch = false;
                        break;
                }
                param.setResult(true);
            }
        };
        //findAndHookMethod(Activity.class, "onTouchEvent", MotionEvent.class, touchEvent);
        // findAndHookMethod(View.class, "onTouchEvent", MotionEvent.class, touchEvent);


        //listen for KeyDown events
        findAndHookMethod(Activity.class, "onKeyDown", int.class, KeyEvent.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                // Toast.makeText(currentActivity, "woopwoop key down " + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                // readPrefs();
                //check param and set keyCode
                if (!(param.args[0] instanceof Integer)) {
                    param.setResult(false);
                }
                int keyCode = (int) param.args[0];
                KeyEvent evt = (KeyEvent) param.args[1];
                XposedBridge.log("KeyDown detected - " + currentActivity.getPackageName());


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
                    long now = System.currentTimeMillis();
                    boolean waiting = true;
                    if (keyCode == activeKeys[1]) {
                        XposedBridge.log("1 pressed");
                        if (activeKeyPressed[1]) {
                            return;
                        }
                        activeKeyPressed[1] = true;
                        if (activeKeyPressed[2]) {
                            Toast.makeText(currentActivity, "key 2 + 1 already" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();

                            XposedBridge.log("2 already pressed");
                            Toast.makeText(currentActivity, "key 1 + 2" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                            param.setResult(true);
                            return;
                        }
                        Toast.makeText(currentActivity, "key 1, waiting is " + String.valueOf(waiting) + ". " + (System.currentTimeMillis() - now) + " .. " + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                        while (waiting && (System.currentTimeMillis() - now < 120L) && activeKeyPressed[1]) {
                            XposedBridge.log("Going whiled (1 pressed, waiting for 2)");
                            if (activeKeyPressed[2]) {
                                XposedBridge.log("Going whiled (1 pressed, 2 pressed, too!!)");
                                Toast.makeText(currentActivity, "key 1 + 2" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                                waiting = false;
                                param.setResult(true);
                            }
                        }
                    } else if (keyCode == activeKeys[2]) {
                        XposedBridge.log("2 pressed");
                        if (activeKeyPressed[2]) {
                            return;
                        }
                        activeKeyPressed[2] = true;
                        if (activeKeyPressed[1]) {
                            Toast.makeText(currentActivity, "key 2 + 1 already " + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                            XposedBridge.log("1 already pressed");
                            param.setResult(true);
                            waiting = false;
                        }
                        Toast.makeText(currentActivity, "key 2, waiting is " + String.valueOf(waiting) + ". " + (System.currentTimeMillis() - now) + " .. " + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                        while (waiting && (System.currentTimeMillis() - now < 180L) && activeKeyPressed[2]) {
                            XposedBridge.log("Going whiled (2 pressed, 1 pressed, too!!)");
                            if (activeKeyPressed[1]) {
                                Toast.makeText(currentActivity, "key 2 + 1" + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                                waiting = false;
                                param.setResult(true);
                            }
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
                if (keyCode == BACK && currentActivity.getPackageName().contains("io.kuenzler.android.stayawake")) {
                    //TODO: debug msg
                    param.setResult(false);
                    Toast.makeText(currentActivity, "back key, systemwide: " + String.valueOf(systemwideScreenOn) + ", app: " + String.valueOf(flagKeepScreenOn), Toast.LENGTH_SHORT).show();
                }
            }


            protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                return true;
            }
        });


        //listen for KeyUp events
        findAndHookMethod(Activity.class, "onKeyUp", int.class, KeyEvent.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        //readPrefs();
                        //Toast.makeText(currentActivity, "woopwoop key up " + currentActivity.getPackageName(), Toast.LENGTH_SHORT).show();
                        if (!(param.args[0] instanceof Integer)) {
                            param.setResult(false);
                        }
                        int keyCode = (int) param.args[0];
                        XposedBridge.log("KeyUp detected - " + keyCode + " .. " + currentActivity.getPackageName());

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

        //listen for KeyUp events
        findAndHookMethod(Activity.class, "onKeyLongPress", int.class, KeyEvent.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        readPrefs();
                        XposedBridge.log("long press detected - " + currentActivity.getPackageName());
                        if (!(param.args[0] instanceof Integer)) {
                            param.setResult(false);
                        }
                        int keyCode = (int) param.args[0];

                        if (currentMethode == METHOD_LONG_PRESS) {
                            //long press stuff
                            if (keyCode == activeKeys[0]) {
                                // not used
                            } else if (keyCode == activeKeys[1]) {
                                lastKeyDown[1] = -1;
                                Toast.makeText(currentActivity, "woopwoop long key press (really)", Toast.LENGTH_SHORT).show();
                                // setFlagKeepScreenOn(!flagKeepScreenOn, TYPE_APP);
                            } else if (keyCode == activeKeys[2]) {
                                lastKeyDown[1] = -1;
                                Toast.makeText(currentActivity, "woopwoop long key pres (really)", Toast.LENGTH_SHORT).show();
                                // setFlagKeepScreenOn(!systemwideScreenOn, TYPE_SYSTEM);
                            }

                        } else if (currentMethode == METHOD_TWO_KEYS) {
                            //two key stuff
                            for (int i = 0; i <= 2; i++) {
                                //"release" key
                                if (keyCode == activeKeys[i]) {
                                    activeKeyPressed[i] = false;
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
            Toast.makeText(currentActivity, "KEEP_SCREEN_ON is " + isFlagKeepScreenOn() + " systemwide", Toast.LENGTH_SHORT).show();
            return isFlagKeepScreenOn();
        } else if (type == TYPE_APP) {
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
        // pref.reload();
        // }
        if (!(System.currentTimeMillis() - lastUpdate > 2000)) {
            // return;
        }
        //activeKeys[0] = pref.getInt("key1", BACK);
        //activeKeys[1] = pref.getInt("key2", VOL_DOWN);
        //activeKeys[2] = pref.getInt("key3", VOL_UP);
        activeKeys[0] = BACK;
        activeKeys[1] = VOL_DOWN;
        activeKeys[2] = VOL_UP;
        //active = pref.getBoolean("enabled", true);
        //systemwideScreenOn = pref.getBoolean("systemwide", false);
        lastUpdate = System.currentTimeMillis();
    }

    private void showToast(String message) {
        Toast.makeText(currentActivity, message, Toast.LENGTH_SHORT);
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