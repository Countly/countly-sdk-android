# Introduction
Countly SDK 18.X is a completely redesigned version of our Android SDK. Please use it with caution 
since it hasn't yet tested by thousands of developers in thousands of applications as 
our previous SDK versions.

## Highlights
Here's a list of notable changes and new features of Countly Android SDK 18.X:
* **Initialization** now must be done from `Application` subclass' `onCreate` method.
* **API** is substantially simplified (no `sharedInstance()` needed for example), thus changed.
We have lots of legacy methods marked with `@Deprecated` for easy migration. 
Also in most places method chaining is available.
* Newly introduced **Config** class has dozens of changeable properties. Pretty much
  any aspect of SDK functionality can be changed or overridden.
* **Activity lifecycle methods** (`onCreate`, `onStart`, `onStop`) are no longer required
 to be called if your application targets API levels 14+. Otherwise, you still need to 
 call them.
* **Data storage** is now based on flat files, not on `SharedPreferences`. This increased
 performance 1000x in some cases. More importantly, Countly SDK doesn't block 
 main thread for reading / storing data anymore.
* **More efficient networking.** Countly sends less requests with more data in each 
of them, thus increasing overall efficiency. Network requests are sent with exponential backoff 
in case of network or server is not available.
* **Programmatic session control.** Developer can now define what is a `Session`, when it
 starts and when it ends.
* **Test mode** is an SDK-wide option which ensures you don't call SDK API methods with
invalid data. In test mode Countly SDK will raise a `RuntimeException` whenever it's in
inconsistent state. If test mode is off, Countly ignores any inconsistencies: it ignores
 events with `NaN` as sum, ignores multiple calls to `Session` lifecycle methods, etc. Instead of
 raising `RuntimeException`, it just logs an error.
* **Logging** is also improved, so you can now manage log level from `DEBUG` to `OFF`.
* **Modules.** Apart from standard logic, SDK is split into 7 modules responsible for 
 each functional part of it: `ModuleSessions` manages automatic session tracking, `ModuleCrash` 
 detects and processes crashes, `ModuleAttribution` contains logic of attributing 
 application installation to a specific advertising campaign, etc. Each `Module` can 
 be replaced with your own implementation.
* **User** data is persistently stored and available for later use. Countly won't
send user data to the server if user data wasn't changed.
* **Push** functionality is now available in our base `sdk` library, no need for `sdk-messaging`. 
Countly SDK now supports only FCM, GCM support is only available in legacy SDK 17.09 and 
earlier. Standard notification handling logic is exposed in public class and can be easily overridden.
* **Crash Reporting** has been heavily refactored as well: crashes are sent to the
  server even if occur on app launch. Crashes are sent immediately after they happen,
  not on next app launch as previously. Countly SDK now detects ANRs as well.
* **Tests** and **Javadoc**. SDK is now fully documented and mostly covered with tests.


# Easy start
Process of setting up Countly SDK includes 2 simple steps: adding SDK as dependency to your Android Studio project
and initializing SDK. Once those are done, you'll have basic analytics on your server like users, sessions,
devices, etc.

## Adding dependency
At first you need to add Countly SDK Gradle dependency and sync your project:
```
dependencies {
		compile 'ly.count.android:sdk:18.X'
}
```

In case dependency is not found, make sure you have `jcenter` repository in your project `build.gradle` 
(this file is separate from your application `build.gradle`):
```
buildscript {
    repositories {
        jcenter()
    }
}
```

You can also add Bintray Maven repository:
```
buildscript {
    repositories {
        maven {
            url  "http://dl.bintray.com/countly/maven"
        }
    }
}
```
## Initializing SDK
To start Countly SDK ou need to create an `Application` subclass and put following code in its `onCreate`
 method:

```
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        Config config = new Config("http://YOUR.SERVER.COM", "YOUR_APP_KEY")
                .enableTestMode()
                .setLoggingLevel(Config.LoggingLevel.DEBUG)
                .setFeatures(Config.Feature.Crash)
                .setDeviceIdStrategy(Config.DeviceIdStrategy.OPEN_UDID);
        
        CountlyNeo.init(this, config);

    }
}
```

