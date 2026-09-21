package com.ironmind.app

import com.ironmind.app.notification.RestTimerNotifier

/** In-memory [RestTimerNotifier] that records interactions so ViewModel tests can assert on them. */
class FakeRestTimerNotifier : RestTimerNotifier {
    val countdownValues = mutableListOf<Int>()
    var completeCount = 0
    var cancelCount = 0

    override fun showCountdown(remainingSeconds: Int) {
        countdownValues += remainingSeconds
    }

    override fun showComplete() {
        completeCount++
    }

    override fun cancel() {
        cancelCount++
    }
}
