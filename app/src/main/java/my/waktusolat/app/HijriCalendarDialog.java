package my.waktusolat.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class HijriCalendarDialog {
    private static final int INK = 0xFF193E34, MUTED = 0xFF6B786F, LIME = 0xFFDAEFAD;
    private static final Locale MALAY = Locale.forLanguageTag("ms-MY");
    private final Context context;
    private final HijriCalendarData data;
    private final LocalDate today;
    private LocalDate selected;
    private int monthIndex;
    private final TextView heading, selectedHijri, selectedGregorian, note;
    private final LinearLayout grid;
    private final Button previous, next, todayButton;

    static AlertDialog show(Context context, PrayerSchedule schedule, LocalDate today) {
        return new HijriCalendarDialog(context, schedule, today).showDialog();
    }

    private final LinearLayout panel;
    private HijriCalendarDialog(Context context, PrayerSchedule schedule, LocalDate today) {
        this.context = context; this.today = today; this.data = new HijriCalendarData(schedule);
        this.monthIndex = Math.max(0, data.monthContaining(today));
        this.selected = data.find(today) != null ? today : data.months.isEmpty() ? null : data.months.get(0).entries.get(0).gregorian;
        panel = column(); panel.setPadding(dp(16), dp(12), dp(16), dp(8));
        LinearLayout top = row();
        top.addView(text(context.getString(R.string.hijri_calendar), 21, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        todayButton = control(context.getString(R.string.calendar_today), 12);
        todayButton.setContentDescription(context.getString(R.string.calendar_today));
        top.addView(todayButton, new LinearLayout.LayoutParams(-2, dp(44))); panel.addView(top);
        todayButton.setOnClickListener(view -> { int index = data.monthContaining(today); if (index >= 0) { monthIndex = index; selected = today; render(); } });

        LinearLayout navigation = row();
        previous = control("‹", 26); previous.setContentDescription(context.getString(R.string.calendar_previous));
        next = control("›", 26); next.setContentDescription(context.getString(R.string.calendar_next));
        heading = text("", 17, INK, true); heading.setGravity(Gravity.CENTER); heading.setId(R.id.calendar_month_title);
        navigation.addView(previous, new LinearLayout.LayoutParams(dp(44), dp(44)));
        navigation.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        navigation.addView(next, new LinearLayout.LayoutParams(dp(44), dp(44))); add(panel, navigation, 12);
        previous.setOnClickListener(view -> move(-1)); next.setOnClickListener(view -> move(1));
        TextView legend = text(context.getString(R.string.calendar_legend), 11, MUTED, false); legend.setGravity(Gravity.CENTER); add(panel, legend, 3);
        LinearLayout weekdays = row();
        for (String day : new String[]{"Isn", "Sel", "Rab", "Kha", "Jum", "Sab", "Ahd"}) {
            TextView label = text(day, 11, MUTED, true); label.setGravity(Gravity.CENTER);
            weekdays.addView(label, new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        add(panel, weekdays, 12);
        grid = column(); grid.setId(R.id.calendar_grid); panel.addView(grid);
        LinearLayout details = column(); details.setPadding(dp(14), dp(14), dp(14), dp(14)); details.setBackground(background(0xFFECF3DD, 16, 0));
        selectedHijri = text("", 17, INK, true); selectedHijri.setId(R.id.calendar_selected_hijri); details.addView(selectedHijri);
        selectedGregorian = text("", 13, MUTED, false); selectedGregorian.setId(R.id.calendar_selected_gregorian); add(details, selectedGregorian, 6);
        add(panel, details, 12);
        note = text("", 11, MUTED, false); note.setLineSpacing(dp(3), 1); add(panel, note, 12);
        render();
    }

    private AlertDialog showDialog() {
        ScrollView scroll = new ScrollView(context); scroll.addView(panel);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(scroll).setNegativeButton(R.string.calendar_close, null).create();
        dialog.show();
        dialog.getWindow().setLayout(Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(24), dp(620)), -2);
        return dialog;
    }
    private void move(int delta) {
        int target = monthIndex + delta;
        if (target < 0 || target >= data.months.size()) return;
        monthIndex = target; selected = data.months.get(target).entries.get(0).gregorian; render();
    }
    private void render() {
        previous.setEnabled(!data.months.isEmpty() && monthIndex > 0);
        next.setEnabled(monthIndex + 1 < data.months.size());
        todayButton.setEnabled(data.find(today) != null);
        grid.removeAllViews();
        if (data.months.isEmpty()) {
            heading.setText(R.string.hijri_unavailable); selectedHijri.setText(R.string.hijri_unavailable); selectedGregorian.setText(""); note.setText(R.string.calendar_empty); return;
        }
        HijriCalendarData.Month month = data.months.get(monthIndex);
        heading.setText(month.label);
        int cellHeight = dp(Math.max(56, Math.round(48 * context.getResources().getConfiguration().fontScale)));
        LinearLayout week = null;
        for (int i = 0; i < month.cells.size(); i++) {
            if (i % 7 == 0) { week = row(); grid.addView(week); }
            HijriCalendarData.Entry entry = month.cells.get(i);
            if (entry == null) { week.addView(new Space(context), new LinearLayout.LayoutParams(0, cellHeight, 1)); continue; }
            boolean chosen = entry.gregorian.equals(selected);
            LinearLayout cell = column(); cell.setGravity(Gravity.CENTER); cell.setPadding(dp(1), dp(5), dp(1), dp(5));
            cell.setBackground(background(chosen ? INK : Color.WHITE, 12, entry.gregorian.equals(today) && !chosen ? 0xFF517552 : 0));
            cell.addView(text(Integer.toString(entry.hijri.day), 17, chosen ? Color.WHITE : INK, true));
            TextView gregorian = text(entry.gregorian.format(DateTimeFormatter.ofPattern("d MMM", MALAY)), 10, chosen ? LIME : MUTED, false);
            gregorian.setMaxLines(1); gregorian.setEllipsize(android.text.TextUtils.TruncateAt.END);
            add(cell, gregorian, 4);
            cell.setContentDescription(entry.hijri.label() + " · " + entry.gregorian.format(DateTimeFormatter.ofPattern("d MMMM yyyy", MALAY)));
            cell.setFocusable(true); cell.setClickable(true); cell.setOnClickListener(view -> { selected = entry.gregorian; render(); });
            week.addView(cell, new LinearLayout.LayoutParams(0, cellHeight, 1));
        }
        HijriCalendarData.Entry entry = data.find(selected);
        selectedHijri.setText(entry.hijri.label());
        selectedGregorian.setText(entry.gregorian.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", MALAY)));
        note.setText(context.getString(R.string.calendar_source) + (month.partial ? "\n" + context.getString(R.string.calendar_partial) : ""));
    }
    private Button control(String label, int size) {
        Button button = new Button(context); button.setText(label); button.setTextSize(size); button.setTextColor(INK); button.setAllCaps(false); button.setMinWidth(0); button.setMinimumWidth(0); button.setPadding(dp(8), 0, dp(8), 0); return button;
    }
    private TextView text(String label, int size, int color, boolean medium) {
        TextView text = new TextView(context); text.setText(label); text.setTextSize(size); text.setTextColor(color); text.setIncludeFontPadding(false);
        text.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); return text;
    }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(context); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(context); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private void add(LinearLayout parent, View child, int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(margin); parent.addView(child, params);
    }
    private GradientDrawable background(int color, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable(); background.setColor(color); background.setCornerRadius(dp(radius)); if (stroke != 0) background.setStroke(dp(1), stroke); return background;
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
}
