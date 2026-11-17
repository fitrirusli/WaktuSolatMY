package my.waktusolat.app;

import android.app.job.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;

public final class SyncJob extends JobService {
    private final ConcurrentHashMap<Integer, AtomicBoolean> active = new ConcurrentHashMap<>();
    @Override public boolean onStartJob(JobParameters parameters) {
        AtomicBoolean running = new AtomicBoolean(true);
        active.put(parameters.getJobId(), running);
        PrayerRepository repository = new PrayerRepository(this);
        repository.refresh(repository.zone(), parameters.getExtras().getBoolean("force", false), (schedule, error) -> {
            if (!running.getAndSet(false)) return;
            active.remove(parameters.getJobId(), running);
            PrayerWidget.updateAll(this);
            UpdateScheduler.scheduleBoundary(this);
            jobFinished(parameters, error != null);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters) {
        AtomicBoolean running = active.remove(parameters.getJobId());
        if (running != null) running.set(false);
        return true;
    }
}
