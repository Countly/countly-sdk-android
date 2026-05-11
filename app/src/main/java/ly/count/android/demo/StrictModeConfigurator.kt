package ly.count.android.demo

import android.os.Build
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy.Builder
import android.os.StrictMode.VmPolicy
import android.os.strictmode.UntaggedSocketViolation
import android.os.strictmode.Violation
import android.util.Log
import java.util.concurrent.Executors

object StrictModeConfigurator {

    private val penaltyExecutor by lazy { Executors.newSingleThreadExecutor() }

    private val threadPolicy: StrictMode.ThreadPolicy
        get() = Builder()
            .detectAll()
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    penaltyListener(penaltyExecutor) { violation ->
                        val knownIssue = knownThreadViolations.any { it(violation) }
                        if (!knownIssue) Log.w("StrictMode", null, violation)
                    }
                } else {
                    penaltyLog()
                }
            }
            .penaltyDeathOnNetwork()
            .build()

    private val knownThreadViolations: List<Violation.() -> Boolean> by lazy {
        listOf(
            // add known violations if any
        )
    }

    private val vmPolicy: VmPolicy
        get() = VmPolicy.Builder()
            .apply {
                detectActivityLeaks()
                detectLeakedSqlLiteObjects()
                detectLeakedClosableObjects()
                detectLeakedRegistrationObjects()
                detectFileUriExposure()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    detectCleartextNetwork()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    detectContentUriWithoutPermission()
                    detectUntaggedSockets() // okhttp "issue"
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    detectCredentialProtectedWhileLocked()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    detectIncorrectContextUse() // countly has known issue
                    detectUnsafeIntentLaunch()
                }
            }
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    penaltyListener(penaltyExecutor) { violation ->
                        val knownIssue = knownVmViolations.any { it(violation) }
                        if (!knownIssue) Log.w("StrictMode", null, violation)
                    }
                } else {
                    penaltyLog()
                }
            }
            .penaltyDeathOnFileUriExposure()
            .build()

    private val knownVmViolations: List<Violation.() -> Boolean> by lazy {
        listOfNotNull(
            {
                this is UntaggedSocketViolation && stackTrace.any {
                    it.className.contains("ImmediateRequestMaker") || it.className.contains("ConnectionProcessor") // countly
                }
            },
        )
    }

    @JvmStatic
    fun configure() {
        StrictMode.setThreadPolicy(threadPolicy)
        StrictMode.setVmPolicy(vmPolicy)
    }
}
