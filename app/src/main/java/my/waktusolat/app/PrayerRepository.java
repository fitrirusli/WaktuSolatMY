package my.waktusolat.app;

import android.content.*;
import java.io.*;
import java.time.*;
import java.util.concurrent.*;

public final class PrayerRepository {
    public static final String SOURCE = "https://www.e-solat.gov.my/";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final long MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;
    private final SharedPreferences preferences;
    private final Context context;

    public PrayerRepository(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences("waktu_solat", Context.MODE_PRIVATE);
    }
    public String zone() { return preferences.getString("zone", "WLY01"); }
    public void setZone(String zone) {
        if (!zone.matches("[A-Z]{3}[0-9]{2}")) throw new IllegalArgumentException("Kod zon tidak sah");
        preferences.edit().putString("zone", zone).apply();
    }
    private String key(String zone, int year) { return "schedule_" + zone + "_" + year; }
    public PrayerSchedule cached(String zone, Instant now) {
        String json = preferences.getString(key(zone, now.atZone(PrayerSchedule.MALAYSIA).getYear()), null);
        if (json == null) return null;
        try { return PrayerSchedule.parse(json, zone); }
        catch (Exception error) { return null; }
    }
    public long updatedAt(String zone, Instant now) {
        return preferences.getLong(key(zone, now.atZone(PrayerSchedule.MALAYSIA).getYear()) + "_updated", 0);
    }
    public boolean needsRefresh(String zone, Instant now) {
        PrayerSchedule cache = cached(zone, now);
        return cache == null || cache.today(now) == null || now.toEpochMilli() - updatedAt(zone, now) > MAX_AGE_MS;
    }
    public void refresh(String zone, boolean force, Callback callback) {
        IO.execute(() -> {
            try {
                Instant now = Instant.now();
                if (!force && !needsRefresh(zone, now)) {
                    callback.done(cached(zone, now), null);
                    return;
                }
                String json = new JakimClient().downloadYear(zone);
                PrayerSchedule schedule = PrayerSchedule.parse(json, zone);
                if (schedule.today(now) == null) throw new IOException("JAKIM belum menyediakan jadual untuk tarikh peranti ini. Semak tarikh peranti.");
                int year = now.atZone(PrayerSchedule.MALAYSIA).getYear();
                boolean saved = preferences.edit().putString(key(zone, year), json)
                        .putLong(key(zone, year) + "_updated", now.toEpochMilli()).commit();
                if (!saved) throw new IOException("Jadual tidak dapat disimpan pada peranti.");
                callback.done(schedule, null);
            } catch (Exception error) {
                callback.done(cached(zone, Instant.now()), error);
            }
        });
    }
    public interface Callback { void done(PrayerSchedule schedule, Exception error); }
}
