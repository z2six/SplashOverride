// MainFile: src/main/java/org/z2six/splashoverride/SplashSources.java
package org.z2six.splashoverride;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utility class responsible for building the final splash list used by our Mixin.
 *
 * - Fetches remote splashes (if enabled).
 * - Parses local config splashes.
 * - Handles GitHub "blob" → "raw" URL conversion.
 * - Caches the final list for the lifetime of the game session.
 *
 * Behavior:
 * - On first use this session:
 *   - If useRemoteSource=true:
 *       - Attempt to fetch and parse remote splashes.
 *       - If remote succeeds (non-empty), cache ONLY the remote list and use that.
 *       - If remote fails or is empty, fall back to localSplashes (if any) and cache those.
 *   - If useRemoteSource=false:
 *       - Use localSplashes (if any) and cache those.
 *
 * - On subsequent calls:
 *   - Return the cached list immediately; do NOT hit the network again.
 *
 * - On config reload:
 *   - Config.onLoad(...) calls invalidateCache(), so next use will rebuild the list.
 */
public final class SplashSources {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // Cached final list of splashes for this session.
    private static List<String> CACHED_SPLASHES = null;

    private SplashSources() {
        // utility
    }

    /**
     * Clear any cached splashes. Called from Config.onLoad() whenever the config is reloaded,
     * so the next splash build uses updated settings and URL.
     */
    public static synchronized void invalidateCache() {
        LOGGER.debug("[SplashOverride] invalidateCache() called – clearing cached splash list.");
        CACHED_SPLASHES = null;
    }

    /**
     * Build (or retrieve) the list of splashes that SplashOverride wants to use.
     *
     * If the final list is empty, the mixin will fall back to vanilla behavior.
     */
    public static synchronized List<String> buildSplashList() {
        // 0) Return cached result if available
        if (CACHED_SPLASHES != null) {
            LOGGER.debug("[SplashOverride] Returning cached splash list ({} entries).", CACHED_SPLASHES.size());
            return CACHED_SPLASHES;
        }

        List<String> result = new ArrayList<>();

        // 1) Try remote source first (if enabled)
        if (Config.useRemoteSource) {
            List<String> remote = fetchRemoteSplashesSafely();
            if (!remote.isEmpty()) {
                LOGGER.info("[SplashOverride] Loaded {} splashes from remote URL (first time this session).",
                        remote.size());
                // When remote works, we ONLY use remote entries.
                result.addAll(remote);
            } else {
                LOGGER.warn("[SplashOverride] Remote splash list is empty or failed to load on first attempt. " +
                        "Falling back to localSplashes (if any) for this session.");
            }
        } else {
            LOGGER.debug("[SplashOverride] useRemoteSource is disabled in config; skipping remote fetch.");
        }

        // 2) If remote failed OR useRemoteSource=false, use local config entries
        if (result.isEmpty()) {
            if (Config.localSplashes != null && !Config.localSplashes.isEmpty()) {
                LOGGER.info("[SplashOverride] Using {} local splashes from config (no remote splashes available).",
                        Config.localSplashes.size());
                result.addAll(Config.localSplashes);
            }
        }

        // 3) Final result
        if (result.isEmpty()) {
            LOGGER.debug("[SplashOverride] No custom splashes resolved; vanilla SplashManager will handle splashes.");
            CACHED_SPLASHES = Collections.emptyList();
        } else {
            CACHED_SPLASHES = List.copyOf(result);
        }

        return CACHED_SPLASHES;
    }

    /**
     * Fetch splashes from the configured remote URL, converting GitHub blob URLs
     * to raw URLs automatically.
     *
     * Returns an empty list on error or when the response body is empty or only comments.
     * Never throws.
     */
    private static List<String> fetchRemoteSplashesSafely() {
        String configuredUrl = Config.remoteUrl;
        if (configuredUrl == null || configuredUrl.isBlank()) {
            LOGGER.warn("[SplashOverride] remoteUrl is blank; skipping remote fetch.");
            return Collections.emptyList();
        }

        String normalizedUrl = normalizeGitHubBlobUrl(configuredUrl);

        try {
            LOGGER.info("[SplashOverride] Fetching splashes from remote URL: {}", normalizedUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizedUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "SplashOverride/" + Splashoverride.MODID)
                    .GET()
                    .build();

            HttpResponse<String> response =
                    HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                LOGGER.warn("[SplashOverride] Remote splash fetch failed: HTTP {} from {}", status, normalizedUrl);
                return Collections.emptyList();
            }

            String body = response.body();
            if (body == null || body.isBlank()) {
                LOGGER.warn("[SplashOverride] Remote splash response body is empty for URL: {}", normalizedUrl);
                return Collections.emptyList();
            }

            List<String> lines = new ArrayList<>();
            Arrays.stream(body.split("\\R"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !s.startsWith("#"))
                    .forEach(lines::add);

            return lines;
        } catch (Exception e) {
            LOGGER.warn("[SplashOverride] Exception while fetching remote splashes from '{}': {}",
                    normalizedUrl, e.toString());
            LOGGER.debug("[SplashOverride] Full remote fetch stacktrace:", e);
            return Collections.emptyList();
        }
    }

    /**
     * Convert a GitHub "blob" URL to the matching raw.githubusercontent.com URL.
     * If the URL is not a GitHub blob URL, it is returned unchanged.
     */
    private static String normalizeGitHubBlobUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.equalsIgnoreCase("github.com")) {
                return url;
            }

            // Expected: /user/repo/blob/branch/path/to/file
            String path = uri.getPath(); // e.g. /z2six/SplashOverride/blob/master/src/main/splashes.txt
            String[] parts = path.split("/");
            // parts[0] is "", parts[1]=user, parts[2]=repo, parts[3]="blob", parts[4]=branch, rest=file path
            if (parts.length < 5) {
                return url;
            }
            if (!"blob".equals(parts[3])) {
                return url;
            }

            String user = parts[1];
            String repo = parts[2];
            String branch = parts[4];

            StringBuilder filePath = new StringBuilder();
            for (int i = 5; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    filePath.append('/').append(parts[i]);
                }
            }

            String raw = "https://raw.githubusercontent.com/" + user + "/" + repo + "/" + branch + filePath;
            LOGGER.debug("[SplashOverride] Converted GitHub blob URL '{}' to raw URL '{}'.", url, raw);
            return raw;
        } catch (Exception e) {
            LOGGER.warn("[SplashOverride] Failed to normalize GitHub blob URL '{}': {}", url, e.toString());
            LOGGER.debug("[SplashOverride] Full URL normalization stacktrace:", e);
            return url;
        }
    }
}
