package io.github.racoondog.explodium;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Arrays;

public class Explodium implements ModInitializer {
	public static final Logger LOGGER = LogUtils.getLogger();
	private static final MethodHandle TRAVERSE_BLOCK_HANDLE;

	static {
		try {
			@Nullable Method traverseBlockMethod = null;
			for (Method method : ExplosionImpl.class.getDeclaredMethods()) {
				@Nullable MixinMerged metadata = method.getAnnotation(MixinMerged.class);
				if (metadata == null) continue;
				if (!metadata.mixin().equals("net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.ServerExplosionMixin")) continue;
				if (method.getReturnType() != float.class) continue;
				if (!Arrays.equals(method.getParameterTypes(), new Class<?>[]{float.class, int.class, int.class, int.class, LongOpenHashSet.class})) continue;

				traverseBlockMethod = method;
			}

			if (traverseBlockMethod == null) {
				throw new IllegalStateException("Could not find required internal method.");
			}

			@Nullable MethodHandles.Lookup lookup = Constants.EXPLOSION_IMPL_LOOKUP;
			if (lookup == null) {
				LOGGER.warn("Entrypoint was initialized too early, trying fallback.");
				traverseBlockMethod.setAccessible(true);
				lookup = MethodHandles.lookup();
			}
			TRAVERSE_BLOCK_HANDLE = lookup.unreflect(traverseBlockMethod);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Error getting handle for required internal methods.", e);
        }
    }

	@Override
	public void onInitialize() {
		if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
			Metrics.registerCommands();
		}
	}

	public static float invokeTraverseBlock(ExplosionImpl explosion, float strength, int blockX, int blockY, int blockZ, LongOpenHashSet touched) {
		try {
			return (float) TRAVERSE_BLOCK_HANDLE.invokeExact(explosion, strength, blockX, blockY, blockZ, touched);
		} catch (Throwable e) {
            throw new RuntimeException("Error invoking required internal method.", e);
        }
    }
}