package com.modpack.linktablet.client;

import net.minecraft.client.gui.Font;
import net.minecraft.util.Mth;

/** Label fitting: ellipsize for GUI pixels, scale-to-fit for world text. */
public final class TextFit {

    /** Vanilla-font-safe ellipsis (the single-glyph … is spotty in resource packs). */
    public static final String ELLIPSIS = "...";

    private TextFit() {
    }

    /** The text unchanged if it fits, else truncated with a trailing {@link #ELLIPSIS}. */
    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int budget = maxWidth - font.width(ELLIPSIS);
        if (budget <= 0) return ELLIPSIS;
        return font.plainSubstrByWidth(text, budget) + ELLIPSIS;
    }

    /** Scale factor that makes the text fit maxWidth, clamped to [minScale, 1]. */
    public static float fitScale(Font font, String text, float maxWidth, float minScale) {
        int w = font.width(text);
        if (w <= 0) return 1f;
        return Mth.clamp(maxWidth / w, minScale, 1f);
    }
}