Then make sure you set your `Application` subclass correctly in `AndroidManifest.xml`:
```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="YOUR.APP.PACKAGE">

    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

    <application
        android:name=".App"
        ... other attributes ...>
        
        ... application components ...
    </application>
</manifest>
```

`.App` here is name of your `Application` subclass.

In our `Config` instance we:
 * Told SDK not to use `https` (note `http://` in url) and to send data to Countly server located at `http://YOUR.SERVER.COM`. We also
 specified app key (`YOUR_APP_KEY`).
 * Enabled test mode (read - crash whenever in inconsistent state, don't forget to disable it 
in Production!).
 * Set logging level to `DEBUG`
to make sure everything works as expected.
 * Enabled crash reporting feature and tell SDK to
use `OPEN_UDID` as device id. 
 * Use default session lifecycle management, that is start a `Session` when first `Activity` is started
and end that `Session` when last `Activity` is stopped. This is called automatic session control.

In case minimum API level supported by your application is more or equal to 14, then that's pretty much it.
Once you launch your application, it will start sending requests. In case you need to support API levels below 14,
you'll also need to call `onCreate`, `onStart` and `onStop` methods from all your activities.

## Record first event
Now lets record our first custom event:

```
        Button button = (Button) findViewById(R.id.makePurchase);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Countly.event(getApplicationContext(), "purchase-btn").setSum(10).record();
                // ... do the purchasing logic ...
            }
        });

```

Whenever Purchase button is pressed, Countly will record an event with a key `purchase-btn` with sum of `10`
 and will eventually send it to our Countly server with next request. You can also add segmentation, 
 count and duration to an event. See our javadoc EVENTLINKHERE.
 
 Now launch your app, open your Countly dashboard and make sure data is there. In case it's not, make sure
 you used correct server url and app key. If they are, check logs in `logcat`. 

# Architecture
Now let's have an overview of Countly SDK internals. 
## Components
Starting version 18.X Countly SDK supports 2 ways of operation:

* **Separate process (default) model.** SDK runs split in 2 parts. Backend part lives in a
separate process, performs network requests to Countly server and detects crashes
of your main app process. Frontend part runs along with your application and is being used by
 your application to record events, process push notifications, etc.

* **Single process model.** In this model SDK is not split and lives in your application
 process with lifecycle identical to your app.

Main benefit of separate process model is that sending requests process is no longer 
 bound to your application lifecycle. That's important in several cases like 
 network connectivity issues, application force-quit or crash. Previously a crash wasn't sent to 
 Countly server until next app launch. Some crashes weren't sent at all in case 
 they occur right on application launch.

Another important benefit is performance. Countly doesn't pollute your application 
memory stack with sometimes huge volume of unsent data. All I/O is now performed 
in background threads or in a separate process in case of network I/O. 
There's almost no synchronization or locking of any kind in our SDK anymore. 

When you add `sdk` gradle dependency, `service` element is automatically injected in your `AndroidManifest.xml`.
To switch from default 2-process model to single process model, just add `tools` namespace to your `AndroidManifest.xml` 
and override our service definition and remove `android:process` attribute from it:
```
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="... your package ..."
    xmlns:tools="http://schemas.android.com/tools">
    
    <application ...>
        ...
        <service android:name="ly.count.sdk.android.internal.CountlyService" tools:remove="android:process">
        </service>
    </application>
</manifest>
```

## Storage
Countly SDK stores serialized versions of following classes: `InternalConfig`, `SessionImpl`, `RequestImpl`, `CrashImpl` and `UserImpl`.
 All those are stored in device memory, in a separate files with filenames prefixed with `[CLY]_`.
 
## Modules
There is a set of classes implementing `Module` interface. They encapsulate logic of specific 
   parts of SDK:
