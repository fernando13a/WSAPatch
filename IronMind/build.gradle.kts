// Top-level build file — configuration shared across modules lives here.
// Plugins are declared (but not applied) so subprojects can apply them by alias.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
