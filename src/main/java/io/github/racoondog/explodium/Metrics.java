package io.github.racoondog.explodium;

import com.google.common.util.concurrent.AtomicDouble;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.screen.ScreenTexts;
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
    private static final Text DIVIDER = Text.literal(" -------------------").formatted(Formatting.DARK_GRAY);
    private static final ConcurrentHashMap<EntityType<?>, EntityEntry> RAYS_PER_ENTITY = new ConcurrentHashMap<>();

    public static final AtomicLong explosions = new AtomicLong();
    public static final AtomicLong explosionsInEmptySection = new AtomicLong();
    public static final AtomicLong entityRaycasts = new AtomicLong();
    public static final AtomicLong entityRaycastsSkipped = new AtomicLong();
    public static final AtomicLong entityLowResRays = new AtomicLong();
    public static final AtomicLong entityRegularResRays = new AtomicLong();
    public static final AtomicDouble entityCoverResultTotal = new AtomicDouble();
    public static final AtomicLong entityCoverResultIsAbsolute = new AtomicLong();
    public static final AtomicLong entityCoverResultLowRes = new AtomicLong();
    public static final AtomicLong blockRaycasts = new AtomicLong();
    public static final AtomicLong blockRaycastsIntersectEmptySection = new AtomicLong();

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(literal("metrics")
            .then(literal("get").executes(ctx -> {
                double explosionEmptySectionPercent = ((double) explosionsInEmptySection.get() / explosions.get()) * 100;
                double entityRaycastPerExplosionAvg = (double) entityRaycasts.get() / explosions.get();
                double entityRaycastSkipPercent = ((double) entityRaycastsSkipped.get() / entityRaycasts.get()) * 100;
                long entityRays = entityLowResRays.get() + entityRegularResRays.get();
                double lowResRayPercent = ((double) entityLowResRays.get() / entityRays) * 100;
                double regularResRayPercent = ((double) entityRegularResRays.get() / entityRays) * 100;
                double raysPerExplosionAvg = (double) entityRays / explosions.get();
                double raysPerRaycastAvg = (double) entityRays / entityRaycasts.get();
                double entityCoverResultAvg = (entityCoverResultTotal.get() / entityRaycasts.get()) * 100;
                double entityCoverIsAbsolutePercent = ((double) entityCoverResultIsAbsolute.get() / entityRaycasts.get()) * 100;
                double entityRaycastLowResPercent = ((double) entityCoverResultLowRes.get() / entityRaycasts.get()) * 100;

                ctx.getSource().sendMessage(title("Explodium Metrics"));
                ctx.getSource().sendMessage(base().append(value(explosions)).append(" total explosions."));
                ctx.getSource().sendMessage(base().append(value(explosionsInEmptySection)).append(" explosions in empty sections, or ").append(value("%.2f%%", explosionEmptySectionPercent)).append("."));
                ctx.getSource().sendMessage(DIVIDER);
                ctx.getSource().sendMessage(base().append(value(entityRaycasts)).append(" total entity cover raycasts issued."));
                ctx.getSource().sendMessage(base().append(value("%.2f", entityRaycastPerExplosionAvg)).append(" average entity cover raycasts issued per explosion."));
                ctx.getSource().sendMessage(base().append(value(entityRaycastsSkipped)).append(" total entity cover raycasts skipped from empty sections, or ").append(value("%.2f%%", entityRaycastSkipPercent)).append("."));
                ctx.getSource().sendMessage(base().append(value(entityRays)).append(" total entity rays cast."));
                ctx.getSource().sendMessage(base().append(value(entityLowResRays)).append(" total low resolution entity rays cast, or ").append(value("%.2f%%", lowResRayPercent)).append("."));
                ctx.getSource().sendMessage(base().append(value(entityRegularResRays)).append(" total regular resolution entity rays cast, or ").append(value("%.2f%%", regularResRayPercent)).append("."));
                ctx.getSource().sendMessage(base().append(value("%.2f", raysPerExplosionAvg)).append(" average entity rays cast per explosion."));
                ctx.getSource().sendMessage(base().append(value("%.2f", raysPerRaycastAvg)).append(" average entity rays cast per entity."));
                ctx.getSource().sendMessage(base().append(value("%.2f%%", entityCoverResultAvg)).append(" average entity cover result."));
                ctx.getSource().sendMessage(base().append(value("%.2f%%", entityCoverIsAbsolutePercent)).append(" entity cover is absolute (full cover or no cover)."));
                ctx.getSource().sendMessage(base().append(value(entityCoverResultLowRes)).append(" entity cover raycasts done with lowered resolution, or ").append(value("%.2f%%", entityRaycastLowResPercent)).append("."));
                ctx.getSource().sendMessage(DIVIDER);
                ctx.getSource().sendMessage(base().append(value(blockRaycasts)).append(" total block raycasts."));
                ctx.getSource().sendMessage(base().append(value(blockRaycastsIntersectEmptySection)).append(" times block raycasts intersected with empty sections."));

                return SINGLE_SUCCESS;
            }))
            .then(literal("getEntityRaycasts").executes(ctx -> {
                ctx.getSource().sendMessage(title("Entity Raycasts Issued"));

                RAYS_PER_ENTITY.forEach((key, entry) -> {
                    MutableText text = base()
                        .append(value(entry.normalResSamplePoints()))
                        .append("/")
                        .append(value(entry.lowResSamplePoints()).formatted(Formatting.YELLOW))
                        .append(" rays per ")
                        .append(key.getName())
                        .append(" entity (")
                        .append(value(entry.occurrences()))
                        .append(ScreenTexts.SPACE)
                        .append(entry.occurrences().get() == 1 ? "occurence" : " occurences")
                        .append(".)");

                    ctx.getSource().sendMessage(text);
                });

                return SINGLE_SUCCESS;
            }))
        ));
    }

    public static void entityRaycastCallback(EntityType<?> entityType, float result) {
        entityRaycasts.getAndIncrement();
        RAYS_PER_ENTITY.computeIfAbsent(entityType, Metrics::createEntityEntry).occurrences().getAndIncrement();
        entityCoverResultTotal.getAndAdd(result);
        if (result == 0f || result == 1f) {
            entityCoverResultIsAbsolute.getAndIncrement();
        }
    }

    private static MutableText base() {
        return Text.empty().setStyle(Style.EMPTY.withColor(Formatting.GRAY));
    }

    private static MutableText title(String text) {
        return base()
            .append(Text.literal(text).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(16351261)).withUnderline(true)))
            .append(":");
    }

    private static MutableText value(String pattern, Object... arguments) {
        return value(String.format(pattern, arguments));
    }

    private static MutableText value(@Nullable Object object) {
        return Text.literal(String.valueOf(object))
            .setStyle(Style.EMPTY.withColor(Formatting.GREEN));
    }

    private static <T extends Entity> EntityEntry createEntityEntry(EntityType<T> entityType) {
        EntityDimensions dimensions = entityType.getDimensions();

        int normalResSamplePoints = getSamplePoints(dimensions.width(), dimensions.height());
        int lowResSamplePoints = getLowResSamplePoints(dimensions.width(), dimensions.height());

        return new EntityEntry(normalResSamplePoints, lowResSamplePoints, new AtomicLong());
    }

    public static int getSamplePoints(double width, double height) {
        double xStep = 1.0 / (width * 2.0 + 1.0);
        double yStep = 1.0 / (height * 2.0 + 1.0);
        double zStep = 1.0 / (width * 2.0 + 1.0);

        return MathHelper.ceil(1 / xStep) * MathHelper.ceil(1 / yStep) * MathHelper.ceil(1 / zStep);
    }

    public static int getLowResSamplePoints(double width, double height) {
        double xStep = 1.0 / (width * 2.0 + 1.0);
        double yStep = 1.0 / (height * 2.0 + 1.0);
        double zStep = 1.0 / (width * 2.0 + 1.0);

        double xStepLowRes = Math.min(1.0, xStep * 3.0 / 2.0);
        double yStepLowRes = Math.min(1.0, yStep * 3.0 / 2.0);
        double zStepLowRes = Math.min(1.0, zStep * 3.0 / 2.0);

        return MathHelper.ceil(1 / xStepLowRes) * MathHelper.ceil(1 / yStepLowRes) * MathHelper.ceil(1 / zStepLowRes);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<ServerCommandSource, T> argument(String name, ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private record EntityEntry(int normalResSamplePoints, int lowResSamplePoints, AtomicLong occurrences) { }
}
