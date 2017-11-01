#Introduction
Countly SDK 18.X is a completely redesigned version of our Android SDK. Please use it with caution 
since it hasn't yet tested by thousands of developers in thousands of applications as 
our previous SDK versions.

##Highlights
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
 performance 1000x in some rare cases. More importantly, Countly SDK doesn't block 
 main thread anymore.
* **More efficient networking.** Countly will send less requests with more data in each 
of them, thus increasing overall efficiency.  
* **Programmatic session control.** Developer can now define what is a `Session`, when it
 starts and when it ends.
* **Test mode** is an SDK-wide option which ensures you don't call SDK API methods with
invalid data. In test mode Countly SDK will raise a `RuntimeException` whenever it's in
inconsistent state. If test mode is off, Countly will ignore any inconsistencies and log
an error instead of throwing an `Exception`.
* **Logging** is also improved, so you can now manage log level from `DEBUG` to `ERROR`.
* **Modules.** Apart from standard logic, SDK is split into 7 modules responsible for 
 some part of it: `ModuleSessions` manages automatic session tracking, `ModuleCrash` 
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


#Easy start
Process of setting up Countly SDK includes 2 simple steps: adding SDK as dependency to your Android Studio project
and initializing SDK. Once those are done, you'll have basic analytics on your server like users, sessions,
devices, etc.

##Adding dependency
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
##Initializing SDK
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

##Record first event
Now lets record our first custom event:

```
        Button button = (Button) findViewById(R.id.makePurchase);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Countly.event("purchase-btn").setSum(10).record();
                // ... do the purchasing logic ...
            }
        });

```

Whenever Purchase button is pressed, Countly will record an event with a key `purchase-btn` with sum of `10`
 and will eventually send it to our Countly server with next request. You can also add segmentation, 
 count and duration to an event. See our javadoc EVENTLINKHERE.
 
 Now launch your app, open your Countly dashboard and make sure data is there. In case it's not, make sure
 you used correct server url and app key. If they are, check logs in `logcat`. 

#Architecture
Now let's have an overview of Countly SDK internals. 
##Components
Starting version 18.X Countly SDK supports 2 ways of operation:

* **Separate process (default) model.** SDK is split in 2 parts. Backend part lives in
separate process, performs network requests to Countly server and detects crashes
of your main process. Frontend part runs along with your application and is being used by
 your application to record events, process push notifications, etc.

* **Single process model.** In this model SDK is not split and lives in your application
 process with lifecycle identical to your app.

Main benefit of separate process model is that sending requests process is no longer 
 coupled to your application lifecycle. That's important in several cases like 
 network connectivity issues, application force-quit or crash. Previously a crash wasn't sent to 
 Countly server until next app launch. Some crashes weren't sent at all in case 
 they occur right on application launch.

Another important benefit is performance. Countly doesn't pollute your application 
memory stack with sometimes huge volume of unsent data. All I/O is now performed 
in background threads or in a separate process in case of network I/O. 
There's almost no synchronization or locking of any kind in our SDK anymore. 

When you add `sdk` gradle dependency, `service` element is automatically injected in your `AndroidManifest.xml`.
To switch from default 2-process model to single process model, just add `tools` namespace to your `AndroidManifest.xml` 
and override our service definition:
```
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="... your package ..."
    xmlns:tools="http://schemas.android.com/tools">
    
    <application ...>
        ...
        <service android:name="ly.count.android.sdk.internal.CountlyService" tools:remove="android:process">
        </service>
    </application>
</manifest>
```

##Storage
Countly SDK stores serialized versions of following classes: `InternalConfig`, `SessionImpl`, `RequestImpl`, `CrashImpl` and `UserImpl`.
 All those are stored in device memory, in a separate files with filenames prefixed with `[CLY]_`.
 
##Modules
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

#API
This page is not supposed to list all possible features and implementation
details of Countly SDK. Instead, we focus on how to perform some common tasks so you could save time for 
more important things. Full documentation is available as javadoc here: PASTETHELINKHERE. 
In case you need more understanding of Countly SDK internals, you should start form CONFIGLINKHERE 
as main entry point into configuration and features, following SESSIONLINKHERE
regarding `Session` lifecycle and control. USERLINKHERE explains user data, EVENTLINKHERE is 
about recording events. 

##Device ID
Android SDK supports following strategies of device ID generation:
* `OPEN_UDID`. Default. Basically `ANDROID_ID` if it is available or a pseudo-random `String` if it's not.
* `ADVERTISING_ID`. ID is taken from `AdvertisingId` class.
* `INSTANCE_ID`. Firebase `InstanceId`.
* `CUSTOM_ID`. Developer-specified device ID `String`.

None of the strategies above is 100% persistent or 100% non-persistent, that is living across
multiple app installs. Some of them, in some cases, maintain ID between different app installs.

Strategy is set in `Config` class prior to `Countly.init()` with `setDeviceIdStrategy()` method.

###Authentication
With no special steps performed, SDK will count any new app install (see note above regarding device ID 
persistency) as new user. In some cases, like when you have some kind of authentication system 
in your app, that's not what you want. When you want actual person to be counted as one user in Countly
and your application supports some kind of authentication system, you should 
call `Countly.login()` with your specific user ID `String`. After this method call reaches 
Countly server, it will:
 * take old user profile (all the events happened prior to login), 
let's say with `OPEN_UDID`-based id;
 * take user profile with your new ID if any (user could 
authenticate in your app previously);
 * and will merge these profiles together. 

When user logs out from your application, you can:
* keep posting events under latest known authenticated user, meaning just do nothing;
* or call `Countly.logout()` so SDK could reset its ID to the strategy you use in `Config`, let's say
`OPEN_UDID`, and start a new `Session` for this user.

##Sessions
Session in Countly is a single app launch or several app launches if time between them is less 
than 30 seconds (by default).

In Android SDK default way of detecting an app launch is monitoring number of active `Activity` 
instances. To make this happen our SDK registers `Application.ActivityLifecycleCallbacks` and 
just increments an integer whenever `onActivityStarted` is called and decrements it 
whenever `onActivityStopped` is called. The only catch here is that `Application.ActivityLifecycleCallbacks` 
is available only in API levels 14+, meaning in case your app supports API levels lower than 14,
you'll need to make your own `Activity` superclass and subclass all your activities from it:
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

For these apps Countly supports what we call programmatic session control. First, to enable it,
you need to call `Config.enableProgrammaticSessionsControl()`. From now on Countly won't 
start or end any sessions, this is your responsibility now. To start new `Session` call 