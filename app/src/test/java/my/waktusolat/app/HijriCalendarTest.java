package my.waktusolat.app;

import org.junit.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import static org.junit.Assert.*;

public class HijriCalendarTest {
    private String fixture;
    private PrayerSchedule schedule;
    private HijriCalendarData calendar;
    @Before public void setup() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/jakim-wly01-year.json")) {
            fixture = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        schedule = PrayerSchedule.parse(fixture, "WLY01"); calendar = new HijriCalendarData(schedule);
    }
    @Test public void preservesAllOfficialGregorianHijriPairs() {
        int total = 0;
        for (HijriCalendarData.Month month : calendar.months) total += month.entries.size();
        assertEquals(365, total);
        for (PrayerSchedule.Day day : schedule.days.values()) {
            assertEquals(day.date, calendar.find(day.date).gregorian);
            assertEquals(day.hijri, calendar.find(day.date).hijri.toString());
        }
    }
    @Test public void calendarCellsAlignWithTheActualWeekdays() {
        for (HijriCalendarData.Month month : calendar.months) {
            assertEquals(0, month.cells.size() % 7);
            int shown = 0;
            for (int i = 0; i < month.cells.size(); i++) {
                HijriCalendarData.Entry entry = month.cells.get(i);
                if (entry == null) continue;
                shown++;
                assertEquals(entry.gregorian.getDayOfWeek().getValue(), i % 7 + 1);
            }
            assertEquals(month.entries.size(), shown);
        }
    }
    @Test public void displaysMalayHijriNamesFromTheOfficialDates() {
        assertEquals("6 Jamadilakhir 1448H", calendar.find(LocalDate.of(2026, 11, 16)).hijri.label());
        assertEquals("11 Rejab 1447H", calendar.find(LocalDate.of(2026, 1, 1)).hijri.label());
    }
    @Test public void partialMonthsNeverInventDatesOutsideDownloadedData() {
        HijriCalendarData.Month first = calendar.months.get(0), last = calendar.months.get(calendar.months.size() - 1);
        assertTrue(first.partial); assertTrue(last.partial);
        assertEquals(11, first.entries.get(0).hijri.day);
        assertEquals(21, last.entries.get(last.entries.size() - 1).hijri.day);
        assertNull(calendar.find(LocalDate.of(2025, 12, 31)));
        assertNull(calendar.find(LocalDate.of(2027, 1, 1)));
        assertEquals(-1, calendar.monthContaining(LocalDate.of(2027, 1, 1)));
    }
    @Test public void handlesActualTwentyNineAndThirtyDayMonthsAcrossHijriNewYear() {
        HijriCalendarData.Month ramadan = calendar.months.stream().filter(month -> month.label.equals("Ramadan 1447H")).findFirst().get();
        HijriCalendarData.Month muharam = calendar.months.stream().filter(month -> month.label.equals("Muharam 1448H")).findFirst().get();
        assertEquals(30, ramadan.entries.size()); assertFalse(ramadan.partial);
        assertEquals(29, muharam.entries.size()); assertFalse(muharam.partial);
        int previous = -1;
        for (HijriCalendarData.Month month : calendar.months) { assertTrue(month.monthKey > previous); previous = month.monthKey; }
    }
    @Test public void unavailableDataHasNoCalculatedCalendar() {
        HijriCalendarData empty = new HijriCalendarData(null);
        assertTrue(empty.months.isEmpty()); assertNull(empty.find(LocalDate.of(2026, 11, 16)));
    }
    @Test public void missingAndInvalidHijriFieldsDoNotBreakPrayerTimesOrCreateCalendarDates() throws Exception {
        JSONObject data = new JSONObject(fixture);
        data.getJSONArray("prayerTime").getJSONObject(0).put("hijri", "1447-13-40");
        data.getJSONArray("prayerTime").getJSONObject(1).remove("hijri");
        PrayerSchedule parsed = PrayerSchedule.parse(data.toString(), "WLY01");
        assertEquals(365, parsed.days.size());
        HijriCalendarData calendar = new HijriCalendarData(parsed);
        assertNull(calendar.find(LocalDate.of(2026, 1, 1))); assertNull(calendar.find(LocalDate.of(2026, 1, 2)));
        assertNotNull(calendar.find(LocalDate.of(2026, 1, 3)));
    }
    @Test public void rejectsMalformedHijriDatesWithoutGuessingAMonthLength() {
        String[] invalid = {null, "", "0000-01-01", "1448-00-01", "1448-13-01", "1448-01-00", "1448-01-31", "1448-1-2", "abc"};
        for (String value : invalid) { assertNull(HijriDate.available(value)); assertThrows(IllegalArgumentException.class, () -> HijriDate.parse(value)); }
        assertEquals(29, HijriDate.parse("1448-01-29").day);
        assertEquals(30, HijriDate.parse("1448-03-30").day);
    }
}
