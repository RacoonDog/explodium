package io.github.racoondog.explodium.mixin.lowres_raycast;

import io.github.racoondog.explodium.Config;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.caffeinemc.mods.lithium.common.world.explosions.MutableExplosionClipContext;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.function.BiFunction;

/**
 * @author Crosby
 */
@Mixin(ExplosionImpl.class)
public class ExplosionImplMixin {
    @Unique
    private static final BlockHitResult MISS = BlockHitResult.createMissed(null, null, null);

    /**
     * @author Crosby
     * @reason Use a lower resolution raycast to check whether the entity has partial cover from the explosion.
     */
    @Overwrite
    public static float calculateReceivedDamage(Vec3d pos, Entity entity) {
        Box box = entity.getBoundingBox();

        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1.0 / (xDiff * 2.0 + 1.0);
        double yStep = 1.0 / (yDiff * 2.0 + 1.0);
        double zStep = 1.0 / (zDiff * 2.0 + 1.0);

        if (xStep < 0.0 || yStep < 0.0 || zStep < 0.0) {
            return 0f;
        }

        MutableExplosionClipContext context = new MutableExplosionClipContext(entity.getWorld(), pos);
        BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory = blockHitFactory();

        // Low resolution raycast

        float lowResResult = calculateReceivedDamageLowRes(box, context, blockHitFactory, xDiff, yDiff, zDiff, xStep, yStep, zStep);

        if (lowResResult == 0f || lowResResult == 1f) {
            return lowResResult;
        }

        // Regular resolution raycast
        // todo investigate why low res can return partial cover, but regular res returns absolute cover

        return calculateReceivedDamageRegularRes(box, context, blockHitFactory, xDiff, yDiff, zDiff, xStep, yStep, zStep);
    }

    /**
     * Split method to show up separately from the other one in a spark profile
     * @author Crosby
     */
    @Unique
    private static float calculateReceivedDamageLowRes(Box box, MutableExplosionClipContext context, BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory, double xDiff, double yDiff, double zDiff, double xStep, double yStep, double zStep) {
        double xStepLowRes = Math.min(1.0, xStep * Config.LOW_RES_DISTANCE_MULT);
        double yStepLowRes = Math.min(1.0, yStep * Config.LOW_RES_DISTANCE_MULT);
        double zStepLowRes = Math.min(1.0, zStep * Config.LOW_RES_DISTANCE_MULT);

        return calculateReceivedDamageRegularRes(box, context, blockHitFactory, xDiff, yDiff, zDiff, xStepLowRes, yStepLowRes, zStepLowRes);
    }

    /**
     * Optimized version of {@link ExplosionImpl#calculateReceivedDamage(Vec3d, Entity)}.
     * @author Crosby
     */
    @Unique
    private static float calculateReceivedDamageRegularRes(Box box, MutableExplosionClipContext context, BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory, double xDiff, double yDiff, double zDiff, double xStep, double yStep, double zStep) {
        double xOffset = (1.0 - Math.floor(1.0 / xStep) * xStep) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zStep) * zStep) / 2.0;

        int missedRays = 0;
        int totalRays = 0;

        // Explodium start
        // Here we remove the inner lerps that are present in the vanilla code by just scaling and
        // offsetting the loop parameters. This is more efficient than PaperMC's strategy of replacing the lerps with
        // FMAs
        xStep = xStep * xDiff;
        yStep = yStep * yDiff;
        zStep = zStep * zDiff;

        double startX = box.minX + xOffset;
        double startY = box.minY;
        double startZ = box.minZ + zOffset;
        double endX = box.maxX + xOffset;
        double endY = box.maxY;
        double endZ = box.maxZ + zOffset;

        for (double x = startX; x <= endX; x += xStep) {
            for (double y = startY; y <= endY; y += yStep) {
                for (double z = startZ; z <= endZ; z += zStep) {
                    // Explodium end

                    context.from = new Vec3d(x, y, z);

                    if (BlockView.raycast(context.from, context.to, context, blockHitFactory, ctx -> MISS).getType() == HitResult.Type.MISS) {
                        ++missedRays;
                    }

                    ++totalRays;
                }
            }
        }

        return (float) missedRays / (float) totalRays;
    }

    /**
     * Taken from {@link net.caffeinemc.mods.lithium.mixin.world.explosions.entity_raycast.ServerExplosionMixin#blockHitFactory()}.
     */
    @SuppressWarnings("JavadocReference")
    @Unique
    private static BiFunction<MutableExplosionClipContext, BlockPos, BlockHitResult> blockHitFactory() {
        return new BiFunction<>() {
            int chunkX = Integer.MIN_VALUE;
            int chunkZ = Integer.MIN_VALUE;
            Chunk chunk = null;

            public BlockHitResult apply(MutableExplosionClipContext context, BlockPos blockPos) {
                BlockState state = this.getBlock(context.level, blockPos);
                return state.getCollisionShape(context.level, blockPos).raycast(context.from, context.to, blockPos);
            }

            private BlockState getBlock(WorldView world, BlockPos blockPos) {
                if (world.isOutOfHeightLimit(blockPos.getY())) {
                    return Blocks.VOID_AIR.getDefaultState();
                } else {
                    int chunkX = Pos.ChunkCoord.fromBlockCoord(blockPos.getX());
                    int chunkZ = Pos.ChunkCoord.fromBlockCoord(blockPos.getZ());
                    if (this.chunkX != chunkX || this.chunkZ != chunkZ) {
                        this.chunk = world.getChunk(chunkX, chunkZ);
                        this.chunkX = chunkX;
                        this.chunkZ = chunkZ;
                    }

                    Chunk chunk = this.chunk;
                    if (chunk != null) {
                        ChunkSection section = chunk.getSectionArray()[Pos.SectionYIndex.fromBlockCoord(chunk, blockPos.getY())];
                        if (section != null && !section.isEmpty()) {
                            return section.getBlockState(blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15);
                        }
                    }

                    return Blocks.AIR.getDefaultState();
                }
            }
        };
    }
}
