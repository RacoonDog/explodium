package io.github.racoondog.explodium;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.world.explosion.ExplosionImpl;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.transformer.meta.MixinMerged;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * This class handles invoking and accessing unique class members injected via mixin.
 * @author Crosby
 */
public class Reflect {
    private static final MethodHandle TRAVERSE_BLOCK_HANDLE;

    public static float invokeTraverseBlock(ExplosionImpl explosion, float strength, int blockX, int blockY, int blockZ, LongOpenHashSet touched) {
        try {
            return (float) TRAVERSE_BLOCK_HANDLE.invokeExact(explosion, strength, blockX, blockY, blockZ, touched);
        } catch (Throwable e) {
            throw new RuntimeException("Error invoking required internal method.", e);
        }
    }

    /**
     * This method is used to mark an injection target for another mixin.
     * @author Crosby
     */
    public static void noopTarget() {}

    static {
        TRAVERSE_BLOCK_HANDLE = unreflectUniqueMethod(ExplodiumMixinPlugin.EXPLOSION_IMPL_LOOKUP, ExplosionImpl.class, "traverseBlock", "net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.ServerExplosionMixin", float.class, new Class<?>[]{float.class, int.class, int.class, int.class, LongOpenHashSet.class});
    }

    @SuppressWarnings("ConstantValue")
    private static MethodHandle unreflectUniqueMethod(@Nullable MethodHandles.Lookup capturedLookup, Class<?> targetClass, String methodName, String mixinClassName, Class<?> returnClass, Class<?>[] parameters) {
        try {
            @Nullable Method foundMethod = null;

            @Nullable Method directMethod = targetClass.getDeclaredMethod(methodName, parameters);
            if (directMethod != null && isValidUniqueMethod(directMethod, mixinClassName, returnClass, parameters)) {
                foundMethod = directMethod;
            } else {
                for (Method method : targetClass.getDeclaredMethods()) {
                    if (isValidUniqueMethod(method, mixinClassName, returnClass, parameters)) {
                        foundMethod = method;
                        break;
                    }
                }
            }

            if (foundMethod == null) {
                throw new IllegalStateException("Could not find required internal method '" + methodName + "' from '" + mixinClassName + "'.");
            }

            @Nullable MethodHandles.Lookup lookup = capturedLookup;
            if (lookup == null) {
                Explodium.LOGGER.warn("Reflect was initialized before lookups were captured, trying fallback.");
                foundMethod.setAccessible(true);
                lookup = MethodHandles.lookup();
            }
            return lookup.unreflect(foundMethod);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Error getting handle for required internal method '" + methodName + "' from '" + mixinClassName + "'.", e);
        }
    }

    private static boolean isValidUniqueMethod(Method method, String mixinClassName, Class<?> returnClass, Class<?>[] parameters) {
        @Nullable MixinMerged metadata = method.getAnnotation(MixinMerged.class);
        if (metadata == null) return false;
        if (!metadata.mixin().equals(mixinClassName)) return false;
        if (method.getReturnType() != returnClass) return false;

        return Arrays.equals(method.getParameterTypes(), parameters);
    }

    @SuppressWarnings("ConstantValue")
    private static VarHandle unreflectUniqueField(@Nullable MethodHandles.Lookup capturedLookup, Class<?> targetClass, String fieldName, String mixinClassName, Class<?> fieldType) {
        try {
            @Nullable Field foundField = null;

            @Nullable Field directField = targetClass.getDeclaredField(fieldName);
            if (directField != null && isValidUniqueField(directField, mixinClassName, fieldType)) {
                foundField = directField;
            } else {
                for (Field field : targetClass.getDeclaredFields()) {
                    if (isValidUniqueField(field, mixinClassName, fieldType)) {
                        foundField = field;
                        break;
                    }
                }
            }

            if (foundField == null) {
                throw new IllegalStateException("Could not find required internal field '" + fieldName + "' from '" + mixinClassName + "'.");
            }

            @Nullable MethodHandles.Lookup lookup = capturedLookup;
            if (lookup == null) {
                Explodium.LOGGER.warn("Reflect was initialized before lookups were captured, trying fallback.");
                foundField.setAccessible(true);
                lookup = MethodHandles.lookup();
            }
            return lookup.unreflectVarHandle(foundField);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Error getting handle for required internal field '" + fieldName + "' from '" + mixinClassName + "'.", e);
        }
    }

    private static boolean isValidUniqueField(Field field, String mixinClassName, Class<?> fieldType) {
        @Nullable MixinMerged metadata = field.getAnnotation(MixinMerged.class);
        if (metadata == null) return false;
        if (!metadata.mixin().equals(mixinClassName)) return false;

        return field.getType() == fieldType;
    }
}
