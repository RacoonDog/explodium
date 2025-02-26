package io.github.racoondog.explodium.mixin.metrics;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.racoondog.explodium.IExplosion;
import io.github.racoondog.explodium.Metrics;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.caffeinemc.mods.lithium.common.world.explosions.MutableExplosionClipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BiFunction;

/**
 * @author Crosby
 */
@Mixin(value = ExplosionImpl.class, priority = 2000)
public abstract class ExplosionImplMixin implements IExplosion {
    @Inject(
        method = "explode",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/world/ServerWorld;emitGameEvent(Lnet/minecraft/entity/Entity;Lnet/minecraft/registry/entry/RegistryEntry;Lnet/minecraft/util/math/Vec3d;)V")
    )
    private void collectMetrics$0(CallbackInfo ci) {
        Metrics.explosions.getAndIncrement();
        if (this.explodium$isOriginSectionEmpty()) {
            Metrics.explosionsInEmptySection.getAndIncrement();
        }
    }

    @Inject(
        method = "calculateReceivedDamage",
        at = @At("RETURN")
    )
    private static void collectMetrics$1(Vec3d pos, Entity entity, CallbackInfoReturnable<Float> cir) {
        Metrics.entityRaycastCallback(entity.getType(), cir.getReturnValue());
    }

    /* Block Raycasts */

    @SuppressWarnings({"MixinAnnotationTarget", "InvalidMemberReference"})
    @TargetHandler(
        mixin = "net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.ServerExplosionMixin",
        name = "performRayCast"
    )
    @Inject(
        method = "@MixinSquared:Handler",
        at = @At("HEAD")
    )
    private void collectMetrics$2(Random random, double vecX, double vecY, double vecZ, LongOpenHashSet touched, CallbackInfo info) {
        Metrics.blockRaycasts.getAndIncrement();
    }

    @SuppressWarnings({"MixinAnnotationTarget", "InvalidMemberReference", "UnresolvedMixinReference"})
    @TargetHandler(
        mixin = "io.github.racoondog.explodium.mixin.empty_sections.ExplosionImplMixin",
        name = "performRayCast"
    )
    @Inject(
        method = "@MixinSquared:Handler",
        at = @At(value = "INVOKE", target = "Lio/github/racoondog/explodium/Reflect;noopTarget()V")
    )
    private static void collectMetrics$3(Random random, double vecX, double vecY, double vecZ, LongOpenHashSet touched, CallbackInfo infoInjected, // Original injection parameters
                                       double dist, double normX, double normY, double normZ, float strength, double stepX, double stepY, double stepZ, int prevX, int prevY, int prevZ, float prevResistance, int boundMinY, int boundMaxY, // Original injection locals
                                       CallbackInfo info) {
        Metrics.blockRaycastsIntersectEmptySection.getAndIncrement();
    }

    /* Entity Raycasts */

    @WrapOperation(
        method = "damageEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/explosion/ExplosionImpl;calculateReceivedDamage(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/Entity;)F")
    )
    private float collectMetrics$4(Vec3d pos, Entity entity, Operation<Float> original) {
        float result = original.call(pos, entity);

        Metrics.entityRaycastCallback(entity.getType(), result);

        if (result == 1f && this.explodium$isOriginSectionEmpty()
            && entity.getChunkPos().x == this.explodium$getOriginSectionX()
            && entity.getChunkPos().z == this.explodium$getOriginSectionZ()
            && ChunkSectionPos.getSectionCoord(entity.getY()) == this.explodium$getOriginSectionY()) {
            Metrics.entityRaycastsSkipped.getAndIncrement();
        }

        return result;
    }

    @SuppressWarnings({"MixinAnnotationTarget", "InvalidMemberReference"})
    @TargetHandler(
        mixin = "io.github.racoondog.explodium.mixin.lowres_raycast.ExplosionImplMixin",
        name = "calculateReceivedDamageLowRes"
    )
    @Inject(
        method = "@MixinSquared:Handler",
        at = @At(value = "RETURN")
    )
    private static float collectMetrics$5(Box box, MutableExplosionClipContext context, BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory, double xDiff, double yDiff, double zDiff, double xStep, double yStep, double zStep, CallbackInfoReturnable<Float> cir) {
        float result = cir.getReturnValueF();

        if (result == 0f || result == 1f) {
            Metrics.entityCoverResultLowRes.getAndIncrement();
        }

        Metrics.entityLowResRays.getAndAdd(Metrics.getLowResSamplePoints(
            box.maxX - box.minX,
            box.maxY - box.minY
        ));

        return result;
    }


    @SuppressWarnings({"UnresolvedMixinReference", "MixinAnnotationTarget"})
    @Inject(
        method = "calculateReceivedDamage",
        at = @At(value = "INVOKE", target = "Lio/github/racoondog/explodium/Reflect;noopTarget()V")
    )
    private static void collectMetrics$6(Vec3d pos, Entity entity, CallbackInfoReturnable<Float> cir) {
        EntityDimensions dimensions = entity.getType().getDimensions();
        Metrics.entityRegularResRays.getAndAdd(Metrics.getSamplePoints(
            dimensions.width(),
            dimensions.height()
        ));
    }
}
