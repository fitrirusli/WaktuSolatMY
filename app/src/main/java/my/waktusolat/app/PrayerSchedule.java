package my.waktusolat.app;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** JAKIM values are preserved as local Malaysian times, without calculation or offsets. */
public final class PrayerSchedule {
    public static final ZoneId MALAYSIA = ZoneId.of("Asia/Kuala_Lumpur");
    public static final String[] KEYS = {"imsak", "fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
    public static final String[] LABELS = {"Imsak", "Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final int[] OBLIGATORY = {1, 3, 4, 5, 6};
    private static final Map<String, Integer> MONTHS = new HashMap<>();
    static {
        String[] aliases = {"jan", "feb", "mac,mar", "apr", "mei,may", "jun", "jul", "ogos,aug", "sep,sept", "okt,oct", "nov", "dis,dec"};
        for (int i = 0; i < aliases.length; i++) for (String alias : aliases[i].split(",")) MONTHS.put(alias, i + 1);
    }
    public final String zone;
    public final NavigableMap<LocalDate, Day> days;

    private PrayerSchedule(String zone, NavigableMap<LocalDate, Day> days) {
        this.zone = zone;
        this.days = Collections.unmodifiableNavigableMap(days);
    }

    public static PrayerSchedule parse(String json, String expectedZone) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"OK!".equals(root.optString("status")) || !expectedZone.equals(root.optString("zone"))) {
            throw new IllegalArgumentException("Jawapan JAKIM tidak sepadan dengan zon pilihan.");
        }
        JSONArray rows = root.getJSONArray("prayerTime");
        if (rows.length() == 0) throw new IllegalArgumentException("Jadual JAKIM kosong.");
        NavigableMap<LocalDate, Day> days = new TreeMap<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            LocalDate date = parseDate(row.getString("date"));
            LocalTime[] times = new LocalTime[KEYS.length];
            for (int p = 0; p < KEYS.length; p++) times[p] = LocalTime.parse(row.getString(KEYS[p]));
            for (int p = 1; p < times.length; p++) {
                if (!times[p].isAfter(times[p - 1])) throw new IllegalArgumentException("Susunan waktu tidak sah.");
            }
            if (days.put(date, new Day(date, row.optString("hijri"), times)) != null) {
                throw new IllegalArgumentException("Tarikh berganda dalam jadual.");
            }
        }
        return new PrayerSchedule(expectedZone, days);
    }

    private static LocalDate parseDate(String value) {
        // The official ms_my response uses Mac, Mei, Ogos, Okt and Dis.
        String[] parts = value.split("-", -1);
        if (parts.length != 3) throw new IllegalArgumentException("Format tarikh JAKIM tidak sah.");
        Integer month = MONTHS.get(parts[1].toLowerCase(Locale.ROOT));
        if (month == null) throw new IllegalArgumentException("Bulan JAKIM tidak dikenali.");
        return LocalDate.of(Integer.parseInt(parts[2]), month, Integer.parseInt(parts[0]));
    }

    public Day today(Instant now) {
        return days.get(now.atZone(MALAYSIA).toLocalDate());
    }

    /** Only today's and tomorrow's five obligatory prayers are candidates. */
    public Next next(Instant now) {
        LocalDate today = now.atZone(MALAYSIA).toLocalDate();
        for (int offset = 0; offset <= 1; offset++) {
            Day day = days.get(today.plusDays(offset));
            if (day == null) continue;
            for (int index : OBLIGATORY) {
                Instant instant = day.date.atTime(day.times[index]).atZone(MALAYSIA).toInstant();
                if (instant.isAfter(now)) return new Next(LABELS[index], index, instant, offset == 1);
            }
        }
        return null;
    }

    public static String time(LocalTime time) {
        return time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT));
    }

    public static String countdown(Instant now, Instant target) {
        long seconds = Math.max(0, Duration.between(now, target).getSeconds());
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60);
    }

    public static final class Day {
        public final LocalDate date;
        public final String hijri;
        private final LocalTime[] times;
        Day(LocalDate date, String hijri, LocalTime[] times) {
            this.date = date; this.hijri = hijri; this.times = times.clone();
        }
        public LocalTime time(int index) { return times[index]; }
    }

    public static final class Next {
        public final String name;
        public final int index;
        public final Instant instant;
        public final boolean tomorrow;
        Next(String name, int index, Instant instant, boolean tomorrow) {
            this.name = name; this.index = index; this.instant = instant; this.tomorrow = tomorrow;
        }
        public String time() { return PrayerSchedule.time(instant.atZone(MALAYSIA).toLocalTime()); }
    }
}
