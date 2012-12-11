##What's Countly?
[Countly](http://count.ly) is an innovative, real-time, open source mobile analytics application. It collects data from 
mobile devices, and visualizes this information to analyze mobile application usage and end-user behavior. 
There are two parts of Countly: the server that collects and analyzes data, and mobile SDK that sends 
this data (for iOS, Android, Windows Phone and Blackberry). Both parts are open source.

##Installing Android SDK

Installing Android SDK requires two very easy steps. Countly Android SDK uses OpenUDID (which comes ready with the zip file). First step is about OpenUDID requirement and second step is integrating Countly SDK to your project:

###1. Add this to your manifest:

* Add OpenUDID_manager.java and OpenUDID_service.java to your project under Eclipse.

<pre class="prettyprint">
&lt;service android:name=&quot;org.openudid.OpenUDID_service&quot;&gt;
    &lt;intent-filter&gt;
        &lt;action android:name=&quot;org.openudid.GETUDID&quot; /&gt;
    &lt;/intent-filter&gt;
&lt;/service&gt;</pre>

###2. Now it's time to add main Countly SDK to your project using steps below:

* Add Countly.java to your project under Eclipse.
* Call `Countly.sharedInstance().init(...)` in onCreate. init(...) function requires the URL of your Countly server.
* Call `Countly.sharedInstance().onStart()` in onStart.
* Call `Countly.sharedInstance().onStop()` in onStop.

Additionally, make sure that *INTERNET* permission is set if there's none in your manifest file.

Countly Server source code:

- [Countly Server (countly-server)](https://github.com/Countly/countly-server)

Other Countly SDK repositories:

- [Countly iOS SDK (countly-sdk-ios)](https://github.com/Countly/countly-sdk-ios)
- [Countly Android SDK (countly-sdk-android)](https://github.com/Countly/countly-sdk-android)
- [Countly Windows Phone SDK (countly-sdk-windows-phone)](https://github.com/Countly/countly-sdk-windows-phone)
- [Countly Blackberry Webworks SDK (countly-sdk-blackberry-webworks)](https://github.com/Countly/countly-sdk-blackberry-webworks)

##How can I help you with your efforts?
Glad you asked. We need ideas, feedbacks and constructive comments. All your suggestions will be taken care with upmost importance. 

We are on [Twitter](http://twitter.com/gocountly) and [Facebook](http://www.facebook.com/Countly) if you would like to keep up with our fast progress!

For community support page, see [http://support.count.ly](http://support.count.ly "Countly Support").