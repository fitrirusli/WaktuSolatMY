package my.waktusolat.app;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@LooperMode(LooperMode.Mode.PAUSED)
public class AndroidComponentsTest {
    private Context app;
    private PrayerSchedule schedule;

    @Before public void seedOfflineCache() throws Exception {
        app = RuntimeEnvironment.getApplication();
        String json;
        try (InputStream input = getClass().getResourceAsStream("/jakim-wly01-year.json")) {
            json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        schedule = PrayerSchedule.parse(json, "WLY01");
        Instant now = Instant.now();
        app.getSharedPreferences("waktu_solat", Context.MODE_PRIVATE).edit().clear()
                .putString("zone", "WLY01").putString("schedule_WLY01_" + now.atZone(PrayerSchedule.MALAYSIA).getYear(), json)
                .putLong("schedule_WLY01_" + now.atZone(PrayerSchedule.MALAYSIA).getYear() + "_updated", now.toEpochMilli()).commit();
    }
    @Test public void activityDisplaysOfficialCachedTimesAndSurvivesRecreation() {
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            List<String> labels = labels(controller.get().getWindow().getDecorView());
            assertTrue(labels.contains("Kuala Lumpur, Putrajaya"));
            assertTrue(labels.contains("© 2026 fitrirusli"));
            PrayerSchedule.Day today = schedule.today(Instant.now());
            assertNotNull("Refresh fixture if the calendar year has changed", today);
            assertEquals(HijriDate.parse(today.hijri).label(), ((TextView) controller.get().findViewById(R.id.hijri_today_text)).getText().toString());
            for (int i = 0; i < PrayerSchedule.KEYS.length; i++) assertTrue(labels.contains(PrayerSchedule.time(today.time(i))));
            controller.recreate();
            assertTrue(labels(controller.get().getWindow().getDecorView()).contains("Waktu solat"));
        }
    }
    @Test public void nativeRemoteViewsRenderDailyTimesAndCorrectNextPrayer() {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ShadowAppWidgetManager shadow = Shadows.shadowOf(manager);
        int widget = shadow.createWidget(PrayerWidget.class, R.layout.prayer_widget);
        View view = shadow.getViewFor(widget);
        assertEquals("KL · Putrajaya", ((TextView) view.findViewById(R.id.widget_zone)).getText().toString());
        assertEquals("© fitrirusli", ((TextView) view.findViewById(R.id.widget_copyright)).getText().toString());
        assertEquals(HijriDate.parse(schedule.today(Instant.now()).hijri).label(), ((TextView) view.findViewById(R.id.widget_date)).getText().toString());
        assertEquals("Subuh\n" + PrayerSchedule.time(schedule.today(Instant.now()).time(1)), ((TextView) view.findViewById(R.id.widget_subuh)).getText().toString());
        PrayerSchedule.Next next = schedule.next(Instant.now());
        assertNotNull(next);
        assertTrue(((TextView) view.findViewById(R.id.widget_next)).getText().toString().startsWith(next.name + "  " + next.time()));
        float density = app.getResources().getDisplayMetrics().density;
        int width = Math.round(250 * density), height = Math.round(200 * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        assertTrue("Timetable bottom=" + view.findViewById(R.id.widget_times).getBottom() + ", available=" + (height - view.getPaddingBottom()) + ", density=" + density + ", labels=" + labels(view), view.findViewById(R.id.widget_times).getBottom() <= height - view.getPaddingBottom());
        new PrayerWidget().onDisabled(app);
    }
    @Test public void zonePickerSearchesOfficialDistricts() {
        assertEquals(60, new Zones(app).all.size());
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            View picker = findDescription(controller.get().getWindow().getDecorView(), "Tukar zon waktu solat");
            assertNotNull(picker); picker.performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            EditText search = findType(dialog.getWindow().getDecorView(), EditText.class);
            ListView list = findType(dialog.getWindow().getDecorView(), ListView.class);
            search.setText("Selangor"); assertEquals(3, list.getAdapter().getCount());
            search.setText("Klang"); assertEquals(1, list.getAdapter().getCount());
            assertTrue(list.getAdapter().getItem(0).toString().contains("SGR03"));
            dialog.dismiss();
        }
    }
    @Test public void widgetResizesFromTallToCompactToOneLineAndBack() {
        AppWidgetManager manager = AppWidgetManager.getInstance(app);
        ShadowAppWidgetManager shadow = Shadows.shadowOf(manager);
        int widget = shadow.createWidget(PrayerWidget.class, R.layout.prayer_widget);
        PrayerWidget provider = new PrayerWidget();
        int[] heights = {400, 200, 176, 110, 40, 200, 400};
        for (int heightDp : heights) {
            Bundle options = new Bundle();
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp);
            manager.updateAppWidgetOptions(widget, options);
            provider.onAppWidgetOptionsChanged(app, manager, widget, options);
            View view = shadow.getViewFor(widget);
            float density = app.getResources().getDisplayMetrics().density;
            int width = Math.round(250 * density), height = Math.round(heightDp * density);
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
            view.layout(0, 0, width, height);
            View card = view.findViewById(R.id.widget_root);
            View title = view.findViewById(R.id.widget_next);
            View times = view.findViewById(R.id.widget_times);
            assertTrue("Card fits at " + heightDp + "dp", card.getBottom() <= height);
            assertTrue("Title fits at " + heightDp + "dp", title.getBottom() <= card.getHeight() - card.getPaddingBottom());
            assertEquals(heightDp >= 200 ? View.VISIBLE : View.GONE, view.findViewById(R.id.widget_date).getVisibility());
            assertEquals(heightDp < 108 ? View.GONE : View.VISIBLE, times.getVisibility());
            if (heightDp >= 108) {
                assertTrue("All five prayers fit at " + heightDp + "dp: bottom=" + times.getBottom() + ", available=" + (card.getHeight() - card.getPaddingBottom()), times.getBottom() <= card.getHeight() - card.getPaddingBottom());
            }
            if (heightDp == 400) {
                assertEquals("Green background ends just after the timetable", times.getBottom() + card.getPaddingBottom(), card.getHeight());
                assertTrue("Card is shorter than the tall host", card.getHeight() < height);
            }
        }
        provider.onDisabled(app);
    }
    @Test public void hijriCalendarSelectsDatesNavigatesMonthsAndReturnsToToday() {
        LocalDate today = Instant.now().atZone(PrayerSchedule.MALAYSIA).toLocalDate();
        HijriCalendarData data = new HijriCalendarData(schedule);
        HijriCalendarData.Month month = data.months.get(data.monthContaining(today));
        Locale malay = Locale.forLanguageTag("ms-MY");
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            findDescription(controller.get().getWindow().getDecorView(), "Buka kalendar Hijrah").performClick();
            AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
            assertTrue(dialog.isShowing());
            assertEquals(month.label, ((TextView) dialog.findViewById(R.id.calendar_month_title)).getText().toString());
            assertEquals(data.find(today).hijri.label(), ((TextView) dialog.findViewById(R.id.calendar_selected_hijri)).getText().toString());
            HijriCalendarData.Entry target = month.entries.get(0);
            if (target.gregorian.equals(today)) target = month.entries.get(month.entries.size() - 1);
            String description = target.hijri.label() + " · " + target.gregorian.format(DateTimeFormatter.ofPattern("d MMMM yyyy", malay));
            View cell = findDescription(dialog.getWindow().getDecorView(), description);
            assertNotNull(cell); cell.performClick();
            assertEquals(target.hijri.label(), ((TextView) dialog.findViewById(R.id.calendar_selected_hijri)).getText().toString());
            assertEquals(target.gregorian.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", malay)), ((TextView) dialog.findViewById(R.id.calendar_selected_gregorian)).getText().toString());
            View next = findDescription(dialog.getWindow().getDecorView(), "Bulan Hijrah seterusnya");
            assertTrue(next.isEnabled()); next.performClick();
            assertNotEquals(month.label, ((TextView) dialog.findViewById(R.id.calendar_month_title)).getText().toString());
            findDescription(dialog.getWindow().getDecorView(), "Hari ini").performClick();
            assertEquals(month.label, ((TextView) dialog.findViewById(R.id.calendar_month_title)).getText().toString());
            assertEquals(data.find(today).hijri.label(), ((TextView) dialog.findViewById(R.id.calendar_selected_hijri)).getText().toString());
            dialog.dismiss();
        }
    }
    @Test public void hijriCalendarHandlesUnavailableDatesAndBothDownloadedYearBoundaries() {
        LocalDate today = Instant.now().atZone(PrayerSchedule.MALAYSIA).toLocalDate();
        try (ActivityController<MainActivity> controller = Robolectric.buildActivity(MainActivity.class).setup()) {
            AlertDialog empty = HijriCalendarDialog.show(controller.get(), null, today);
            assertTrue(labels(empty.getWindow().getDecorView()).contains(app.getString(R.string.calendar_empty)));
            assertFalse(findDescription(empty.getWindow().getDecorView(), "Bulan Hijrah sebelumnya").isEnabled());
            assertFalse(findDescription(empty.getWindow().getDecorView(), "Bulan Hijrah seterusnya").isEnabled()); empty.dismiss();
            AlertDialog dialog = HijriCalendarDialog.show(controller.get(), schedule, schedule.days.firstKey());
            assertFalse(findDescription(dialog.getWindow().getDecorView(), "Bulan Hijrah sebelumnya").isEnabled());
            int months = new HijriCalendarData(schedule).months.size();
            for (int i = 1; i < months; i++) findDescription(dialog.getWindow().getDecorView(), "Bulan Hijrah seterusnya").performClick();
            assertFalse(findDescription(dialog.getWindow().getDecorView(), "Bulan Hijrah seterusnya").isEnabled());
            assertTrue(labels(dialog.getWindow().getDecorView()).stream().anyMatch(label -> label.contains(app.getString(R.string.calendar_partial))));
            dialog.dismiss();
        }
    }
    @Test public void newYearRequiresFreshDataInsteadOfReusingThePreviousYear() {
        PrayerRepository repository = new PrayerRepository(app);
        Instant now = Instant.now();
        Instant nextYear = LocalDate.of(now.atZone(PrayerSchedule.MALAYSIA).getYear() + 1, 1, 1).atStartOfDay(PrayerSchedule.MALAYSIA).toInstant();
        assertNotNull(repository.cached("WLY01", now));
        assertNull(repository.cached("WLY01", nextYear));
        assertTrue(repository.needsRefresh("WLY01", nextYear));
    }
    private List<String> labels(View view) {
        List<String> result = new ArrayList<>();
        if (view instanceof TextView text) result.add(text.getText().toString());
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) result.addAll(labels(group.getChildAt(i)));
        return result;
    }
    private View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = findDescription(group.getChildAt(i), description); if (found != null) return found;
        }
        return null;
    }
    private <T> T findType(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            T found = findType(group.getChildAt(i), type); if (found != null) return found;
        }
        return null;
    }
}
