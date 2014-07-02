##What's Countly?
[Countly](http://count.ly) is an innovative, real-time, open source mobile analytics application. 
It collects data from mobile devices, and visualizes this information to analyze mobile application 
usage and end-user behavior. There are two parts of Countly: the server that collects and analyzes data, 
and mobile SDK that sends this data. Both parts are open source with different licensing terms.

This repository includes the SDK for Android.

##Why use this fork instead of the official one?

At my company we started using Countly. We noticed that on Android, some of the aggregate times displayed
in the dashboard were negative (see bug report [here](http://support.count.ly/discussions/problems/1691-time-spent-and-avg-time-spent-overflow)),
so I started investigating how to fix this issue. Upon perusal of the Android Countly source code, I
identified several bugs and possibilities for data loss and/or corruption, so I forked it and fixed all
of those issues (see this [commit](https://github.com/jboehle/countly-sdk-android/commit/93e0858fe8e3b453ad67c584f1d6a42bbf52ebb4)).
On top of that, I wrote complete unit tests for the entire SDK.

##Installing Android SDK

Installing Android SDK requires two very easy steps.

###1. Add Countly SDK to your project

Download [Latest JAR](https://github.com/jboehle/countly-sdk-android/releases/latest) and put it into your lib folder.

###2. Set up SDK

* Call `Countly.sharedInstance().init(context, "https://YOUR_SERVER", "YOUR_APP_KEY", "UNIQUE_DEVICE_ID")` in onCreate, which requires your App key and the URL of your Countly server (use `https://cloud.count.ly` for Countly Cloud).
* Call `Countly.sharedInstance().onStart()` in onStart.
* Call `Countly.sharedInstance().onStop()` in onStop.

Additionally, make sure that *INTERNET* permission is set if there's none in your manifest file.

If your app does not already have the concept of a unique device ID or app installation ID, you can use [OpenUDID](https://github.com/vieux/OpenUDID).
Please don't use ANDROID_ID or one of the other unreliable unique IDs provided by Android itself.
For more info see [this](http://android-developers.blogspot.com/2011/03/identifying-app-installations.html) and [this](http://stackoverflow.com/questions/2785485/is-there-a-unique-android-device-id).

**Note:** Make sure you use App Key (found under Management -> Applications) and not API Key. Entering API Key will not work. 

**Note:** Call init only once during onCreate of main activity. After that, for each onStart and onStop for 
each activity, call Countly onStart and onStop. 

###4. Other

Check Countly Server source code here: 

- [Countly Server (countly-server)](https://github.com/jboehle/countly-server)

There are also other Countly SDK repositories below:

- [Countly iOS SDK](https://github.com/Countly/countly-sdk-ios)
- [Countly Android SDK](https://github.com/jboehle/countly-sdk-android)
- [Countly Windows Phone SDK](https://github.com/Countly/countly-sdk-windows-phone)
- [Countly Blackberry Webworks SDK](https://github.com/Countly/countly-sdk-blackberry-webworks)
- [Countly Blackberry Cascades SDK](https://github.com/craigmj/countly-sdk-blackberry10-cascades) (Community supported)
- [Countly Mac OS X SDK](https://github.com/mrballoon/countly-sdk-osx) (Community supported)
- [Countly Appcelerator Titanium SDK](https://github.com/euforic/Titanium-Count.ly) (Community supported)
- [Countly Unity3D SDK](https://github.com/Countly/countly-sdk-unity) (Community supported)

##How can I help you with your efforts?
Glad you asked. We need ideas, feedbacks and constructive comments. All your suggestions will be taken care with upmost importance. 

Countly is on [Twitter](http://twitter.com/gocountly) and [Facebook](http://www.facebook.com/Countly) if you would like to keep up with their fast progress!

For community support page, see [http://support.count.ly](http://support.count.ly "Countly Support").