* `ModuleAttribution` sets up a `BroadcastReceiver` which listens for Google Play `INSTALL_REFERRER` broadcasts.
It also acquires `AdvertisingId` with the help of `ModuleDeviceId` even if you don't use it as your device id generation
strategy to match against the one given by Google Play.
* `ModuleCrash` sets up `Thread.UncaughtExceptionHandler` which catches and records any fatal
crashes. It also maintains ANR detection `Thread` which pings your application main `Thread` and 
records ANR "crash" whenever main `Thread` doesn't respond for some time.
* `ModuleDeviceId` is responsible for acquiring all kinds of ids. It does that 
through reflection, so Countly SDK doesn't have any external dependencies anymore.
* `ModulePush` decodes incoming push notifications sent from Countly server through Firebase
Cloud Messaging.
* `ModuleRequests` is just a single place to construct and set up network requests.
* `ModuleSessions` has logic responsible for automatic session control: start a session
when first `Activity` is started, end it when last `Activity` is stopped.
* `ModuleViews` is responsible for automatic view recording, that is recording a view named after `Activity`
 class name starting it on `Activity` start and stoping it on `Activity` view.

# API
This page is not supposed to list all possible features and implementation
details of Countly SDK. Instead, we focus on how to perform some common tasks so you could save time for 
more important things. Full documentation is available as javadoc here: PASTETHELINKHERE. 
In case you need more understanding of Countly SDK internals, you should start form CONFIGLINKHERE 
as main entry point into configuration and features, following SESSIONLINKHERE
regarding `Session` lifecycle and control. USERLINKHERE explains user data, EVENTLINKHERE is 
about recording events. 

