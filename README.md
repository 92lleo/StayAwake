# StayAwake (Android Xposed Module)
This module lets you keep the screen on for any app you want. just press volUp + volDown at the same time to toggle keepScreenOn. The app in foreground will stay awake until you press the power button or toggle again. You're also able to disable the screen timeout for all apps in the modules UI. 
In the next version you will be able to set different keys for the toggle action.

### Known From

The module is featured and explained on android gadget hacks on [android.wonderhowto.com](http://android.wonderhowto.com/how-to/keep-your-androids-screen-from-turning-off-automatically-per-app-basis-0171931/) and [on youtube](https://www.youtube.com/watch?v=Z4Lb4U64KTQ).

### How To Use

Watch android gadged hacks [video](https://www.youtube.com/watch?v=Z4Lb4U64KTQ) for a detailed tutorial.

### Target Devices

This module supports SDK 15 (4.0.3 ICS) and higher as well as XposedBrigde version 54 and higher


### Known Bugs
Please report bugs and feature requests on here or write me to android@kuenzler.io

 -  launcher icon hiding may not work properly on all devices. Clear app data if you face problems. 
 -  when systemwide screen on was selected, it will be disabled on reboot. But on some devices (such as HTC One m8) this isn't working

### How It Works

The module hooks into the current activity and hooks all key events there. If they match volume-up or volume-down, and both keys are pressed at the same time, it will toggle the flag WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

