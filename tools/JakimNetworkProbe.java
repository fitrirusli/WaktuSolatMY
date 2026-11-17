package my.waktusolat.app;

/** Optional live HTTPS check using the same bounded client as the app. */
public final class JakimNetworkProbe {
    public static void main(String[] args) throws Exception {
        String body = new JakimClient().downloadYear("WLY01");
        if (!body.contains("prayerTime") || !body.contains("WLY01")) throw new IllegalStateException("Unexpected JAKIM response");
        System.out.println("PASS: JAKIM HTTPS request with normal certificate checks, redirects disabled, and bounded response (" + body.length() + " characters).");
    }
}
