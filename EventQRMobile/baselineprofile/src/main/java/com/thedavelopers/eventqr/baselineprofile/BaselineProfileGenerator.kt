package com.thedavelopers.eventqr.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's baseline profile. Never runs in CI; the output is committed to app/src/main/baseline-prof.txt.
 *
 * Regenerate with an emulator (or userdebug device) that has the app installed and signed out:
 *   ANDROID_SERIAL=<serial> ./gradlew :baselineprofile:connectedBenchmarkAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=baselineprofile
 * then copy the `*-baseline-prof.txt` from
 * baselineprofile/build/outputs/connected_android_test_additional_output/ into app/src/main/baseline-prof.txt.
 *
 * Covers the signed-out cold-start path (splash -> landing -> sign-in screen). A signed-in session can't be
 * assumed on the generation device, so the dashboard path is not exercised here.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() {
        // The camera prompt would otherwise sit on top of the landing screen. Granting kills the target's process,
        // so it is done once up front rather than inside the profiled block.
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("pm grant $TARGET_PACKAGE android.permission.CAMERA").close()

        rule.collect(packageName = TARGET_PACKAGE) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "btnCreateAccount")), UI_TIMEOUT_MS)

            // Landing -> sign-in screen.
            device.findObject(By.res(TARGET_PACKAGE, "btnSignIn"))?.click()
            device.wait(Until.hasObject(By.res(TARGET_PACKAGE, "edtEmail")), UI_TIMEOUT_MS)
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.thedavelopers.eventqr"
        const val UI_TIMEOUT_MS = 5_000L
    }
}
