## 20.11.9
  * Fixing issue with star rating lowest allowed value. Changing it from 0 to 1.

## 20.11.8
  * Minor tweaks. Changes related to publishing to MavenCentral

## 20.11.7
  * Adding metric for the device manufacturer.
  * Fixing potential issues by sending all available events before the "end session" request
  
## 20.11.6
  * Fixed init time bug where the context from the provided application class was not used.
  * Fixed init time bug which happened when location was disabled during init.

## 20.11.5
  * Added option to enable additional push redirection protections

## 20.11.4
  * Added a way to retrieve feedback widget data and manually report them
  * Fixed bug where network request were retried too soon after a failed request

## 20.11.3
  * Removed thrown exceptions in places where that is possible

## 20.11.2
  * Added SDK log listener
  * Refactored all logs to use the new logging mechanism
  * Fixed bug where manual session control calls were working even when not enabled

## 20.11.1
  * Lessened push notification security restrictions
  * Added a default way to acquire app start timestamp for APM
  * Added a way to override the current app start timestamp for APM
  * Added manual trigger for when app has finished loading for APM
  * Added manual foreground/background triggers for APM

## 20.11.0
  * !! Consent change !! To record orientation you now need to give "user" consent
  * !! Consent change !! To use remote config, you now need to give "remote-config" consent
  * !! Push breaking changes !! Due to a security vulnerability the following permission need to be added to your app manifest:
  '
  <uses-permission android:name="${applicationId}.CountlyPush.BROADCAST_PERMISSION" />
  '
  To make these breaking changes noticable, the broadcast listener id 'NOTIFICATION_BROADCAST' has been replaced with 'SECURE_NOTIFICATION_BROADCAST'

  * Added survey, nps feature
  * Added retries to push media download
  * Added call that removes all server requests that don't have the currently set appKey
  * Added call that updates all server requests so that all of them have the currently set appKey
  * Update breakpad for native exception catching
  * Symbol upload tool now sends the used breakpad version
  * Reworked openID device ID to not be a separate service  
  * Sending device type (phone, tablet, tv) as part of metrics
  * Reworked location data persistence and when it is sent. It's recommended to recheck your app to see if it's still behaving as expected.
  * Deprecated CountlyConfig constructor that takes 'Context'. Added new constructor that also takes the Application class instead of 'Context'
  * Initialising the SDK without providing the application class is not deprecated
  * Fixed a push consent edge case bug where it would have been counted when set in the past
  * Fixed a push consent bug where 'anyConsent' would have returned true when no consent would have been set
  * Fixed a bug regarding temporary ID mode transitioning device ID's which would merge it to the previous value
  * Fixed init time openUDID bug which made it less persistent than required
  * Fixed potential multithreaded synchronization issues
  * Fixed location url encoding bug
  * Fixed init issue where the device ID was not saved and could be changed by changing it's value during next init

## 20.04.5
  * Replacing sha-1 hash with sha-256 hash for requests.
  * Adding apm calls for cancelTrace, cancelAllTraces, recordNetworkTrace
  * Adding option to use Huawei push notifications
  * making tweaks to APM key validation logic

## 20.04.4
  * Adding metric override feature
  * Adding functionality to override SDK name and SDK version strings
  * Fixed an issue in the network request retry logic
  * Removed requirement for any consent to be set before device ID can be changed.
  * Mitigating issue which would happen if push token is refreshed before SDK is initialized.
  * Fixed the sending of empty event collections in cases where event sending was forced.

## 20.04.3
  * Adding fallback cache to CountlyPush for cases where the SDK is not initialised
  * Fixed bug which happened when consent was required but no consent was provided during init

## 20.04.2
  * Fixed bug where data was not removed from the URL in case of forced http POST
  * Fixed HTTP post issue with remote config and rating widget

## 20.04.1
  * Changing permissions for modules so that they are accessible from kotlin

