20.02
  * Adding functionality for filtering crashes
  * Adding functionality to set segmentation for automatic and manual views
  * Adding functionality to ignore activities for automatic view tracking
  * Adding functinality to track orientation changes
  * Adding call to manually record user rating without showing any UI
  * Adding call to record a event with a given timestamp
  * Adding call to cancel a timed event
  * Adding manual session control
  * Adding option to set tamper protection salt in config object
  * Adding calls to give and remove consent to all features at the same time
  * Adding a way to change the session update timer interval
  * Adding call to set notification accent color
  * Moving crash, event, view, rating related calls to a seperate modules
  * Deprecating functions that are being put into modules
  * Removing depracated "addCrashLog" call
  * Removing depracated "logException" call
  * Removing depracated "setCustomUserData" call
  * Removing depracated "setUserData" call
  * Migrated to AndroidX
  * Improved internal time management which fixes inconsistencies that could arrise on day changes

19.09.2
  * Fixed a bug which did not add links to push buttons.

19.09.1
  * Applying null pointer exception mitigations to immediate request maker.

19.09
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

19.02.3
  * Adding support for native crash handling and symbolication

19.02.2
  * Fixing bug with location data not being erased during begin_session
  * Fix potential issue with push Activity

19.02.1
  * fixing endEvent bug that ignores provided "count" and "sum" values

19.02
  * Added remote config
  * Star rating requests now use the same urlConnection as other requests
  * Added functionality for adding custom header key/value pairs to every request

18.08.1
  * Fixed bug with events getting unrelated segmentation fields added
  * Added flags to push action intent
  * Refactored push URL action handling

18.08
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

18.04
  * Added functionality for GDPR (giving and removing consent for features)
  * Added separate module for FCM push notifications

18.01.2
  * Fixing a crash in messaging because of null context 
  
18.01.1
  * Fixing small push notification accent color bug
  * Properly deleting cached location data 
  * Improving debug and log messages

18.01
  * Changes made how location data is passed and handled
  * Adding option to disable sending of location data (it can be reenabled later)
  * Adding option to add a large icon and accent color to push notifications
  * Adding option to add meta information to push notification intents

17.09.2
  * Adding option to override the icon for push notifications

17.09.1
  * Adding additional calls for manipulating the star rating dialog

17.09
  * Fixed app crawler filtering & ANR if substantially changing device date back in time

17.05
  * Added Rich Push Notifications support (attachments and custom action buttons)
  * Added functionality to ignore app crawlers
  * Added calls to retrieve device ID and ID type
  * Added call see if onStart has been called at least once

16.12.3
  * Adding certificate pinning in addition to public key pinning

16.12.02
  * Changing automatic star rating default behaviour (disabling it)
  * Removing Context as a needed field from some function calls

16.12.01

  * Added additional meta data to each API request
  * Added support for the star rating plugin
  * Added option to force HTTP POST for all requests
  * Added support for optional parameters during initialization

16.02

  * Views support
  * User data part updated

15.08.01

   * Lowering required API level back to 9

15.08

  * Bug fixes:
   - Incorrect handling of empty review message #50
   - Change GCM registration ID whenever sender ID changed #51

15.06

  * Bug fixes & other improvements
  * Attribution analytics
  * Crash reports
