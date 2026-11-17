package my.waktusolat.app;

import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.text.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int INK = 0xFF193E34, MUTED = 0xFF6B786F, PAPER = 0xFFF6F7F1, LIME = 0xFFDAEFAD;
    private static final Locale MALAY = Locale.forLanguageTag("ms-MY");
    private PrayerRepository repository;
    private Zones zones;
    private PrayerSchedule schedule;
    private LinearLayout body, rows;
    private TextView dateLabel, hijriToday, zoneLabel, zoneCode, nextName, nextTime, countdown, status;
    private Button refreshButton;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean loading, visible;
    private String renderedDate = "", renderedNext = "";
    private final Runnable ticker = new Runnable() {
        @Override public void run() { if (visible) { renderClock(); handler.postDelayed(this, 1000); } }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new PrayerRepository(this);
        zones = new Zones(this);
        buildUi();
    }
    @Override public void onResume() {
        super.onResume(); visible = true;
        schedule = repository.cached(repository.zone(), Instant.now());
        render();
        handler.post(ticker);
        if (repository.needsRefresh(repository.zone(), Instant.now())) refresh(false);
        UpdateScheduler.schedule(this);
    }
    @Override public void onPause() { visible = false; handler.removeCallbacks(ticker); super.onPause(); }

    private void buildUi() {
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(PAPER);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        FrameLayout.LayoutParams scrollParams = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        scrollParams.width = Math.min(getResources().getDisplayMetrics().widthPixels, dp(640));
        root.addView(scroll, scrollParams);
        body = column(); body.setPadding(dp(24), dp(24), dp(24), dp(28));
        scroll.addView(body);
        TextView brand = text("WAKTU  /  MALAYSIA", 11, MUTED, true); brand.setLetterSpacing(.18f); body.addView(brand);
        TextView title = text("Waktu solat", 34, INK, true); add(body, title, 12);
        dateLabel = text("", 14, MUTED, false); add(body, dateLabel, 6);
        LinearLayout islamDate = new LinearLayout(this); islamDate.setGravity(Gravity.CENTER_VERTICAL);
        islamDate.setPadding(dp(16), dp(16), dp(16), dp(16)); islamDate.setBackground(shape(Color.WHITE, 20, 0xFFE3E8DC));
        LinearLayout islamText = column(); islamText.addView(text(getString(R.string.hijri_today), 11, MUTED, true));
        hijriToday = text("", 18, INK, true); hijriToday.setId(R.id.hijri_today_text); add(islamText, hijriToday, 6);
        islamDate.addView(islamText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView calendarLink = text(getString(R.string.calendar_link), 12, INK, true); calendarLink.setPadding(dp(12), dp(12), 0, dp(12)); islamDate.addView(calendarLink);
        islamDate.setContentDescription(getString(R.string.open_hijri_calendar)); islamDate.setFocusable(true); islamDate.setClickable(true);
        islamDate.setOnClickListener(view -> HijriCalendarDialog.show(this, schedule, Instant.now().atZone(PrayerSchedule.MALAYSIA).toLocalDate()));
        add(body, islamDate, 18);

        LinearLayout location = new LinearLayout(this); location.setGravity(Gravity.CENTER_VERTICAL);
        location.setPadding(dp(18), dp(16), dp(16), dp(16)); location.setBackground(shape(Color.WHITE, 20, 0xFFE3E8DC));
        LinearLayout locationText = column();
        zoneCode = text("", 11, MUTED, true); locationText.addView(zoneCode);
        zoneLabel = text("", 17, INK, true); add(locationText, zoneLabel, 5);
        location.addView(locationText, new LinearLayout.LayoutParams(0, -2, 1));
        TextView change = text("Tukar  ›", 13, INK, true); change.setPadding(dp(12), dp(12), 0, dp(12)); location.addView(change);
        location.setContentDescription("Tukar zon waktu solat"); location.setFocusable(true); location.setClickable(true);
        location.setOnClickListener(v -> showZones()); add(body, location, 24);

        LinearLayout hero = column(); hero.setBackground(shape(INK, 26, 0)); hero.setPadding(dp(24), dp(23), dp(24), dp(23));
        LinearLayout top = new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL);
        TextView eyebrow = text("SOLAT SETERUSNYA", 10, LIME, true); eyebrow.setLetterSpacing(.17f);
        top.addView(eyebrow, new LinearLayout.LayoutParams(0, -2, 1));
        TextView moon = text("☾", 30, LIME, false); top.addView(moon); hero.addView(top);
        nextName = text("Memuatkan jadual", 25, Color.WHITE, true); add(hero, nextName, 13);
        nextTime = text("—:—", 58, Color.WHITE, false); nextTime.setFontFeatureSettings("tnum"); add(hero, nextTime, 0);
        countdown = text("Menyambung ke e-Solat JAKIM", 13, LIME, false); add(hero, countdown, 10);
        add(body, hero, 16);

        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(text("Jadual hari ini", 19, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView format = text("24 JAM  ·  MYT", 10, MUTED, true); heading.addView(format); add(body, heading, 28);
        rows = column(); rows.setBackground(shape(Color.WHITE, 20, 0xFFE3E8DC)); rows.setPadding(dp(8), dp(6), dp(8), dp(6)); add(body, rows, 12);
        status = text("", 12, MUTED, false); status.setLineSpacing(dp(3), 1); add(body, status, 14);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button widget = button("＋  Tambah widget", INK, LIME); widget.setOnClickListener(v -> pinWidget());
        actions.addView(widget, new LinearLayout.LayoutParams(0, dp(52), 1));
        refreshButton = button("↻", INK, Color.WHITE); refreshButton.setTextSize(25); refreshButton.setContentDescription("Segar semula waktu solat");
        LinearLayout.LayoutParams refreshParams = new LinearLayout.LayoutParams(dp(54), dp(52)); refreshParams.leftMargin = dp(10);
        actions.addView(refreshButton, refreshParams); refreshButton.setOnClickListener(v -> refresh(true)); add(body, actions, 20);
        TextView source = text("Sumber waktu: e-Solat JAKIM  ↗", 12, MUTED, false); source.setPadding(0, dp(16), 0, dp(10)); source.setGravity(Gravity.CENTER);
        source.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(PrayerRepository.SOURCE))); }
            catch (ActivityNotFoundException error) { Toast.makeText(this, PrayerRepository.SOURCE, Toast.LENGTH_LONG).show(); }
        }); body.addView(source);
        TextView note = text("Aplikasi bebas · Tiada iklan · Tiada akaun", 10, MUTED, false); note.setGravity(Gravity.CENTER); body.addView(note);
        TextView copyright = text(getString(R.string.copyright_notice), 11, MUTED, false);
        copyright.setGravity(Gravity.CENTER); add(body, copyright, 10);
        setContentView(root);
    }
    private void render() {
        Zones.Zone zone = zones.find(repository.zone());
        zoneCode.setText(zone.state.toUpperCase(MALAY) + "  /  " + zone.code);
        zoneLabel.setText(zone.name);
        renderedDate = ""; renderedNext = "";
        renderClock();
        renderStatus(null);
    }
    private void renderClock() {
        Instant now = Instant.now();
        LocalDate today = now.atZone(PrayerSchedule.MALAYSIA).toLocalDate();
        // At the year boundary reload the year-specific cache, never reuse yesterday's timetable.
        if (schedule != null && !schedule.days.isEmpty() && schedule.days.firstKey().getYear() != today.getYear()) {
            schedule = repository.cached(repository.zone(), now);
        }
        PrayerSchedule.Day day = schedule == null ? null : schedule.today(now);
        PrayerSchedule.Next next = schedule == null ? null : schedule.next(now);
        String nextKey = next == null ? "none" : next.instant.toString();
        if (!renderedDate.equals(today.toString()) || !renderedNext.equals(nextKey)) {
            boolean dateChanged = !renderedDate.isEmpty() && !renderedDate.equals(today.toString());
            renderedDate = today.toString(); renderedNext = nextKey;
            String date = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", MALAY));
            dateLabel.setText(date);
            HijriDate hijri = day == null ? null : HijriDate.available(day.hijri);
            hijriToday.setText(hijri == null ? getString(R.string.hijri_unavailable) : hijri.label());
            nextName.setText(next == null ? "Jadual belum tersedia" : next.name + (next.tomorrow ? " · esok" : ""));
            nextTime.setText(next == null ? "—:—" : next.time());
            renderRows(day, next);
            if (dateChanged && repository.needsRefresh(repository.zone(), now)) refresh(false);
        }
        countdown.setText(next == null ? (loading ? "Menyambung ke e-Solat JAKIM…" : "Ketik ↻ untuk memuatkan jadual") : "Lagi " + PrayerSchedule.countdown(now, next.instant) + "  hingga " + next.name);
    }
    private void renderRows(PrayerSchedule.Day day, PrayerSchedule.Next next) {
        rows.removeAllViews();
        for (int i = 0; i < PrayerSchedule.KEYS.length; i++) {
            boolean upcoming = next != null && !next.tomorrow && next.index == i;
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(14), dp(14), dp(14), dp(14));
            if (upcoming) row.setBackground(shape(0xFFECF3DD, 13, 0));
            boolean secondary = i == 0 || i == 2;
            TextView label = text(PrayerSchedule.LABELS[i], 16, secondary ? MUTED : INK, upcoming);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            if (upcoming) {
                TextView badge = text("SETERUSNYA", 9, 0xFF4A6532, true); badge.setPadding(0, 0, dp(12), 0); row.addView(badge);
            }
            TextView time = text(day == null ? "—:—" : PrayerSchedule.time(day.time(i)), 17, secondary ? MUTED : INK, true);
            time.setFontFeatureSettings("tnum"); row.addView(time); rows.addView(row);
        }
    }
    private void refresh(boolean force) {
        if (loading) return;
        loading = true; refreshButton.setEnabled(false); renderStatus(null);
        String requestedZone = repository.zone();
        repository.refresh(requestedZone, force, (data, error) -> runOnUiThread(() -> {
            if (isDestroyed()) return;
            loading = false; refreshButton.setEnabled(true);
            if (requestedZone.equals(repository.zone())) { schedule = data; render(); renderStatus(error); }
            else refresh(false);
            PrayerWidget.updateAll(this); UpdateScheduler.schedule(this);
        }));
    }
    private void renderStatus(Exception error) {
        Instant now = Instant.now();
        boolean hasToday = schedule != null && schedule.today(now) != null;
        if (loading) status.setText(hasToday ? "Mengemas kini jadual daripada JAKIM…" : "Memuat turun jadual rasmi daripada JAKIM…");
        else if (error != null) status.setText(hasToday ? "Sambungan gagal. Jadual tersimpan masih tersedia. Ketik ↻ untuk cuba lagi." : "Jadual belum dimuat turun. Semak internet dan tarikh peranti, kemudian ketik ↻.");
        else if (hasToday) {
            long updated = repository.updatedAt(repository.zone(), now);
            String stamp = Instant.ofEpochMilli(updated).atZone(PrayerSchedule.MALAYSIA).format(DateTimeFormatter.ofPattern("d MMM, HH:mm", MALAY));
            status.setText("Dikemas kini " + stamp + " · Jadual tersimpan untuk bacaan luar talian.");
        } else status.setText("Internet diperlukan untuk muat turun kali pertama bagi zon dan tahun pilihan.");
    }
    private void showZones() {
        LinearLayout panel = column(); panel.setPadding(dp(20), dp(6), dp(20), 0);
        EditText search = new EditText(this); search.setSingleLine(true); search.setTextSize(16); search.setHint("Cari negeri, daerah atau kod zon"); panel.addView(search);
        ListView list = new ListView(this); panel.addView(list, new LinearLayout.LayoutParams(-1, dp(380)));
        ArrayList<Zones.Zone> filtered = new ArrayList<>(zones.all);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        for (Zones.Zone zone : filtered) adapter.add(zone.display());
        list.setAdapter(adapter);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Pilih zon solat").setView(panel).setNegativeButton("Tutup", null).create();
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().toLowerCase(MALAY).trim(); filtered.clear(); adapter.clear();
                for (Zones.Zone zone : zones.all) if (zone.display().toLowerCase(MALAY).contains(query)) { filtered.add(zone); adapter.add(zone.display()); }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        list.setOnItemClickListener((parent, view, position, id) -> {
            repository.setZone(filtered.get(position).code); dialog.dismiss();
            schedule = repository.cached(repository.zone(), Instant.now()); render();
            PrayerWidget.updateAll(this); UpdateScheduler.schedule(this); refresh(false);
        });
        dialog.show();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    private void pinWidget() {
        AppWidgetManager manager = getSystemService(AppWidgetManager.class);
        if (manager.isRequestPinAppWidgetSupported()) {
            manager.requestPinAppWidget(new ComponentName(this, PrayerWidget.class), null, null);
        } else {
            new AlertDialog.Builder(this).setTitle("Tambah widget ke skrin utama")
                    .setMessage("Tekan lama ruang kosong di skrin utama telefon → Widget → Waktu Solat MY. Seret widget ke skrin utama.\n\nWidget menggunakan zon yang anda pilih dalam app.")
                    .setPositiveButton("Faham", null).show();
        }
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView text(String content, int size, int color, boolean medium) {
        TextView view = new TextView(this); view.setText(content); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL)); view.setIncludeFontPadding(false); return view;
    }
    private Button button(String label, int color, int background) {
        Button view = new Button(this); view.setText(label); view.setAllCaps(false); view.setTextSize(14); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); view.setBackground(shape(background, 15, background == Color.WHITE ? 0xFFE3E8DC : 0)); return view;
    }
    private GradientDrawable shape(int color, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke); return drawable;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(top); parent.addView(child, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