## 20.04
  * Adding functionality for filtering crashes
  * Adding functionality to set segmentation for automatic and manual views
  * Adding functionality to ignore activities for automatic view tracking
  * Adding functionality to track orientation changes
  * Adding call to manually record user rating without showing any UI
  * Adding call to record an event with a given timestamp
  * Adding call to cancel a timed event
  * Adding manual session control
  * Adding option to set tamper protection salt in config object
  * Adding calls to give and remove consent to all features at the same time
  * Adding a way to change the session update timer interval
  * Adding call to set notification accent color
  * Adding APM functionality to record app start time
  * Adding APM functionality to record custom traces
  * Adding APM functionality to record network request traces
  * Adding APM functionality to record app time spent in foreground and background
  * Adding remote config call to get all saved values
  * Moving crash, event, view, rating related calls to a separate modules
  * Deprecating functions that are being put into modules
  * Migrated to AndroidX
  * Improved internal time management which fixes inconsistencies that could arrise on day changes
  * Fixed a bug where deep links from notifications did not include the sent message as part of the intent
  * Removing deprecated "addCrashLog" call
  * Removing deprecated "logException" call
  * Removing deprecated "setCustomUserData" call
  * Removing deprecated "setUserData" call

## 19.09.3
  * Fixed a bug that did not add the link to the push dialog button.
  
## 19.09.2
  * Fixed a bug that did not add links to push buttons.

## 19.09.1
  * Applying null pointer exception mitigations to immediate request maker.

## 19.09
  * Adding feature to flush all requests in the queue
  * Adding CountlyConfig object which is used during init
  * Added call to try to complete stored requests
  * Merging Push module with SDK module
  * Fixing a potential 'null' bug for push
  * Fixing a consent bug which disabled features when consent was not enabled
  * Fixed a race condition bug when sending push token to server
  * Fixed remote config bug during init when no device ID was available
  * Fixed bug with events not using the provided event duration.
  * Fixing issue that automated star rating session count was not cleared when changing device ID
  * Removing GCM push support

## 19.02.3
  * Adding support for native crash handling and symbolication

## 19.02.2
  * Fixing bug with location data not being erased during begin_session
  * Fix potential issue with push Activity

## 19.02.1
  * fixing endEvent bug that ignores provided "count" and "sum" values

## 19.02
  * Added remote config
  * Star rating requests now use the same urlConnection as other requests
  * Added functionality for adding custom header key/value pairs to every request

## 18.08.1
  * Fixed bug with events getting unrelated segmentation fields added
  * Added flags to push action intent
  * Refactored push URL action handling

## 18.08
  * Added functionality for webView user rating popup dialog
  * Added call for recording unhandled exceptions
  * Added 10 second delay before merging device Id's
  * Added functionality for sending integers and doubles with segmentation, instead of just strings
  * Added call to record exception with a throwable
  * Improved Countly SDK logging messages
  * SDK now tries to send messages in it's timer event when in the background
  * Limited the size of crash logs to 10k characters
  * Limited the size of breadcrumbs to 1000 characters
  * Limited the amount of breadcrumbs to 1000 entries. If a newer one is added, the oldest one is discarded
  * Fixed a deviceId changing related bug
  * Fixed a bug for setting push consent before init

## 18.04
  * Added functionality for GDPR (giving and removing consent for features)
  * Added separate module for FCM push notifications

## 18.01.2
  * Fixing a crash in messaging because of null context 
  
## 18.01.1
  * Fixing small push notification accent color bug
  * Properly deleting cached location data 
  * Improving debug and log messages

## 18.01
  * Changes made how location data is passed and handled
  * Adding option to disable sending of location data (it can be reenabled later)
  * Adding option to add a large icon and accent color to push notifications
  * Adding option to add meta information to push notification intents

## 17.09.2
  * Adding option to override the icon for push notifications

## 17.09.1
  * Adding additional calls for manipulating the star rating dialog

## 17.09
  * Fixed app crawler filtering & ANR if substantially changing device date back in time

## 17.05
  * Added Rich Push Notifications support (attachments and custom action buttons)
  * Added functionality to ignore app crawlers
  * Added calls to retrieve device ID and ID type
  * Added call see if onStart has been called at least once

## 16.12.3
  * Adding certificate pinning in addition to public key pinning

## 16.12.02
  * Changing automatic star rating default behaviour (disabling it)
  * Removing Context as a needed field from some function calls

## 16.12.01
  * Added additional meta data to each API request
  * Added support for the star rating plugin
  * Added option to force HTTP POST for all requests
  * Added support for optional parameters during initialization

## 16.02
  * Views support
  * User data part updated

## 15.08.01
   * Lowering required API level back to 9

## 15.08
  * Bug fixes:
   - Incorrect handling of empty review message #50
   - Change GCM registration ID whenever sender ID changed #51

## 15.06
  * Bug fixes & other improvements
  * Attribution analytics
  * Crash reports
