package my.waktusolat.app;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PrayerWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        updateAll(context);
        UpdateScheduler.schedule(context);
        PrayerRepository repository = new PrayerRepository(context);
        if (repository.needsRefresh(repository.zone(), Instant.now())) UpdateScheduler.sync(context, false);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        updateAll(context);
    }
    @Override public void onDisabled(Context context) { UpdateScheduler.cancel(context); }
    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, PrayerWidget.class));
        if (ids.length == 0) return;
        PrayerRepository repository = new PrayerRepository(context);
        Instant now = Instant.now();
        String code = repository.zone();
        Zones.Zone zone = new Zones(context).find(code);
        PrayerSchedule schedule = repository.cached(code, now);
        PrayerSchedule.Day day = schedule == null ? null : schedule.today(now);
        PrayerSchedule.Next next = schedule == null ? null : schedule.next(now);
        for (int id : ids) {
            Bundle options = manager.getAppWidgetOptions(id);
            int landscapeHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110);
            if (landscapeHeight <= 0) landscapeHeight = 110;
            int portraitHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, landscapeHeight);
            if (portraitHeight <= 0) portraitHeight = landscapeHeight;
            // The host selects the right orientation, including after a device rotation.
            RemoteViews views = new RemoteViews(
                    createViews(context, code, zone, day, next, now, landscapeHeight),
                    createViews(context, code, zone, day, next, now, portraitHeight));
            manager.updateAppWidget(id, views);
        }
    }

    private static RemoteViews createViews(Context context, String code, Zones.Zone zone,
                                          PrayerSchedule.Day day, PrayerSchedule.Next next,
                                          Instant now, int height) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.prayer_widget);
        float fontScale = Math.max(1f, context.getResources().getConfiguration().fontScale);
        boolean compact = height < Math.ceil(62 + 138 * fontScale);
        boolean strip = height < Math.ceil(24 + 84 * fontScale);
        views.setViewVisibility(R.id.widget_header, strip ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_times, strip ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_date, compact ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_countdown, compact ? View.GONE : View.VISIBLE);
        float titleSize = strip ? Math.min(18f, 22f / fontScale) : compact ? 20f : 22f;
        views.setTextViewTextSize(R.id.widget_next, TypedValue.COMPLEX_UNIT_SP, titleSize);
        int horizontal = dp(context, compact ? 14 : 18);
        int vertical = dp(context, strip ? 6 : compact ? 8 : 12);
        views.setViewPadding(R.id.widget_root, horizontal, vertical, horizontal, vertical);
        views.setTextViewText(R.id.widget_zone, code.equals("WLY01") ? "KL · Putrajaya" : zone.state + " · " + code);
        HijriDate hijri = day == null ? null : HijriDate.available(day.hijri);
        views.setTextViewText(R.id.widget_date, hijri == null ? context.getString(R.string.hijri_unavailable) : hijri.label());
        String gregorianDate = now.atZone(PrayerSchedule.MALAYSIA).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.forLanguageTag("ms-MY")));
        if (next != null && day != null) {
            views.setTextViewText(R.id.widget_next, next.name + "  " + next.time() + (next.tomorrow ? " · esok" : ""));
            views.setTextViewText(R.id.widget_countdown, gregorianDate + " · JAKIM");
        } else {
            views.setTextViewText(R.id.widget_next, day == null ? "Jadual belum tersedia" : "Selesai waktu hari ini");
            views.setTextViewText(R.id.widget_countdown, day == null ? "Buka app atau ketik ↻ untuk memuatkan" : "Jadual esok belum tersedia · ketik ↻");
        }
        int[] timeIds = {R.id.widget_subuh, R.id.widget_zohor, R.id.widget_asar, R.id.widget_maghrib, R.id.widget_isyak};
        int[] indexes = {1, 3, 4, 5, 6};
        for (int i = 0; i < indexes.length; i++) {
            int p = indexes[i];
            views.setTextViewText(timeIds[i], PrayerSchedule.LABELS[p] + "\n" + (day == null ? "—" : PrayerSchedule.time(day.time(p))));
            views.setTextColor(timeIds[i], next != null && !next.tomorrow && next.index == p ? 0xFFDAEFAD : 0xFFFFFFFF);
        }
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent refresh = PendingIntent.getBroadcast(context, 1, new Intent(context, RefreshReceiver.class).setAction(UpdateScheduler.REFRESH), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, open);
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh);
        return views;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
