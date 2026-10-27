package com.htc.widget.weatherclock.openmeteo;

import android.app.KeyguardManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

/**
 * Keeps the widget process un-cached while the screen is on, for the unlock flip (FlipAnimV3).
 *
 * Honor holds broadcasts back from a cached widget process: USER_PRESENT and SCREEN_OFF often arrive late or
 * not at all, and manifest receivers don't get them (background execution limits). While this job runs the
 * process isn't cached, so its registered receivers get them on time. The job itself does no work then:
 *   - while the lock screen is up it polls the keyguard (Honor sends USER_PRESENT as soon as face unlock
 *     recognises the user, while the lock screen stays up until a swipe), a few seconds at most;
 *   - after that it only waits for SCREEN_OFF, sets the cards back (FlipAnimV3.screenGoingOff) and ends.
 * A run lasts at most RUN_MS (under the job run-time limit); the next one is scheduled when it ends.
 */
public class UnlockWatchJob extends JobService {
    private static final String TAG = "UnlockWatchJob";
    static final int JOB_ID = 1179404627;
    /** Polls for the keyguard going away, only while it's showing. */
    private static final long POLL_MS = 100;
    /**
     * One run, under the system's run-time limit (expedited jobs about 3 min, regular ones 10 min); the next
     * one, a regular job (it needn't start at once), is scheduled when it ends.
     */
    private static final long EJ_RUN_MS = 150000;
    private static final long RUN_MS = 8 * 60000;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private JobParameters mParams;
    private BroadcastReceiver mScreenOff;
    private boolean mDone;

    /** Screen on, or the widget in use: start watching now (an expedited job starts at once). */
    static void start(Context context) {
        start(context, true);
    }

    private static void start(Context context, boolean expedited) {
        try {
            JobScheduler js = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (js == null || js.getPendingJob(JOB_ID) != null) {
                return;  // already watching
            }
            JobInfo.Builder b = new JobInfo.Builder(JOB_ID, new ComponentName(context, UnlockWatchJob.class));
            if (expedited && Build.VERSION.SDK_INT >= 31) {
                b.setExpedited(true);
            } else {
                b.setOverrideDeadline(0);
            }
            js.schedule(b.build());
        } catch (Throwable th) {
            Log.w(TAG, "schedule failed", th);
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mParams = params;
        final KeyguardManager km = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isInteractive()) {
            return false;
        }
        mScreenOff = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                FlipAnimV3.screenGoingOff(UnlockWatchJob.this, new Runnable() {
                    @Override
                    public void run() {
                        finish(false);
                    }
                });
            }
        };
        registerReceiver(mScreenOff, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        mHandler.post(new Runnable() {
            private boolean mSawLock;

            @Override
            public void run() {
                if (km == null || !km.isKeyguardLocked()) {
                    FlipAnimV3.unlocked(UnlockWatchJob.this, mSawLock);  // once per screen-on; then just wait
                } else if (pm == null || pm.isInteractive()) {
                    mSawLock = true;
                    mHandler.postDelayed(this, POLL_MS);
                }
            }
        });
        boolean ej = Build.VERSION.SDK_INT >= 31 && params.isExpeditedJob();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish(true);
            }
        }, ej ? EJ_RUN_MS : RUN_MS);
        return true;
    }

    private void finish(boolean renew) {
        if (mDone) {
            return;
        }
        mDone = true;
        cleanup();
        if (mParams != null) {
            jobFinished(mParams, false);
        }
        if (renew) {
            // A fresh run, within the job run-time limit; once this one is no longer pending.
            final Context app = getApplicationContext();
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                    if (pm == null || pm.isInteractive()) {
                        start(app, false);
                    }
                }
            }, 1000);
        }
    }

    private void cleanup() {
        mHandler.removeCallbacksAndMessages(null);
        if (mScreenOff != null) {
            try {
                unregisterReceiver(mScreenOff);
            } catch (Throwable ignored) {
            }
            mScreenOff = null;
        }
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // Stopped by the system (run-time limit, quota): carry on with a fresh run while the screen is on.
        mParams = null;
        finish(true);
        return false;
    }
}
