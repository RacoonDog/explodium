package io.github.racoondog.explodium;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class Explodium implements ModInitializer {
	public static final Logger LOGGER = LogUtils.getLogger();

	@Override
	public void onInitialize() {
		if (ExplodiumMixinPlugin.CAPTURE_METRICS) {
			Metrics.registerCommands();
		}
	}
}