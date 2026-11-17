package my.waktusolat.app;

import org.junit.Test;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.*;
import java.security.Principal;
import java.security.cert.Certificate;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class JakimClientTest {
    @Test public void constructsOnlyTheOfficialHttpsEndpoint() throws Exception {
        URL url = JakimClient.urlForZone("WLY01");
        assertEquals("https", url.getProtocol());
        assertEquals("www.e-solat.gov.my", url.getHost());
        assertEquals("r=esolatApi/takwimsolat&period=year&zone=WLY01", url.getQuery());
    }
    @Test public void rejectsZoneInjectionBeforeOpeningAConnection() {
        String[] invalid = {null, "", "WLY01&redirect=https://evil.example", "../../etc/passwd", "https://evil.example", "wly01", "WLY001"};
        for (String zone : invalid) assertThrows(IllegalArgumentException.class, () -> JakimClient.urlForZone(zone));
    }
    @Test public void refusesRedirectsAndDisconnects() throws Exception {
        FakeConnection connection = new FakeConnection(302, 0, new byte[0]);
        assertThrows(IOException.class, () -> new JakimClient(url -> connection).downloadYear("WLY01"));
        assertFalse(connection.getInstanceFollowRedirects());
        assertFalse(connection.bodyOpened);
        assertTrue(connection.disconnected);
    }
    @Test public void rejectsOversizedContentLengthWithoutReadingTheBody() throws Exception {
        FakeConnection connection = new FakeConnection(200, JakimClient.MAX_BYTES + 1, new byte[0]);
        assertThrows(IOException.class, () -> new JakimClient(url -> connection).downloadYear("WLY01"));
        assertFalse(connection.bodyOpened);
        assertTrue(connection.disconnected);
    }
    @Test public void enforcesLimitEvenWhenLengthIsMissingOrIncorrect() {
        byte[] bytes = new byte[JakimClient.MAX_BYTES + 1]; Arrays.fill(bytes, (byte) ' ');
        assertThrows(IOException.class, () -> JakimClient.readBounded(new ByteArrayInputStream(bytes)));
    }
    @Test public void acceptsABodyExactlyAtTheLimit() throws Exception {
        byte[] bytes = new byte[JakimClient.MAX_BYTES]; Arrays.fill(bytes, (byte) ' ');
        assertEquals(JakimClient.MAX_BYTES, JakimClient.readBounded(new ByteArrayInputStream(bytes)).length());
    }
    @Test public void rejectsInvalidUtf8() {
        assertThrows(IOException.class, () -> JakimClient.readBounded(new ByteArrayInputStream(new byte[]{(byte) 0xC0, (byte) 0xAF})));
    }
    @Test public void preservesValidTextAndClosesResources() throws Exception {
        String json = "{\"status\":\"OK!\",\"note\":\"© fitrirusli\"}";
        FakeConnection connection = new FakeConnection(200, -1, json.getBytes(StandardCharsets.UTF_8));
        assertEquals(json, new JakimClient(url -> connection).downloadYear("WLY01"));
        assertTrue(connection.bodyClosed); assertTrue(connection.disconnected);
        assertEquals(15000, connection.getConnectTimeout()); assertEquals(20000, connection.getReadTimeout());
    }
    private static final class FakeConnection extends HttpsURLConnection {
        final int status; final long length; final byte[] bytes;
        boolean disconnected, bodyOpened, bodyClosed;
        FakeConnection(int status, long length, byte[] bytes) throws Exception {
            super(new URL("https://www.e-solat.gov.my/")); this.status = status; this.length = length; this.bytes = bytes;
        }
        @Override public int getResponseCode() { return status; }
        @Override public long getContentLengthLong() { return length; }
        @Override public InputStream getInputStream() {
            bodyOpened = true;
            return new ByteArrayInputStream(bytes) { @Override public void close() throws IOException { bodyClosed = true; super.close(); } };
        }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() {}
        @Override public String getCipherSuite() { return "TLS_TEST"; }
        @Override public Certificate[] getLocalCertificates() { return null; }
        @Override public Certificate[] getServerCertificates() { return new Certificate[0]; }
        @Override public Principal getPeerPrincipal() { return null; }
        @Override public Principal getLocalPrincipal() { return null; }
    }
}
