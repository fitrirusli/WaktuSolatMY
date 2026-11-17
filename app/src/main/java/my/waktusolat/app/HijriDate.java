package my.waktusolat.app;

import java.util.Locale;

/** A Hijri date supplied by JAKIM; no calculated conversion or day adjustment. */
public final class HijriDate {
    private static final String[] MONTHS = {"Muharam", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal", "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijah"};
    public final int year, month, day;

    private HijriDate(int year, int month, int day) { this.year = year; this.month = month; this.day = day; }

    public static HijriDate parse(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) throw new IllegalArgumentException("Tarikh Hijrah tidak sah.");
        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(5, 7));
        int day = Integer.parseInt(value.substring(8, 10));
        if (year < 1 || month < 1 || month > 12 || day < 1 || day > 30) throw new IllegalArgumentException("Tarikh Hijrah tidak sah.");
        return new HijriDate(year, month, day);
    }
    public static HijriDate available(String value) {
        try { return parse(value); } catch (IllegalArgumentException error) { return null; }
    }
    public String label() { return day + " " + monthLabel(); }
    public String monthLabel() { return MONTHS[month - 1] + " " + year + "H"; }
    public int monthKey() { return year * 12 + month - 1; }
    @Override public String toString() { return String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day); }
}
