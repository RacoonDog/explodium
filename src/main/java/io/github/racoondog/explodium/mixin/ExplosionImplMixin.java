package io.github.racoondog.explodium.mixin;

import io.github.racoondog.explodium.ExplodiumMixinPlugin;
import io.github.racoondog.explodium.IExplosion;
import net.caffeinemc.mods.lithium.common.util.Pos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.invoke.MethodHandles;

/**
 * Common injections used in other mixins.
 * @author Crosby
 */
@Mixin(ExplosionImpl.class)
public class ExplosionImplMixin implements IExplosion {
    @Shadow @Final private Vec3d pos;
    @Shadow @Final private ServerWorld world;

    @Unique private int originSectionX;
    @Unique private int originSectionY;
    @Unique private int originSectionZ;

    @Unique private boolean isOriginSectionEmpty;

    @Override
    public int explodium$getOriginSectionX() {
        return this.originSectionX;
    }

    @Override
    public int explodium$getOriginSectionY() {
        return this.originSectionY;
    }

    @Override
    public int explodium$getOriginSectionZ() {
        return this.originSectionZ;
    }

    @Override
    public boolean explodium$isOriginSectionEmpty() {
        return this.isOriginSectionEmpty;
    }

    @Override
    public boolean explodium$isSectionEmpty(int sectionX, int sectionY, int sectionZ) {
        Chunk chunk = world.getChunk(sectionX, sectionZ);
        ChunkSection section = chunk.getSection(Pos.SectionYIndex.fromSectionCoord(chunk, sectionY));
        return section.isEmpty();
    }

    @Inject(
        method = "explode",
        at = @At("HEAD")
    )
    private void initializeFields(CallbackInfo ci) {
        originSectionX = ChunkSectionPos.getSectionCoord(pos.x);
        originSectionY = ChunkSectionPos.getSectionCoord(pos.y);
        originSectionZ = ChunkSectionPos.getSectionCoord(pos.z);
        isOriginSectionEmpty = explodium$isSectionEmpty(originSectionX, originSectionY, originSectionZ);
    }

    @Inject(
        method = "<clinit>",
        at = @At("TAIL")
    )
    private static void captureLookup(CallbackInfo ci) {
        ExplodiumMixinPlugin.EXPLOSION_IMPL_LOOKUP = MethodHandles.lookup();
    }
}
