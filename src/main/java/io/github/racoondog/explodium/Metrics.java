package io.github.racoondog.explodium;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;

public class Metrics {
    private static final ConcurrentHashMap<EntityType<?>, EntityEntry> RAYS_PER_ENTITY = new ConcurrentHashMap<>();
    public static final boolean CAPTURE_METRICS = FabricLoader.getInstance().isDevelopmentEnvironment() || Boolean.getBoolean("explodium.metrics");

    public static final AtomicLong explosions = new AtomicLong();
    public static final AtomicLong explosionsInEmptySection = new AtomicLong();
    public static final AtomicLong entityRaycasts = new AtomicLong();
    public static final AtomicLong entityRaycastsSkipped = new AtomicLong();
    public static final AtomicLong entityRays = new AtomicLong();
    public static final AtomicDouble entityRaycastResultTotal = new AtomicDouble();
    public static final AtomicLong entityRaycastResultIsAbsolute = new AtomicLong();
    public static final AtomicLong entityRaycastResultLowRes = new AtomicLong();
    public static final AtomicLong blockRaycasts = new AtomicLong();
    public static final AtomicLong blockRaycastsIntersectEmptySection = new AtomicLong();

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("metrics")
            .then(literal("get").executes(ctx -> {
                double explosionEmptySectionPercent = ((double) explosionsInEmptySection.get() / explosions.get()) * 100;
                double entityRaycastPerExplosionAvg = (double) entityRaycasts.get() / explosions.get();
                double entityRaycastSkipPercent = ((double) entityRaycastsSkipped.get() / entityRaycasts.get()) * 100;
                double raysPerExplosionAvg = (double) entityRays.get() / explosions.get();
                double raysPerRaycastAvg = (double) entityRays.get() / entityRaycasts.get();
                double entityRaycastResultAvg = (entityRaycastResultTotal.get() / entityRaycasts.get()) * 100;
                double entityRaycastIsAbsolutePercent = ((double) entityRaycastResultIsAbsolute.get() / entityRaycasts.get()) * 100;
                double entityRaycastLowResPercent = ((double) entityRaycastResultLowRes.get() / entityRaycasts.get()) * 100;

                ctx.getSource().sendMessage(title("Explodium Metrics"));
                ctx.getSource().sendMessage(base().append(value(explosions)).append(" total explosions."));
                ctx.getSource().sendMessage(base().append(value(explosionsInEmptySection)).append(" explosions in empty sections, or ").append(value("%.2f%%", explosionEmptySectionPercent)).append("."));
                ctx.getSource().sendMessage(divider());
                ctx.getSource().sendMessage(base().append(value(entityRaycasts)).append(" total entity raycasts issued."));
                ctx.getSource().sendMessage(base().append(value("%.2f", entityRaycastPerExplosionAvg)).append(" entity raycasts issued per explosion average."));
                ctx.getSource().sendMessage(base().append(value(entityRaycastsSkipped)).append(" total entity raycasts skipped from empty sections, or ").append(value("%.2f%%", entityRaycastSkipPercent)).append("."));
                ctx.getSource().sendMessage(base().append(value(entityRays)).append(" total entity rays cast."));
                ctx.getSource().sendMessage(base().append(value("%.2f", raysPerExplosionAvg)).append(" entity rays cast per explosion average."));
                ctx.getSource().sendMessage(base().append(value("%.2f", raysPerRaycastAvg)).append(" entity rays cast per entity average."));
                ctx.getSource().sendMessage(base().append(value("%.2f%%", entityRaycastResultAvg)).append(" entity raycast average result."));
                ctx.getSource().sendMessage(base().append(value("%.2f%%", entityRaycastIsAbsolutePercent)).append(" entity raycast result is absolute (full cover or no cover)."));
                ctx.getSource().sendMessage(base().append(value(entityRaycastResultLowRes)).append(" entity raycasts done with lowered resolution, or ").append(value("%.2f%%", entityRaycastLowResPercent)).append("."));
                ctx.getSource().sendMessage(divider());
                ctx.getSource().sendMessage(base().append(value(blockRaycasts)).append(" total block raycasts."));
                ctx.getSource().sendMessage(base().append(value(blockRaycastsIntersectEmptySection)).append(" times block raycasts intersected with empty sections."));

                return SINGLE_SUCCESS;
            }))
            .then(literal("getEntityRaycasts").executes(ctx -> {
                ctx.getSource().sendMessage(title("Entity Raycasts Issued"));

                RAYS_PER_ENTITY.forEach((key, entry) -> {
                    ctx.getSource().sendMessage(base()
                        .append(value(entry.normalResSamplePoints()))
                        .append("/")
                        .append(value(entry.lowResSamplePoints()))
                        .append(" rays per ")
                        .append(key.getName())
                        .append(" entity (")
                        .append(value(entry.occurrences()))
                        .append(" occurrences.)"));
                });

                return SINGLE_SUCCESS;
            }))
        ));
    }

    public static void entityRaycastCallback(EntityType<?> entityType, float result) {
        entityRaycasts.getAndIncrement();
        RAYS_PER_ENTITY.computeIfAbsent(entityType, Metrics::createEntityEntry).occurrences().getAndIncrement();
        entityRaycastResultTotal.getAndAdd(result);
        if (result == 0f || result == 1f) {
            entityRaycastResultIsAbsolute.getAndIncrement();
        }
    }

    private static Text divider() {
        return Text.literal(" -------------------").formatted(Formatting.DARK_GRAY);
    }

    private static MutableText base() {
        return Text.empty().setStyle(Style.EMPTY.withColor(Formatting.GRAY));
    }

    private static Text title(String text) {
        return base()
            .append(Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(16351261)).withUnderline(true)))
            .append(":");
    }

    private static Text value(String pattern, Object... arguments) {
        return value(String.format(pattern, arguments));
    }

    private static Text value(@Nullable Object object) {
        return Text.literal(String.valueOf(object))
            .setStyle(Style.EMPTY.withColor(Formatting.GREEN));
    }

    private static <T extends Entity> EntityEntry createEntityEntry(EntityType<T> entityType) {
        EntityDimensions dimensions = entityType.getDimensions();
        double xStep = 1.0 / (dimensions.width() * 2.0 + 1.0);
        double yStep = 1.0 / (dimensions.height() * 2.0 + 1.0);
        double zStep = 1.0 / (dimensions.width() * 2.0 + 1.0);

        double xStepLowRes = Math.min(1.0, xStep * 3.0 / 2.0);
        double yStepLowRes = Math.min(1.0, yStep * 3.0 / 2.0);
        double zStepLowRes = Math.min(1.0, zStep * 3.0 / 2.0);

        int normalResSamplePoints = MathHelper.ceil(1 / xStep) * MathHelper.ceil(1 / yStep) * MathHelper.ceil(1 / zStep);
        int lowResSamplePoints = MathHelper.ceil(1 / xStepLowRes) * MathHelper.ceil(1 / yStepLowRes) * MathHelper.ceil(1 / zStepLowRes);

        return new EntityEntry(normalResSamplePoints, lowResSamplePoints, new AtomicLong());
    }

    private static LiteralArgumentBuilder<ServerCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<ServerCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private record EntityEntry(int normalResSamplePoints, int lowResSamplePoints, AtomicLong occurrences) { }
}
