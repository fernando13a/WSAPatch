package com.ironmind.app.notification

/**
 * Surfaces the rest-timer state to the user through system notifications, so the countdown and its
 * completion buzz reach them even when the Session screen is not on-screen (screen off, phone in a
 * pocket, or the app in the background). The interface keeps the ViewModel free of Android
 * dependencies — the real implementation is [AndroidRestTimerNotifier]; unit tests use a fake.
 */
interface RestTimerNotifier {

    /** Posts/updates a silent, ongoing notification showing the remaining rest time. */
    fun showCountdown(remainingSeconds: Int)

    /** Replaces the countdown with a heads-up alert (vibrates) when the rest period ends. */
    fun showComplete()

    /** Clears any rest-timer notification. */
    fun cancel()
}
