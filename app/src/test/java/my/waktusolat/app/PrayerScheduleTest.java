package my.waktusolat.app;

import org.junit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.Assert.*;

public class PrayerScheduleTest {
    private static String live;
    private PrayerSchedule schedule;

    @BeforeClass public static void fixture() throws Exception {
        try (InputStream input = PrayerScheduleTest.class.getResourceAsStream("/jakim-wly01-year.json")) {
            assertNotNull(input);
            live = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
    @Before public void setup() throws Exception { schedule = PrayerSchedule.parse(live, "WLY01"); }
    private Instant at(String value) { return LocalDateTime.parse(value).atZone(PrayerSchedule.MALAYSIA).toInstant(); }
    private Instant at(LocalDate day, LocalTime time) { return day.atTime(time).atZone(PrayerSchedule.MALAYSIA).toInstant(); }

    @Test public void preservesEveryOfficialTimeAcrossTheYear() throws Exception {
        JSONArray source = new JSONObject(live).getJSONArray("prayerTime");
        assertEquals(365, schedule.days.size());
        int row = 0;
        for (PrayerSchedule.Day day : schedule.days.values()) {
            for (int i = 0; i < PrayerSchedule.KEYS.length; i++) {
                assertEquals(LocalTime.parse(source.getJSONObject(row).getString(PrayerSchedule.KEYS[i])), day.time(i));
            }
            row++;
        }
    }
    @Test public void nextBeforeSubuhIsSubuhNotImsak() {
        PrayerSchedule.Day day = schedule.days.firstEntry().getValue();
        assertEquals("Subuh", schedule.next(at(day.date, day.time(0))).name);
    }
    @Test public void syurukIsNotAnObligatoryPrayer() {
        PrayerSchedule.Day day = schedule.days.firstEntry().getValue();
        assertEquals("Zohor", schedule.next(at(day.date, day.time(1)).plusSeconds(1)).name);
        assertEquals("Zohor", schedule.next(at(day.date, day.time(2))).name);
    }
    @Test public void exactlyAtPrayerTimeAdvancesToNextPrayer() {
        PrayerSchedule.Day day = schedule.days.firstEntry().getValue();
        assertEquals("Asar", schedule.next(at(day.date, day.time(3))).name);
    }
    @Test public void afterIsyakUsesTomorrowsActualSubuh() {
        LocalDate date = LocalDate.of(2026, 11, 13);
        PrayerSchedule.Next next = schedule.next(at("2026-11-13T23:59:59"));
        assertEquals("Subuh", next.name); assertTrue(next.tomorrow);
        assertEquals(at(date.plusDays(1), schedule.days.get(date.plusDays(1)).time(1)), next.instant);
    }
    @Test public void midnightReadsNewDate() {
        PrayerSchedule.Day day = schedule.today(at("2026-11-14T00:00:00"));
        assertEquals(LocalDate.of(2026, 11, 14), day.date);
        assertFalse(schedule.next(at("2026-11-14T00:00:00")).tomorrow);
    }
    @Test public void ignoresDeviceTimezone() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            assertEquals(LocalDate.of(2026, 11, 14), schedule.today(Instant.parse("2026-11-13T16:01:00Z")).date);
        } finally { TimeZone.setDefault(original); }
    }
    @Test public void neverUsesExpiredYearOrInventsTomorrow() {
        assertNull(schedule.today(at("2027-01-01T01:00:00")));
        assertNull(schedule.next(at("2026-12-31T23:59:59")));
    }
    @Test public void countdownClampsNegativeAndHandlesHours() {
        Instant now = at("2026-11-13T10:00:00");
        assertEquals("01:02:03", PrayerSchedule.countdown(now, now.plusSeconds(3723)));
        assertEquals("00:00:00", PrayerSchedule.countdown(now, now.minusSeconds(1)));
    }
    @Test public void rejectsWrongZone() {
        assertThrows(IllegalArgumentException.class, () -> PrayerSchedule.parse(live, "SGR01"));
    }
    @Test public void rejectsApiErrorAndEmptyResults() throws Exception {
        JSONObject error = new JSONObject(live); error.put("status", "error");
        assertThrows(IllegalArgumentException.class, () -> PrayerSchedule.parse(error.toString(), "WLY01"));
        error.put("status", "OK!"); error.put("prayerTime", new JSONArray());
        assertThrows(IllegalArgumentException.class, () -> PrayerSchedule.parse(error.toString(), "WLY01"));
    }
    @Test public void rejectsBrokenOrDuplicateDates() throws Exception {
        JSONObject json = new JSONObject(live);
        JSONArray rows = json.getJSONArray("prayerTime"); rows.put(rows.getJSONObject(0));
        assertThrows(IllegalArgumentException.class, () -> PrayerSchedule.parse(json.toString(), "WLY01"));
        rows.remove(rows.length() - 1); rows.getJSONObject(0).put("date", "31-Feb-2026");
        assertThrows(Exception.class, () -> PrayerSchedule.parse(json.toString(), "WLY01"));
    }
    @Test public void rejectsMalformedAndOutOfOrderTimes() throws Exception {
        JSONObject json = new JSONObject(live);
        json.getJSONArray("prayerTime").getJSONObject(0).put("fajr", "25:00:00");
        assertThrows(Exception.class, () -> PrayerSchedule.parse(json.toString(), "WLY01"));
        json.getJSONArray("prayerTime").getJSONObject(0).put("fajr", "23:00:00");
        assertThrows(IllegalArgumentException.class, () -> PrayerSchedule.parse(json.toString(), "WLY01"));
    }
}
