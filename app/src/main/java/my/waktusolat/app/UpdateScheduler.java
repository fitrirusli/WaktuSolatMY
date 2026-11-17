package my.waktusolat.app;

import android.app.*;
import android.app.job.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.os.PersistableBundle;
import java.time.*;

public final class UpdateScheduler {
    private static final int PERIODIC_JOB = 701;
    private static final int SYNC_JOB = 702;
    public static final String BOUNDARY = "my.waktusolat.app.BOUNDARY";
    public static final String REFRESH = "my.waktusolat.app.REFRESH";

    public static void schedule(Context context) {
        if (widgetCount(context) == 0) return;
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs.getPendingJob(PERIODIC_JOB) == null) {
            jobs.schedule(new JobInfo.Builder(PERIODIC_JOB, new ComponentName(context, SyncJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(12L * 60 * 60 * 1000)
                    .setPersisted(true).build());
        }
        scheduleBoundary(context);
    }
    public static void sync(Context context, boolean force) {
        PersistableBundle extras = new PersistableBundle();
        extras.putBoolean("force", force);
        context.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(SYNC_JOB, new ComponentName(context, SyncJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setExtras(extras).setMinimumLatency(0)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL).build());
    }
    public static void scheduleBoundary(Context context) {
        if (widgetCount(context) == 0) return;
        Instant now = Instant.now();
        Instant trigger = now.atZone(PrayerSchedule.MALAYSIA).toLocalDate().plusDays(1).atStartOfDay(PrayerSchedule.MALAYSIA).toInstant();
        PrayerRepository repository = new PrayerRepository(context);
        PrayerSchedule schedule = repository.cached(repository.zone(), now);
        PrayerSchedule.Next next = schedule == null ? null : schedule.next(now);
        if (next != null && next.instant.isBefore(trigger)) trigger = next.instant;
        // Inexact alarm: no special alarm permission or constant foreground process.
        context.getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                trigger.plusSeconds(1).toEpochMilli(), alarm(context));
    }
    public static void cancel(Context context) {
        context.getSystemService(AlarmManager.class).cancel(alarm(context));
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        jobs.cancel(PERIODIC_JOB);
        jobs.cancel(SYNC_JOB);
    }
    private static PendingIntent alarm(Context context) {
        return PendingIntent.getBroadcast(context, 401, new Intent(context, RefreshReceiver.class).setAction(BOUNDARY),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static int widgetCount(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, PrayerWidget.class)).length;
    }
}
