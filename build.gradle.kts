// Top-level build file. Configuration common to all sub-projects/modules
// is declared here. Plugin versions are pinned so CI builds are reproducible.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
