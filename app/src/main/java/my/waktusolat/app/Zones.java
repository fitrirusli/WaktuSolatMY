package my.waktusolat.app;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class Zones {
    public final List<Zone> all;
    public Zones(Context context) {
        ArrayList<Zone> list = new ArrayList<>();
        try (InputStream stream = context.getAssets().open("zones.json"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[4096];
            int length;
            while ((length = stream.read(chunk)) != -1) bytes.write(chunk, 0, length);
            JSONArray rows = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name())).getJSONArray("zones");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                list.add(new Zone(row.getString("code"), row.getString("state"), row.getString("name")));
            }
        } catch (Exception error) { throw new IllegalStateException("Senarai zon tidak dapat dibaca", error); }
        all = Collections.unmodifiableList(list);
    }
    public Zone find(String code) {
        for (Zone zone : all) if (zone.code.equals(code)) return zone;
        return new Zone("WLY01", "Wilayah Persekutuan", "Kuala Lumpur, Putrajaya");
    }
    public static final class Zone {
        public final String code, state, name;
        Zone(String code, String state, String name) { this.code = code; this.state = state; this.name = name; }
        public String display() { return state + " · " + code + "\n" + name; }
    }
}
