package ly.count.android.demo.kotlin

import android.app.Application
import ly.count.android.sdk.Countly
import ly.count.android.sdk.CountlyConfig
import ly.count.android.sdk.CountlyStore
import ly.count.android.sdk.ModuleLog

//import ly.count.android.sdk.DeviceIdType

class App :Application() {
  val COUNTLY_SERVER_URL = "https://master.count.ly"
  val COUNTLY_APP_KEY = "5e20d03806255d314eb6679b26fda6e580b3d899"
  override fun onCreate() {
    super.onCreate()

    val cs = CountlyStore(this, ModuleLog())
    cs.deviceID = "asd"

    //var config : CountlyConfig = (CountlyConfig(this, COUNTLY_APP_KEY, COUNTLY_SERVER_URL)).setLoggingEnabled(true);
    //Countly.sharedInstance().init(config)

    val countlyConfig = CountlyConfig(
      this,
      COUNTLY_APP_KEY,
      COUNTLY_SERVER_URL
    )
    .setDeviceId(
      "myDeviceId"
    )
    .enableCrashReporting()
    .setRecordAllThreadsWithCrash()
    .setLoggingEnabled(true)

    Countly.sharedInstance().init(countlyConfig)
  }
}