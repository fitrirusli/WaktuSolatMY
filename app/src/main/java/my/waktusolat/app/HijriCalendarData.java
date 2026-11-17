package my.waktusolat.app;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** Calendar cells retain only the Gregorian/Hijri pairs actually returned by JAKIM. */
public final class HijriCalendarData {
    public final List<Month> months;
    private final Map<LocalDate, Entry> dates;

    public HijriCalendarData(PrayerSchedule schedule) {
        TreeMap<Integer, ArrayList<Entry>> groups = new TreeMap<>();
        Map<LocalDate, Entry> dates = new HashMap<>();
        if (schedule != null) for (PrayerSchedule.Day day : schedule.days.values()) {
            HijriDate hijri = HijriDate.available(day.hijri);
            if (hijri == null) continue;
            Entry entry = new Entry(day.date, hijri);
            dates.put(day.date, entry);
            groups.computeIfAbsent(hijri.monthKey(), key -> new ArrayList<>()).add(entry);
        }
        ArrayList<Month> months = new ArrayList<>();
        for (ArrayList<Entry> entries : groups.values()) {
            Entry first = entries.get(0), last = entries.get(entries.size() - 1);
            Entry following = dates.get(last.gregorian.plusDays(1));
            boolean hasLastDay = last.hijri.day == 30 || (following != null && following.hijri.monthKey() == last.hijri.monthKey() + 1 && following.hijri.day == 1);
            months.add(new Month(entries, first.hijri.day != 1 || !hasLastDay));
        }
        this.months = Collections.unmodifiableList(months);
        this.dates = Collections.unmodifiableMap(dates);
    }
    public Entry find(LocalDate date) { return dates.get(date); }
    public int monthContaining(LocalDate date) {
        Entry entry = find(date);
        if (entry != null) for (int i = 0; i < months.size(); i++) if (months.get(i).monthKey == entry.hijri.monthKey()) return i;
        return -1;
    }
    public static final class Entry {
        public final LocalDate gregorian;
        public final HijriDate hijri;
        private Entry(LocalDate gregorian, HijriDate hijri) { this.gregorian = gregorian; this.hijri = hijri; }
    }
    public static final class Month {
        public final int monthKey;
        public final String label;
        public final List<Entry> entries, cells;
        public final boolean partial;
        private Month(ArrayList<Entry> entries, boolean partial) {
            this.monthKey = entries.get(0).hijri.monthKey();
            this.label = entries.get(0).hijri.monthLabel();
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.partial = partial;
            LocalDate start = entries.get(0).gregorian.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDate end = entries.get(entries.size() - 1).gregorian.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
            Map<LocalDate, Entry> index = new HashMap<>();
            for (Entry entry : entries) index.put(entry.gregorian, entry);
            ArrayList<Entry> cells = new ArrayList<>();
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) cells.add(index.get(date));
            this.cells = Collections.unmodifiableList(cells);
        }
    }
}
