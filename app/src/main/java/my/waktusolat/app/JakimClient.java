package my.waktusolat.app;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;

/** One fixed HTTPS origin, normal system certificate checks, no redirects or executable content. */
final class JakimClient {
    static final String HOST = "www.e-solat.gov.my";
    static final int MAX_BYTES = 2 * 1024 * 1024;
    private final Connections connections;

    JakimClient() { this(url -> (HttpsURLConnection) url.openConnection()); }
    JakimClient(Connections connections) { this.connections = connections; }

    static URL urlForZone(String zone) throws MalformedURLException {
        if (zone == null || !zone.matches("[A-Z]{3}[0-9]{2}")) throw new IllegalArgumentException("Kod zon tidak sah.");
        return new URL("https://" + HOST + "/index.php?r=esolatApi/takwimsolat&period=year&zone=" + zone);
    }

    String downloadYear(String zone) throws IOException {
        HttpsURLConnection connection = connections.open(urlForZone(zone));
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "WaktuSolatMY/0.1.3 (Android)");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Pelayan JAKIM tidak dapat dihubungi (" + status + ").");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Jawapan pelayan terlalu besar.");
            try (InputStream stream = connection.getInputStream()) { return readBounded(stream); }
        } finally { connection.disconnect(); }
    }

    static String readBounded(InputStream stream) throws IOException {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = stream.read(chunk)) != -1) {
                if (count > MAX_BYTES - buffer.size()) throw new IOException("Jawapan pelayan terlalu besar.");
                buffer.write(chunk, 0, count);
            }
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            return decoder.decode(ByteBuffer.wrap(buffer.toByteArray())).toString();
        }
    }

    interface Connections { HttpsURLConnection open(URL url) throws IOException; }
}
