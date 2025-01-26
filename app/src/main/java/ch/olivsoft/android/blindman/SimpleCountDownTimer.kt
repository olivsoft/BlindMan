package ch.olivsoft.android.blindman

import android.os.CountDownTimer

/**
 * Counts down until cancelled or the given time has elapsed.
 * This timer extends [CountDownTimer] and includes
 * a method to check if it is currently running.
 * It does not call any intermediate ticks.
 * To use this class extend from it and override onTimerElapsed.
 *
 * @author Oliver Fritz, OlivSoft
 */
abstract class SimpleCountDownTimer(millis: Long) : CountDownTimer(millis, millis) {

    var isTimerRunning = false

    // Pseudo overrides
    @Synchronized
    fun startTimer() {
        if (isTimerRunning) return
        isTimerRunning = true
        start()
    }

    @Synchronized
    fun cancelTimer() {
        if (!isTimerRunning) return
        isTimerRunning = false
        cancel()
    }

    // This must be overridden
    abstract fun onTimerElapsed()

    override fun onFinish() {
        isTimerRunning = false
        onTimerElapsed()
    }

    // Get rid of this method
    override fun onTick(millisUntilFinished: Long) {
    }
}
