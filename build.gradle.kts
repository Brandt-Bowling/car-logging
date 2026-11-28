plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

tasks.register<Exec>("checkArchitectureGates") {
    group = "verification"
    description = "Enforces file-size ceilings, composable length caps, and architecture invariants."
    workingDir = rootDir
    commandLine("bash", "scripts/check_architecture_gates.sh")
}
