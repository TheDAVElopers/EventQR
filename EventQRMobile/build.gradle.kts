// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.test) apply false
}

// Design-token guard (tools/check-tokens.ps1 -Enforce): fails on colour / size / spacing / text / string literals that
// should be tokens (categories in the script header; reviewed exceptions in tools/dp-allow.txt). Registered for manual use
// (`gradlew checkTokens`); intentionally NOT wired into `check` or CI yet.
tasks.register<Exec>("checkTokens") {
    group = "verification"
    description = "Runs tools/check-tokens.ps1 -Enforce (design-token literal guard). Not part of `check`."
    workingDir = rootDir
    val script = file("tools/check-tokens.ps1").absolutePath
    val windows = System.getProperty("os.name").lowercase().contains("win")
    if (windows) {
        commandLine("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", script, "-Enforce")
    } else {
        commandLine("pwsh", "-NoProfile", "-File", script, "-Enforce")
    }
}
