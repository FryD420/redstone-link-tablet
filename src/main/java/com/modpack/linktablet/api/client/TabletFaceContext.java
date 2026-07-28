package com.modpack.linktablet.api.client;

import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Drawing surface for a {@link TabletFacePainter}. Coordinates are
 * GLASS TEXELS: (0,0) is the screen's top-left, {@link #width()} ×
 * {@link #height()} its extent (a standalone tablet's glass is 12×10;
 * merged surfaces and content rotation change it — always lay out
 * relative to the reported size). One block face is 16 texels.
 *
 * <p>Calls are buffered and flushed after {@code paint} returns; within
 * each kind, later calls draw on top of earlier ones.
 */
public interface TabletFaceContext {

    /** Glass width in texels (content-rotation aware). */
    float width();

    /** Glass height in texels (content-rotation aware). */
    float height();

    /** The tablet's UI theme — use its colors to match the OS. */
    ScreenTheme theme();

    /** True while the screen is backlit (someone is near / it's lit). */
    boolean backlit();

    /**
     * The tablet's block entity (a merged surface reports its
     * controller). Addons wanting per-tablet face state can hang their
     * own NeoForge data attachments on it.
     */
    BlockEntity blockEntity();

    /** Frame partial tick, for animation. */
    float partialTick();

    /** Filled rectangle; {@code argb} alpha is forced opaque. */
    void fill(float u, float v, float w, float h, int argb);

    /** Item model, centered on (centerU, centerV), {@code size} texels tall. */
    void item(ItemStack stack, float centerU, float centerV, float size);

    /**
     * A line of text, {@code height} texels tall. {@code u} is the
     * horizontal CENTER when {@code centered}, else the left edge;
     * {@code outline} draws a black outline for text on busy colors.
     */
    void text(String text, float u, float top, float height, int argb,
              boolean centered, boolean outline);

    /** Centered, un-outlined {@link #text}. */
    default void text(String text, float centerU, float top, float height, int argb) {
        text(text, centerU, top, height, argb, true, false);
    }
}