## Test mode
To ensure correct SDK behaviour, please use `Config.enableTestMode()` when you app is in development
and testing. In test mode Countly SDK raises `RuntimeException`s whenever is in inconsistent state. 
Once you remove `Config.enableTestMode()` call from your initialization sequence, SDK stops 
raising any `Exception`s and switches to logging errors instead (if logging wasn't specifically turned off). 
Without having test mode on during development you may encounter some important issues with data consistency
in production. 

## Device ID
Android SDK supports following strategies of device ID generation:
* `OPEN_UDID`. Default. Basically `ANDROID_ID` if it is available or a pseudo-random `String` if it's not.
* `ADVERTISING_ID`. ID is taken from `AdvertisingId` class.
* `INSTANCE_ID`. Firebase `InstanceId`.
* `CUSTOM_ID`. Developer-specified device ID `String`.

None of the strategies above is 100% persistent or 100% non-persistent, that is living across
multiple app installs. Some of them, in some cases, maintain ID between different app installs.

Strategy is set in `Config` class prior to `Countly.init()` with `setDeviceIdStrategy()` method.

### Authentication
With no special steps performed, SDK will count any new app install (see note above regarding device ID 
persistence) as new user. In some cases, like when you have some kind of authentication system 
in your app, that's not what you want. When actual person should be counted as one user in Countly
irrespective of number of app installs this user has and when you can provide user id to Countly SDK, 
you should call `Countly.login(String)` with your specific user ID `String`. 
After this method call reaches Countly server, it will:
 * take old user profile (and all the events happened prior to login), 
let's say with `OPEN_UDID`-based id;
 * take user profile with your new ID if any (user could already have a profile associated with 
 this new ID);
 * and will merge these profiles together. 

When user logs out from your application, you can:
* keep posting events under latest known authenticated user, meaning just do nothing;
* or call `Countly.logout()` so SDK could reset its ID to the strategy you use in `Config`, let's say
`OPEN_UDID`, and start a new `Session` for this user.

There is also third method `Countly.resetDeviceId(String)` which doesn't merge user profiles together
and only ends current `Session` with old id & instructs SDK to use new device id from that moment in time.

## Sessions
Session in Countly is a single app launch or several app launches if time between them is less 
than 30 seconds (by default). Of course you can override this behaviour, but lets describe how 
standard way works at first.

In Android SDK default way of detecting an app launch is monitoring number of active `Activity` 
instances. To make this happen our SDK registers `Application.ActivityLifecycleCallbacks` and 
just increments an integer whenever `onActivityStarted` is called and decrements it 
whenever `onActivityStopped` is called. The only catch here is that `Application.ActivityLifecycleCallbacks` 
is available only in API levels 14+, meaning in case your app supports API levels lower than 14,
you'll need to make your own `Activity` superclass and subclass all your activities from it. 
Also add `onActivityCreated` for the sake of other parts of SDK:
```
public class BaseActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Countly.onActivityCreated(this, savedInstanceState);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState, @Nullable PersistableBundle persistentState) {
        super.onCreate(savedInstanceState, persistentState);
        Countly.onActivityCreated(this, savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Countly.onActivityStarted(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        Countly.onActivityStopped(this);
    }
}
```

That was what we call automatic session control. It will work well for most of apps, but sometimes
applications have very different `Session` definitions:
* Apps which use GPS in background: turn-by-turn navigation, fitness trackers, etc.
* Music players.
* Utility apps.
* Etc.

For these apps Countly supports what we call programmatic session control. First, to disable automatic
session tracking, you need to call `Config.disableFeatures(Config.Feature.AutoSessionTracking)` 
at initialization phase. With this set up Countly won't start, update or end any sessions, 
it's your responsibility now. But there are two exceptions: 
* when device id changes (`Countly.login(String)`, `Countly.logout()`, `Countly.resetDeviceId(String)`), 
current session will be ended;
* when application crashes or when it exits abruptly, unfinished `Session` will be
ended automatically on next app launch or within several seconds from a crash (in 2-process mode).

There are only 2 `Session`-related API methods:
* `Countly.session(Context)` always returns a `Session` instance: new one if no session was created before,
 or already existing one if there is an active session.
* `Countly.getSession()` returns active `Session` instance or `null` if there is no active session.

`Session` lifecycle methods include:
* `session.begin()` must be called when you want to send begin session request to the server. Called with 
first `Activity.onStart()` in auto session mode.
* `session.update()` can be called to send a session duration update to the server along with any events,
user properties and any other data types supported by Countly SDK. Called each `Config.sendUpdateEachSeconds`
seconds in auto session mode.
* `session.end()` must be called to mark end of session. All the data recorded since last `session.update()`
or since `session.begin()` in case no updates have been sent yet, is sent in this request as well. 
Called with last `Activity.onStop()` in auto session mode.

We also made some additions which would increase consistency of `Session` management. A 
developer can accidentally start multiple sessions, stop the same session multiple times. In such 
cases data received by Countly server won't be reliable. That's why developer cannot instantiate
a `Session` instance himself, he needs to use SDK methods for that. That's also why we have only
one `Session` instance active at any time. By SDK API it is impossible to have multiple `Session`
instances at any given point in time. Finally, only one `session.begin()` and one `session.end()` 
method call per session instance can be made, all others are ignored.

## Events
Events in Countly represent some meaningful event user performed in your 
application within a `Session`. Please avoid recording everything like all taps or clicks user performed.
In case you do, it will be very hard to extract valuable information from generated analytics.

An `Event` object contains following data types:
* `name`, or event key. *Required.* Unique string which identifies the event.
* `count` - number of times. *Required, 1 by default.* Like number of goods added to shopping basket.
* `sum` - sum of something, amount. *Optional.* Like total sum of the basket.
* `dur` - duration of the event. *Optional.* For example how much time user spent to checking out.
* `segmentation` - some data associated with the event. *Optional.* It's a Map<String, String> which can be filled
  with arbitary data like `{"category": "Pants", "size": "M"}`.

Standard way of recording events is through your `Session` instance:

```
Countly.session(getApplicationContext()).event('purchase')
                    .setCount(2)
                    .setSum(19.98)
                    .setDuration(35)
                    .addSegments("category", "pants", "size", "M")
                .record();
```

Please note last method in that call chain, `.record()` call is required for event to be recorded.

There is also a shorthand method for the call above - `Countly.event(Context, String)`. 
It does the same job as `Countly.session(Context).event(String)`.

Example above results in new event being recorded in current session. Event won't be sent to the server
right away. Instead, Countly SDK will wait until one of following happens:
* `Config.sendUpdateEachSeconds` seconds passed since begin or last update request in case of automatic session control.
* `Config.eventsBufferSize` events have been already recorded and not sent yet.
* `Session.update()` have been called by developer.
* `Session.end()` have been called by developer or by Countly SDK in case of automatic session control.

To send events right away you can set `Config.eventsBufferSize` to `1` or call 
`Countly.session(getApplicationContext()).update()` to manually send a request containing all 
recent data: events, parameters, crashes, etc.

### Timed events
There is also special type of `Event` supported by Countly - timed events. Timed events help you to
track long continuous interactions when keeping an `Event` instance is not very convenient.

Basic use case for timed events is following:
* User starts playing a level "37" of your game, you call `Countly.session(getApplicationContext()).timedEvent("LevelTime").addSegment("level", "37")`
to start tracking how much time user spends on this level.
* Then something happens when user is in that level, for example user bought some coins. Along with
regular "Purchase" event, you decide you want to segment "LevelTime" event with purchase information: 
`Countly.session(getApplicationContext()).timedEvent("LevelTime").setSum(9.99)`.
* Once user stopped playing, you need to stop recording this event: 
`Countly.session(getApplicationContext()).timedEvent("LevelTime").endAndRecord()`.

Once this event is sent to the server, you'll see:
* how much time users spend on each level (duration per `level` segmentation);
* which levels are generating most revenue (sum per `level` segmentation);
* which levels are not generating revenue at all since you don't show ad there (0 sum in `level` segmentation). 

With timed events, there is one thing to keep in mind: you have to end timed event for it to be
recorded. Without `endAndRecord()` call, nothing will happen.

## Crash reporting
In order to enable crash reporting, add `Config.enableFeatures(Config.Feature.Crash)` 
to your feature list. Once started, SDK will set `Thread.UncaughtExceptionHandler` on main thread
and will report about any uncaught exceptions automatically.

With SDK 18.X we've also added ANR detection. It works by continuously scheduling a `Runnable` on 
main thread of your app and on some background thread. Whenever `Runnable` on main thread 
wasn't executed in `Config.crashReportingANRTimeout` seconds, background thread records all stack
traces of the threads running and submits them to Countly server as a ANR crash. ANR detection
is enabled by default when you enable `Config.Feature.Crash`, but you can always disable it 
by calling `Config.disableANRCrashReporting()`. 

In addition to automatic crash reporting of unhandled exceptions, you can also report exceptions
you caught in your code: 
`Countly.session(getApplicationContext()).addCrashReport(Throwable t, boolean fatal, String name, Map<String, String> segments, String... logs)`.
First two parameters are mandatory, last three allow you to add some details to this crash: custom name,
segmentation like in `Event` and some log strings.

Not only manually reported crashes can be detailed using `name`, `segmentation` and `logs`. You
can also set your own customization class, for example, to hook up your custom app-specific logger class:
```
public static class CrashCustomizer implements CrashProcessor {
    @Override
    public void process(Crash crash) {
        crash.setLogs(... some Strings from your logger...);
    }
}

@Override
public void onCreate() {
    super.onCreate();
    Config config = new Config(COUNTLY_SERVER_URL, COUNTLY_APP_KEY)
            .setFeatures(Config.Feature.Crash)
            .setCrashProcessorClass(CrashCustomizer.class);

    Countly.init(this, config);
}
```

## Push notifications
Countly SDK 18.X supports only Firebase Cloud Messaging (FCM). Support for Google Cloud Messaging (GCM)
has been dropped and exists only in previous versions of SDK (17.09 and below). 

To enable FCM in your app, first follow standard [Firebase setting up guide](https://firebase.google.com/docs/android/setup]).
Once done, you must have `com.google.gms.google-services` plugin & at least 
`com.google.firebase:firebase-core:11.6.0` dependency set in your `build.gradle` and `google-services.json`
in your app folder.

Next up, you'll need to enable FCM in your app. In order to do this, first read 
[Firebase guide for this](https://firebase.google.com/docs/cloud-messaging/android/client) to
understand how it works. Basically, you'll need to create 2 `Service` classes and 
add their definitions to `AndroidManifest.xml`. We have example implementations in
our demo app available in Github. First one listens for `InstanceId` token and 
forwards it to Countly SDK:
```
package ly.count.android.demo;

import android.util.Log;

import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.FirebaseInstanceIdService;

import ly.count.sdk.android.CountlyPush;

/**
 * How-to module for listening for InstanceId changes
 */

public class DemoFirebaseInstanceIdService extends FirebaseInstanceIdService {
    @Override
    public void onTokenRefresh() {
        super.onTokenRefresh();
        Log.d("DemoInstanceIdService", "got new token: " + FirebaseInstanceId.getInstance().getToken());
        CountlyPush.onTokenRefresh(this, FirebaseInstanceId.getInstance().getToken());
    }
}
```

Second service listens for incoming messages and calls `CountlyPush.displayMessage(ctx, Map)`
in order to perform standard Countly logic for handling messages, that is show `Notification` if 
app is in background or `Dialog` if app is in foreground. 

```
package ly.count.android.demo;

import android.content.Intent;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import ly.count.sdk.android.CountlyPush;

public class DemoFirebaseMessagingService extends FirebaseMessagingService {
    private static final String TAG = "DemoMessagingService";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.d("DemoFirebaseService", "got new message: " + remoteMessage);

        Boolean result = CountlyPush.displayMessage(this, remoteMessage.getData());
        if (result == null) {
            Log.i(TAG, "Message wasn't sent from Countly server, so it cannot be handled by Countly SDK");
        } else if (result) {
            Log.i(TAG, "Message was handled by Countly SDK");
        } else {
            Log.i(TAG, "Message wasn't handled by Countly SDK because API level is too low for Notification support or because currentActivity is null (not enough lifecycle method calls)");
        }
    }
}
```

All the logic code behind message displaying is exposed in public and well-documented `CountlyPush` class. You can 
always copy that code into your class and customize it in any way. `DemoFirebaseMessagingService` also
shows how to access specific fields of FCM message sent by Countly (by using `CountlyPush.Message message = CountlyPush.decodeMessage(remoteMessage.getData())`).

Note that `CountlyPush` is mostly API level 11+ (because of `Notification`) with rich push being API 16+
(due to `Notification.BigPictureStyle`). FCM itself is API 14+.

### Cohorts support
Countly server 17.09+ added support of [User Cohorts](COHORTSLINKHERE). There are 2 types of cohorts: 
Generated (automatically generated from user behaviour) and Manual (set up from SDKs manually).

While you can use cohorts to send push notifications to specific user groups, there's also 
one feature in FCM which nicely falls into Cohorts functionality. That is topic subscriptions. So
whenever you call `Countly.user().edit().addToCohort("SOME_COHORT").commit()`, you not only add 
current user to `"SOME_COHORT"` manual cohort, but subscribe this user to `"SOME_COHORT"` FCM topic as well. 

### Migration from GCM to FCM
While functionality & APIs of these 2 systems (FCM & GCM) are very similar, there are still some differences:

* FCM server URL at Google is different from GCM server URL.
* FCM server key is different from GCM server key (GCM one is called legacy server key in Firebase Console).
* FCM device token is based on `InstanceId`, while GCM device token was a standalone thing.

Countly server automatically distinguishes GCM server key from FCM server key and picks correct server URL.

Common migration guide consists of following steps:
1. Update Countly SDK to 18.X and enable Firebase in your app.
2. Change server key in Countly dashboard from GCM (or legacy) server key to FCM server key you got from Firebase Console.

Once done:
* Old GCM device tokens will start going through new FCM server URL with new FCM server key. Google allows that 
  for easy migration.
* New FCM device tokens will eventually replace old GCM device tokens in Countly server as your users
launch your updated app with Countly SDK 18.X+ and FCM dependencies in it.
* Device tokens of users who don't launch your app will eventually expire and stop receiving 
 notifications until new app launch with updated SDK & Firebase libraries.

