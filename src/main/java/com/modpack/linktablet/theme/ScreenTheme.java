package com.modpack.linktablet.theme;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Named UI palette for a tablet, stored per-tablet (data component / BE
 * field) so a themed tablet looks themed to everyone, including its
 * placed mini-screen. {@link #DARK} is the default and must stay
 * byte-identical to the original hardcoded colors — it is never
 * persisted, so 1.2.x tablets are untouched.
 * <p>
 * PURPLE was suggested by player PurpleFox.
 */
public enum ScreenTheme implements StringRepresentable {

    DARK("dark",
            0xFF223044, 0xFF12151B, 0xFF2A2E37,
            0xFF16181D, 0xFF23262E,
            0xFF2C303A, 0xFF353A46, 0xFF262A33, 0xFF3A3F4B,
            0xFFE2E5EB, 0xFF9AA0AC, 0xFF565D6B, 0xFFB0B6C2,
            0xFF4ADE80, 0xFF2F855A, 0xFF444955),

    LIGHT("light",
            0xFFD8DCE4, 0xFFB8BCC6, 0xFFC4C9D3,
            0xFFC9CDD6, 0xFFE4E7ED,
            0xFFD3D7DF, 0xFFC4C9D3, 0xFFDDE0E7, 0xFFBFC5CF,
            0xFF1E2126, 0xFF5A616D, 0xFF8A919E, 0xFF3A414D,
            0xFF2F855A, 0xFF9ED9BC, 0xFF9AA0AC),

    AMOLED("amoled",
            0xFF000000, 0xFF000000, 0xFF141414,
            0xFF000000, 0xFF0A0A0A,
            0xFF111111, 0xFF1C1C1C, 0xFF0E0E0E, 0xFF222222,
            0xFFF2F2F2, 0xFF9A9A9A, 0xFF5A5A5A, 0xFFCFCFCF,
            0xFF4ADE80, 0xFF2F855A, 0xFF2A2A2A),

    BRASS("brass",
            0xFF4A3722, 0xFF2E2115, 0xFF54401F,
            0xFF3B2B18, 0xFF56411F,
            0xFF6B5327, 0xFF7D6230, 0xFF5F4A23, 0xFF8A6D36,
            0xFFF3E1B4, 0xFFC9AE7A, 0xFF96814F, 0xFFE4CD96,
            0xFFE0AA46, 0xFF9C742C, 0xFF3C2E1B),

    TERMINAL("terminal",
            0xFF04180A, 0xFF020C05, 0xFF0C2414,
            0xFF06140B, 0xFF0A2113,
            0xFF10301C, 0xFF17402A, 0xFF0D2817, 0xFF1D4C32,
            0xFF6CF29A, 0xFF3E9C64, 0xFF2A6B45, 0xFF9AF7BC,
            0xFF4ADE80, 0xFF2F855A, 0xFF1B3A28),

    PURPLE("purple",
            0xFF2A1F44, 0xFF160F26, 0xFF352852,
            0xFF1D1430, 0xFF2E2249,
            0xFF3C2E5E, 0xFF49386F, 0xFF352852, 0xFF554382,
            0xFFEDE4FF, 0xFFB3A3D6, 0xFF7A6BA0, 0xFFD5C8F0,
            0xFFB07CFF, 0xFF7A4FC0, 0xFF463862),

    /** 1.5.0 "Parchment": the Stock-Keeper-styled flagship palette —
     * cream surfaces, dark-brown text, Create-blue accent. */
    CREATE("create",
            0xFFF2E8C9, 0xFF57493A, 0xFFE3D5AE,
            0xFF54402C, 0xFFF2E8C9,
            0xFFEADFBE, 0xFFF7EFD6, 0xFFDECFA8, 0xFFF7EFD6,
            0xFF3B2A18, 0xFF6E5B40, 0xFF9C8A68, 0xFF4F3A22,
            0xFF2E9DD8, 0xFF1F6E9C, 0xFFC9B98F),

    /** 1.5.2 "Avionics": flight-computer terminal look requested by the
     * testers — deep navy panels, steel-blue frames, cyan accent. */
    AVIONICS("avionics",
            0xFF16294A, 0xFF090E1A, 0xFF20335A,
            0xFF44618F, 0xFF0D1526,
            0xFF16243E, 0xFF20345A, 0xFF101B30, 0xFF28406B,
            0xFFC5D7F2, 0xFF7E95BC, 0xFF48587A, 0xFFA6C1E8,
            0xFF3EC6E8, 0xFF1E6E96, 0xFF24355A);

    public static final Codec<ScreenTheme> CODEC = StringRepresentable.fromEnum(ScreenTheme::values);
    /** ByteBuf-based, so it slots into both component sync and payload composites.
     * Ordinal-based: append-only, and appending a constant GROWS the wire
     * domain — that is only safe because the network registrar version in
     * ModNetworking fences client/server pairs. Bump the registrar every
     * time a constant is added (see the "8" bump for CREATE). */
    public static final StreamCodec<io.netty.buffer.ByteBuf, ScreenTheme> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], ScreenTheme::ordinal);

    private final String id;

    /** Physical mini-screen: backlit background, off background, list-row track. */
    public final int screenBgLit;
    public final int screenBgOff;
    public final int screenTrack;

    /** GUI tablet body: outer frame, inner panel. */
    public final int bodyOuter;
    public final int bodyInner;

    /** GUI rows/tiles: base, hovered, recessed (add/placeholder), raised (hovered add). */
    public final int rowBg;
    public final int rowBgHover;
    public final int surfaceLo;
    public final int surfaceHi;

    /** Text: primary, muted (labels, off-knobs), faint (idle glyphs/hints), hovered glyphs. */
    public final int textPrimary;
    public final int textMuted;
    public final int textFaint;
    public final int glyphHover;

    /** Accent: bright (knobs, glow, active glyphs), dim (on-track), off-switch body. */
    public final int accent;
    public final int accentDim;
    public final int switchOff;

    /**
     * Whether GUI text draws Minecraft's drop shadow. Dark themes keep it
     * (the pre-1.3.0 look); light backgrounds (LIGHT, CREATE's parchment)
     * turn it off — a dark shadow under dark text reads as a smeared
     * double-print.
     */
    public final boolean textShadow;

    ScreenTheme(String id,
                int screenBgLit, int screenBgOff, int screenTrack,
                int bodyOuter, int bodyInner,
                int rowBg, int rowBgHover, int surfaceLo, int surfaceHi,
                int textPrimary, int textMuted, int textFaint, int glyphHover,
                int accent, int accentDim, int switchOff) {
        this.textShadow = !"light".equals(id) && !"create".equals(id);
        this.id = id;
        this.screenBgLit = screenBgLit;
        this.screenBgOff = screenBgOff;
        this.screenTrack = screenTrack;
        this.bodyOuter = bodyOuter;
        this.bodyInner = bodyInner;
        this.rowBg = rowBg;
        this.rowBgHover = rowBgHover;
        this.surfaceLo = surfaceLo;
        this.surfaceHi = surfaceHi;
        this.textPrimary = textPrimary;
        this.textMuted = textMuted;
        this.textFaint = textFaint;
        this.glyphHover = glyphHover;
        this.accent = accent;
        this.accentDim = accentDim;
        this.switchOff = switchOff;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Component displayName() {
        return Component.translatable("gui.linktablet.theme." + id);
    }

    public static ScreenTheme byName(String name) {
        for (ScreenTheme theme : values()) {
            if (theme.id.equals(name)) return theme;
        }
        return DARK;
    }
}
