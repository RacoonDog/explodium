package io.github.racoondog.explodium.mixin.empty_sections;

import com.bawnorton.mixinsquared.TargetHandler;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.racoondog.explodium.IExplosion;
import io.github.racoondog.explodium.Reflect;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * @author Crosby
 */
@Mixin(value = ExplosionImpl.class, priority = 1500)
public abstract class ExplosionImplMixin implements IExplosion {
    /**
     * If the entity is in the same chunk section as the explosion and the chunk section is empty, we skip the raycasts
     * and directly return full visibility.
     * @author Crosby
     */
    @WrapOperation(
        method = "damageEntities",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/explosion/ExplosionImpl;calculateReceivedDamage(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/Entity;)F")
    )
    private float specialCaseEmptySections(Vec3d pos, Entity entity, Operation<Float> original) {
        if (this.explodium$isOriginSectionEmpty()
            && entity.getChunkPos().x == this.explodium$getOriginSectionX()
            && entity.getChunkPos().z == this.explodium$getOriginSectionZ()
            && ChunkSectionPos.getSectionCoord(entity.getY()) == this.explodium$getOriginSectionY()) {
            return 1f;
        }

        return original.call(pos, entity);
    }

    /**
     * Prevent execution of the regular lithium loop, we reimplement it in a later injection.
     * @author Crosby
     */
    @SuppressWarnings({"MixinAnnotationTarget", "InvalidMemberReference"})
    @TargetHandler(
        mixin = "net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.ServerExplosionMixin",
        name = "performRayCast"
    )
    @ModifyConstant(
        method = "@MixinSquared:Handler",
        constant = @Constant(floatValue = 0.0f, ordinal = 1)
    )
    private float preventRegularExecution(float prevValue) {
        return Float.MAX_VALUE;
    }

    /**
     * Copied from {@link net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.ServerExplosionMixin#performRayCast(Random, double, double, double, LongOpenHashSet)}
     * with an added special-case where the ray traverses an empty chunk section.
     * @author Crosby
     */
    @SuppressWarnings({"MixinAnnotationTarget", "InvalidMemberReference", "JavadocReference"})
    @TargetHandler(
        mixin = "net.caffeinemc.mods.lithium.mixin.world.explosions.block_raycast.ServerExplosionMixin",
        name = "performRayCast"
    )
    @Inject(
        method = "@MixinSquared:Handler",
        at = @At("TAIL"),
        locals = LocalCapture.CAPTURE_FAILHARD
    )
    private void performRayCast(Random random, double vecX, double vecY, double vecZ, LongOpenHashSet touched, CallbackInfo info,
                                double dist, double normX, double normY, double normZ, float strength,
                                double stepX, double stepY, double stepZ,
                                int prevX, int prevY, int prevZ, float prevResistance,
                                int boundMinY, int boundMaxY) {
        int prevChunkX = Integer.MIN_VALUE;
        int prevChunkY = Integer.MIN_VALUE;
        int prevChunkZ = Integer.MIN_VALUE;

        // Step through the ray until it is finally stopped
        while (strength > 0.0F) {
            int blockX = MathHelper.floor(stepX);
            int blockY = MathHelper.floor(stepY);
            int blockZ = MathHelper.floor(stepZ);

            float resistance;

            // Explodium start
            // Since large chain-reaction tnt explosions have a high chance of occurring in empty chunk sections, we can
            // add a check for that and skip the appropriate amount of steps, saving many trips to the blockstate palette.
            int chunkX = ChunkSectionPos.getSectionCoord(blockX);
            int chunkY = ChunkSectionPos.getSectionCoord(blockY);
            int chunkZ = ChunkSectionPos.getSectionCoord(blockZ);
            if (prevChunkX != chunkX && prevChunkY != chunkY && prevChunkZ != chunkZ && this.explodium$isSectionEmpty(chunkX, chunkY, chunkZ)) {
                Reflect.noopTarget();

                prevChunkX = chunkX;
                prevChunkY = chunkY;
                prevChunkZ = chunkZ;

                int steps = calculateSteps(normX, chunkX, stepX);
                steps = Math.min(steps, calculateSteps(normY, chunkY, stepY));
                steps = Math.min(steps, calculateSteps(normZ, chunkZ, stepZ));

                strength -= 0.22500001F * steps;
                stepX += normX * steps;
                stepY += normY * steps;
                stepZ += normZ * steps;

                // Explodium end
            } else {
                // Check whether we have actually moved into a new block this step. Due to how rays are stepped through,
                // over-sampling of the same block positions will occur. Changing this behaviour would introduce differences in
                // aliasing and sampling, which is unacceptable for our purposes. As a band-aid, we can simply re-use the
                // previous result and get a decent boost.
                if (prevX != blockX || prevY != blockY || prevZ != blockZ) {
                    if (blockY < boundMinY || blockY > boundMaxY || blockX < -30000000 || blockZ < -30000000 || blockX >= 30000000 || blockZ >= 30000000) {
                        return;
                    }
                    // The coordinates are within the world bounds, so we can safely traverse the block
                    // Mixinsquared doesn't support invokers, so we have to reflect - Explodium
                    resistance = Reflect.invokeTraverseBlock((ExplosionImpl) (Object) this, strength, blockX, blockY, blockZ, touched);

                    prevX = blockX;
                    prevY = blockY;
                    prevZ = blockZ;

                    prevResistance = resistance;
                } else {
                    resistance = prevResistance;
                }

                strength -= resistance;
                // Apply a constant fall-off
                strength -= 0.22500001F;

                stepX += normX;
                stepY += normY;
                stepZ += normZ;
            }
        }
    }

    @Unique
    private static int calculateSteps(double perStep, int sectionPos, double pos) {
        if (perStep == 0) throw new AssertionError("Raycast step cannot be zero.");

        int targetPos;
        if (perStep > 0) {
            targetPos = ChunkSectionPos.getBlockCoord(sectionPos + 1);
        } else {
            targetPos = ChunkSectionPos.getBlockCoord(sectionPos - 1) + 15;
        }

        return MathHelper.ceil((targetPos - pos) / perStep);
    }
}
