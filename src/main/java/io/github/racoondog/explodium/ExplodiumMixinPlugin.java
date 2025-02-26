package io.github.racoondog.explodium;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Set;

/**
 * To maximize performance, this mixin plugin handles disabling metrics related injections when not wanted.
 * This class also contains fields that are accessed early to not cause cascading class loading issues.
 * @author Crosby
 */
public class ExplodiumMixinPlugin implements IMixinConfigPlugin {
    public static boolean CAPTURE_METRICS;
    public static @Nullable MethodHandles.Lookup EXPLOSION_IMPL_LOOKUP = null;
    private String mixinPackage;

    @Override
    public void onLoad(String mixinPackage) {
        CAPTURE_METRICS = FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("explodium.metrics");

        this.mixinPackage = mixinPackage;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith(mixinPackage + ".metrics")) {
            return CAPTURE_METRICS;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
