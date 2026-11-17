package my.waktusolat.app;

import android.content.*;
import java.time.Instant;

public final class RefreshReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (UpdateScheduler.widgetCount(context) == 0) return;
        PrayerWidget.updateAll(context);
        UpdateScheduler.schedule(context);
        PrayerRepository repository = new PrayerRepository(context);
        boolean force = UpdateScheduler.REFRESH.equals(intent.getAction());
        if (force || repository.needsRefresh(repository.zone(), Instant.now())) UpdateScheduler.sync(context, force);
    }
}
