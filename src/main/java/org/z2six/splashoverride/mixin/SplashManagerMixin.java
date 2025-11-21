// MainFile: src/main/java/org/z2six/splashoverride/mixin/SplashManagerMixin.java
package org.z2six.splashoverride.mixin;

import net.minecraft.client.resources.SplashManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.z2six.splashoverride.SplashSources;
import org.z2six.splashoverride.Splashoverride;

import java.util.List;

/**
 * Mixin into SplashManager to override how the splash list is prepared.
 *
 * We intercept the prepare(...) method used during resource reload. If our
 * custom sources (remote URL + config) produce a non-empty list, we return
 * that instead of letting vanilla load assets/minecraft/texts/splashes.txt.
 *
 * If we return an empty list, vanilla behavior is preserved.
 */
@Mixin(SplashManager.class)
public abstract class SplashManagerMixin {

    /**
     * Intercept SplashManager.prepare(ResourceManager, ProfilerFiller).
     *
     * Signature in 1.21.x:
     *     protected List<String> prepare(ResourceManager pResourceManager, ProfilerFiller pProfiler)
     */
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true)
    private void splashoverride$prepare(ResourceManager resourceManager,
                                        ProfilerFiller profiler,
                                        CallbackInfoReturnable<List<String>> cir) {

        // Build our custom list of splashes from URL + config.
        List<String> overrideList = SplashSources.buildSplashList();

        if (overrideList != null && !overrideList.isEmpty()) {
            Splashoverride.LOGGER.info("[SplashOverride] Overriding vanilla splashes with {} custom entries.", overrideList.size());
            // Returning here means SplashManager.apply(...) will receive our list
            // and populate its internal "splashes" field with it.
            cir.setReturnValue(overrideList);
            // No need to cancel explicitly when setReturnValue is used with cancellable=true,
            // but we do it for clarity.
            cir.cancel();
        } else {
            // No custom entries: let vanilla load splashes.txt as usual.
            Splashoverride.LOGGER.debug("[SplashOverride] Custom splash list is empty; falling back to vanilla SplashManager behavior.");
        }
    }
}
