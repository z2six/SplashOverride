// MainFile: src/main/java/org/z2six/splashoverride/Config.java
package org.z2six.splashoverride;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Client config for SplashOverride.
 *
 * Controls:
 * - Whether to load splashes from a remote URL.
 * - The remote URL itself.
 * - A local list of splashes editable directly in the config file.
 */
@EventBusSubscriber(modid = Splashoverride.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Whether we attempt to fetch splashes from a remote URL
    private static final ModConfigSpec.BooleanValue USE_REMOTE_SOURCE =
            BUILDER.comment("""
                            If true, SplashOverride fetches splashes from the configured remoteUrl.
                            On first use this session:
                              - If the remote request succeeds and returns valid lines, those are used
                                and cached for the rest of the session.
                              - If the remote request fails or returns no valid lines, localSplashes
                                are used instead (and cached).
                            
                            Once a splash list has been successfully built, it is cached in memory and reused
                            for the rest of this game session, until the config is reloaded or changed.""")
                    .define("useRemoteSource", true);

    // The remote URL used to fetch splashes (one line per splash)
    private static final ModConfigSpec.ConfigValue<String> REMOTE_URL =
            BUILDER.comment("""
                            Remote URL for splashes. One line = one splash.
                            Supports regular text URLs and GitHub "blob" URLs – the mod will automatically
                            convert GitHub blob links to their corresponding raw.githubusercontent.com URLs.
                            Example:
                            https://github.com/z2six/SplashOverride/blob/master/splashes.txt""")
                    .define("remoteUrl", "https://github.com/z2six/SplashOverride/blob/master/splashes.txt");

    // A local, user-editable list of splash texts
    private static final ModConfigSpec.ConfigValue<List<? extends String>> LOCAL_SPLASHES =
            BUILDER.comment("""
                            Local splash entries. One string per splash.
                            Empty or comment-style lines (starting with '#') are ignored.
                            
                            These are used:
                              - When useRemoteSource = false, OR
                              - As a fallback if the remote URL fails or returns nothing
                                on first use this session.
                            
                            Once a splash list (remote OR local) has been built, it is cached in memory
                            and reused until the config is reloaded.""")
                    .defineListAllowEmpty(
                            "localSplashes",
                            List.of(
                                    "Welcome to Minecraft!",
                                    "Custom splashes from SplashOverride (local fallback).",
                                    "Edit localSplashes in the config to customize these messages."
                            ),
                            obj -> obj instanceof String
                    );

    // The actual spec we register as a CLIENT config
    static final ModConfigSpec CLIENT_SPEC = BUILDER.build();

    // Runtime values, updated whenever the config is loaded/reloaded
    public static boolean useRemoteSource = true;
    public static String remoteUrl = "";
    public static List<String> localSplashes = Collections.emptyList();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != CLIENT_SPEC) {
            return;
        }

        useRemoteSource = USE_REMOTE_SOURCE.get();
        remoteUrl = REMOTE_URL.get();

        List<String> cleaned = new ArrayList<>();
        for (Object obj : LOCAL_SPLASHES.get()) {
            if (!(obj instanceof String s)) {
                continue;
            }
            String trimmed = s.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#")) { // allow comments
                continue;
            }
            cleaned.add(trimmed);
        }

        localSplashes = List.copyOf(cleaned);

        Splashoverride.LOGGER.info(
                "[SplashOverride] Config loaded: useRemoteSource={}, remoteUrl='{}', localSplashes={}",
                useRemoteSource,
                remoteUrl,
                localSplashes.size()
        );

        // Invalidate any previously cached list so new config values are picked up
        SplashSources.invalidateCache();
        Splashoverride.LOGGER.debug("[SplashOverride] Splash cache invalidated due to config reload.");
    }

    private Config() {
        // no-op
    }
}
