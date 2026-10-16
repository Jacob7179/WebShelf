package com.webshelf.app;

import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Conservative third-party ad/tracker hostname filter. No remote lists or traffic proxy. */
public final class AdBlocker {
    private static final Set<String> DOMAINS = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org", "criteo.com",
        "criteo.net", "taboola.com", "outbrain.com", "pubmatic.com",
        "rubiconproject.com", "openx.net", "smartadserver.com", "bidswitch.net",
        "casalemedia.com", "advertising.com", "contextweb.com", "media.net",
        "yieldmo.com", "indexww.com", "3lift.com", "lijit.com",
        "scorecardresearch.com", "quantserve.com", "moatads.com"
    ));
    // Narrow ad endpoints on YouTube: never block googlevideo.com, player API,
    // watch pages or manifests because those are also needed for regular playback.
    private static boolean isYoutubeAdEndpoint(String host, String path) {
        boolean youtube=host.equals("youtube.com") || host.endsWith(".youtube.com");
        if (!youtube || path==null) return false;
        return path.equals("/pagead/interaction") || path.startsWith("/pagead/viewthroughconversion/")
                || path.startsWith("/api/stats/ads") || path.startsWith("/pagead/conversion/");
    }
    private volatile boolean enabled;

    public AdBlocker(boolean enabled) { this.enabled = enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public WebResourceResponse intercept(WebResourceRequest request) {
        if (!enabled || request == null || request.isForMainFrame()) return null;
        Uri uri = request.getUrl();
        if (uri == null) return null;
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
        String host = uri.getHost();
        if (host == null) return null;
        host = host.toLowerCase(Locale.ROOT);
        boolean blocked = false;
        for (String domain : DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) { blocked = true; break; }
        }
        if (!blocked && !isYoutubeAdEndpoint(host,uri.getPath())) return null;
        return new WebResourceResponse("text/plain", "UTF-8", 200, "OK",
                java.util.Collections.singletonMap("Access-Control-Allow-Origin", "*"),
                new ByteArrayInputStream(new byte[0]));
    }
}
