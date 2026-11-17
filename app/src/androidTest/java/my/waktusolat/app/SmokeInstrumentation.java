package my.waktusolat.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Real-device smoke runner without test framework dependencies. Runs on a test emulator only. */
public final class SmokeInstrumentation extends Instrumentation {
    private int checks;
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        MainActivity activity = null;
        AppWidgetHost host = null;
        int widgetId = -1;
        try {
            Context target = getTargetContext();
            String fixture;
            try (InputStream stream = getContext().getAssets().open("jakim-wly01-year.json"); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int n;
                while ((n = stream.read(bytes)) != -1) buffer.write(bytes, 0, n);
                fixture = buffer.toString(StandardCharsets.UTF_8.name());
            }
            int year = Instant.now().atZone(PrayerSchedule.MALAYSIA).getYear();
            PrayerSchedule parsed = PrayerSchedule.parse(fixture, "WLY01");
            check(parsed.today(Instant.now()) != null, "Update the live fixture to the current year before smoke testing.");
            target.getSharedPreferences("waktu_solat", Context.MODE_PRIVATE).edit().clear()
                    .putString("zone", "WLY01").putString("schedule_WLY01_" + year, fixture)
                    .putLong("schedule_WLY01_" + year + "_updated", System.currentTimeMillis()).commit();
            PrayerRepository repository = new PrayerRepository(target);
            check(new Zones(target).all.size() == 60, "All 60 official zones are available.");
            check(repository.cached("WLY01", Instant.now()) != null, "Schedule survives a fresh repository instance.");
            check(repository.cached("SGR01", Instant.now()) == null, "Cache never crosses zones.");
            check(!repository.needsRefresh("WLY01", Instant.now()), "Fresh offline cache avoids a network request.");
            activity = (MainActivity) startActivitySync(new Intent(target, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync();
            MainActivity screen = activity;
            List<String> labels = new ArrayList<>();
            runOnMainSync(() -> collect(screen.getWindow().getDecorView(), labels));
            check(labels.contains("Waktu solat"), "App launches.");
            check(labels.contains("Kuala Lumpur, Putrajaya"), "KL/Putrajaya is the default zone.");
            for (int p = 0; p < PrayerSchedule.KEYS.length; p++) {
                check(labels.contains(PrayerSchedule.time(parsed.today(Instant.now()).time(p))), "Official prayer time appears on screen: " + p);
            }
            saveScreenshot("app.png");
            // Bind to a native AppWidgetHost: verifies RemoteViews and receiver updates on Android.
            host = new AppWidgetHost(screen, 3001);
            host.startListening();
            widgetId = host.allocateAppWidgetId();
            AppWidgetManager manager = AppWidgetManager.getInstance(target);
            ComponentName component = new ComponentName(target, PrayerWidget.class);
            check(manager.bindAppWidgetIdIfAllowed(widgetId, component), "Test emulator must grantbind to the target package.");
            AppWidgetHost widgetHost = host; int boundId = widgetId;
            runOnMainSync(() -> {
                LinearLayout preview = new LinearLayout(screen); preview.setOrientation(LinearLayout.VERTICAL);
                preview.setPadding(dp(screen, 24), dp(screen, 72), dp(screen, 24), dp(screen, 24)); preview.setBackgroundColor(0xFFF6F7F1);
                TextView title = new TextView(screen); title.setText("Waktu Solat MY · Widget"); title.setTextColor(0xFF193E34); title.setTextSize(22);
                preview.addView(title);
                AppWidgetHostView view = widgetHost.createView(screen, boundId, manager.getAppWidgetInfo(boundId));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(screen, 220)); params.topMargin = dp(screen, 24);
                preview.addView(view, params); screen.setContentView(preview);
                PrayerWidget.updateAll(screen);
            });
            waitForIdleSync();
            // Allow the widget host's binder-delivered RemoteViews update to enter the main queue.
            android.os.SystemClock.sleep(1200);
            waitForIdleSync();
            labels.clear(); runOnMainSync(() -> collect(screen.getWindow().getDecorView(), labels));
            check(labels.contains("KL · Putrajaya"), "Widget inflates without RemoteViews errors.");
            check(labels.stream().anyMatch(label -> label.startsWith("Subuh\n")), "Widget displays the daily timetable.");
            saveScreenshot("widget.png");
            result.putString("stream", "PASS: " + checks + " native Android smoke checks. Screenshots saved in app external files/smoke.\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL after " + checks + " checks: " + android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            if (host != null) { if (widgetId != -1) host.deleteAppWidgetId(widgetId); host.stopListening(); }
            if (activity != null) { MainActivity screen = activity; runOnMainSync(screen::finish); }
        }
    }
    private void check(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
    private void collect(View view, List<String> labels) {
        if (view instanceof TextView text) labels.add(text.getText().toString());
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), labels);
    }
    private int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private void saveScreenshot(String name) throws IOException {
        Bitmap image = getUiAutomation().takeScreenshot();
        if (image == null) throw new IOException("Screenshot not available");
        File directory = new File(getTargetContext().getExternalFilesDir(null), "smoke");
        if (!directory.exists() && !directory.mkdirs()) throw new IOException("Cannot create screenshot directory");
        try (FileOutputStream output = new FileOutputStream(new File(directory, name))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { image.recycle(); }
    }
}
