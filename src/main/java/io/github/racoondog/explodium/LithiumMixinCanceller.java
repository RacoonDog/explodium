package io.github.racoondog.explodium;

import com.bawnorton.mixinsquared.api.MixinCanceller;

import java.util.List;

public class LithiumMixinCanceller implements MixinCanceller {
    @Override
    public boolean shouldCancel(List<String> list, String s) {
        return s.equals("net.caffeinemc.mods.lithium.mixin.world.explosions.entity_raycast.ServerExplosionMixin");
    }
}
