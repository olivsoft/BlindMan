package ch.olivsoft.android.blindman;

import android.os.CountDownTimer;

/**
 * Counts down until cancelled or the given time has elapsed.
 * This timer extends {@link CountDownTimer} and includes
 * a method to check if it is currently running.
 * It does not call any intermediate ticks.
 * To use this class extend from it and override onTimerElapsed.
 *
 * @author Oliver Fritz, OlivSoft
 */
public abstract class SimpleCountDownTimer extends CountDownTimer
{
    private boolean timerRunning = false;

    public SimpleCountDownTimer(long millis)
    {
        super(millis, millis);
    }

    public final boolean isTimerRunning()
    {
        return timerRunning;
    }

    // Pseudo overrides
    public synchronized final void startTimer()
    {
        if (timerRunning)
            return;
        timerRunning = true;
        start();
    }

    public synchronized final void cancelTimer()
    {
        if (!timerRunning)
            return;
        timerRunning = false;
        cancel();
    }

    // This must be overridden
    public abstract void onTimerElapsed();

    @Override
    public final void onFinish()
    {
        timerRunning = false;
        onTimerElapsed();
    }

    // Get rid of this method
    @Override
    public final void onTick(long millisUntilFinished)
    {
    }
}
