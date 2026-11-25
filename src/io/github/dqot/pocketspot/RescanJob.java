package io.github.dqot.pocketspot;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Phone side: keeps the background scan registered (e.g. after Bluetooth was turned off and on). */
public final class RescanJob extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        if (Status.isPhone(this)) Status.setScan(this, Scanner.register(this));
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
