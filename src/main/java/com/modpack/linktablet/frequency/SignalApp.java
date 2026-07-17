package com.modpack.linktablet.frequency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A single "app" saved on a Tablet. An app is a scene button: activating
 * it broadcasts on <em>all</em> of its frequencies at once.
 *
 * @param name        display name shown under the tile
 * @param frequencies the Redstone Link frequencies this app transmits on
 *                    (1..{@link #MAX_FREQUENCIES}, all activated together)
 * @param active      current on/off state (persisted on the tablet item;
 *                    always false for momentary apps — their held state is
 *                    transient and server-side only)
 * @param momentary   true = transmits only while the button is held,
 *                    false = classic toggle
 * @param strength    transmitted signal strength — 1..15, except slider
 *                    apps where it doubles as the LIVE slider value
 *                    ({@code sliderMin}..{@code sliderMax})
 * @param color       ARGB tile background color
 * @param icon        optional custom icon item; when empty the first
 *                    frequency's item pair is drawn as the icon instead
 * @param slider      true = analog slider: output follows {@link #strength},
 *                    and {@code active} is DERIVED (strength &gt; 0), never
 *                    user-toggled — that keeps the transmitter collectors'
 *                    {@code active && !momentary} rule working unchanged
 * @param sliderMin   bottom of a slider's travel (0..14). A min above 0
 *                    means the slider can never rest at 0 — the app is
 *                    always transmitting, by design (no off notch)
 * @param sliderMax   top of a slider's travel (sliderMin+1..15)
 */
public record SignalApp(String name, List<Frequency> frequencies, boolean active, boolean momentary,
                        int strength, int color, Optional<ResourceLocation> icon, boolean slider,
                        int sliderMin, int sliderMax) {

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_FREQUENCIES = 8;
    public static final int MAX_STRENGTH = 15;
    public static final int DEFAULT_COLOR = 0xFF3A3F4B;

    public static final Codec<SignalApp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(SignalApp::name),
            Frequency.CODEC.listOf().fieldOf("frequencies").forGetter(SignalApp::frequencies),
            Codec.BOOL.fieldOf("active").forGetter(SignalApp::active),
            Codec.BOOL.optionalFieldOf("momentary", false).forGetter(SignalApp::momentary),
            Codec.INT.optionalFieldOf("strength", MAX_STRENGTH).forGetter(SignalApp::strength),
            Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(SignalApp::color),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(SignalApp::icon),
            Codec.BOOL.optionalFieldOf("slider", false).forGetter(SignalApp::slider),
            Codec.INT.optionalFieldOf("slider_min", 0).forGetter(SignalApp::sliderMin),
            Codec.INT.optionalFieldOf("slider_max", MAX_STRENGTH).forGetter(SignalApp::sliderMax)
    ).apply(instance, SignalApp::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Frequency>> FREQ_LIST_STREAM_CODEC =
            Frequency.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FREQUENCIES));

    /** Hand-rolled: too many fields for StreamCodec.composite. New fields
     * go at the END of decode AND encode, in the same order. */
    public static final StreamCodec<RegistryFriendlyByteBuf, SignalApp> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SignalApp decode(RegistryFriendlyByteBuf buf) {
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<Frequency> frequencies = FREQ_LIST_STREAM_CODEC.decode(buf);
            boolean active = buf.readBoolean();
            boolean momentary = buf.readBoolean();
            int strength = buf.readVarInt();
            int color = buf.readInt();
            Optional<ResourceLocation> icon = buf.readOptional(FriendlyByteBuf::readResourceLocation);
            boolean slider = buf.readBoolean();
            int sliderMin = buf.readVarInt();
            int sliderMax = buf.readVarInt();
            return new SignalApp(name, frequencies, active, momentary, strength, color, icon, slider,
                    sliderMin, sliderMax);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, SignalApp app) {
            ByteBufCodecs.STRING_UTF8.encode(buf, app.name());
            FREQ_LIST_STREAM_CODEC.encode(buf, app.frequencies());
            buf.writeBoolean(app.active());
            buf.writeBoolean(app.momentary());
            buf.writeVarInt(app.strength());
            buf.writeInt(app.color());
            buf.writeOptional(app.icon(), FriendlyByteBuf::writeResourceLocation);
            buf.writeBoolean(app.slider());
            buf.writeVarInt(app.sliderMin());
            buf.writeVarInt(app.sliderMax());
        }
    };

    public SignalApp withActive(boolean newActive) {
        return new SignalApp(name, frequencies, newActive, momentary, strength, color, icon, slider,
                sliderMin, sliderMax);
    }

    /** Slider apps: set the live value; {@code active} follows (value > 0).
     * Clamps into the app's range — the server authority against stale or
     * hostile payloads. */
    public SignalApp withSliderValue(int value) {
        int clean = Mth.clamp(value, sliderMin, sliderMax);
        return new SignalApp(name, frequencies, clean > 0, false, clean, color, icon, true,
                sliderMin, sliderMax);
    }

    /**
     * Slider travel fraction (0..1) → live value. The ONLY place a drag
     * position becomes a signal level — GUI drags, placed-tablet drags,
     * and click-to-set all call this.
     */
    public int valueFromFraction(float frac) {
        return Mth.clamp(Math.round(sliderMin + frac * (sliderMax - sliderMin)), sliderMin, sliderMax);
    }

    /**
     * Live value → travel fraction (0..1). The ONLY place a signal level
     * becomes a bar fill / knob position — all four slider render sites
     * call this. Numerals stay ABSOLUTE strength; fills are range-relative.
     */
    public float fillFraction() {
        return Mth.clamp((strength - sliderMin) / (float) (sliderMax - sliderMin), 0.0F, 1.0F);
    }

    /** First frequency, used as the default tile icon. */
    public Frequency primaryFrequency() {
        return frequencies.isEmpty() ? Frequency.EMPTY : frequencies.getFirst();
    }

    /** Icon stack to render; falls back to the first frequency's first
     * non-empty item (single-item frequencies may only fill slot 2). */
    public ItemStack iconStack() {
        return icon.map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id)))
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> primaryFrequency().anyIcon());
    }

    public boolean hasCustomIcon() {
        return icon.isPresent();
    }

    /** Sanitizes untrusted client input server-side. */
    public SignalApp sanitized() {
        String cleanName = name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
        List<Frequency> cleanFreqs = frequencies.stream()
                .filter(f -> !f.isEmpty())
                .distinct()
                .limit(MAX_FREQUENCIES)
                .toList();
        if (slider) {
            // Sliders can rest at their min (0 allowed); active is derived,
            // momentary excluded. Range: 0 <= min < max <= 15.
            int cleanMin = Mth.clamp(sliderMin, 0, MAX_STRENGTH - 1);
            int cleanMax = Mth.clamp(sliderMax, cleanMin + 1, MAX_STRENGTH);
            int cleanValue = Mth.clamp(strength, cleanMin, cleanMax);
            return new SignalApp(cleanName.strip(), cleanFreqs, cleanValue > 0, false,
                    cleanValue, color, icon, true, cleanMin, cleanMax);
        }
        // Momentary apps never persist an active state; non-sliders keep
        // the default 0/15 range so nothing extra persists.
        boolean cleanActive = !momentary && active;
        return new SignalApp(cleanName.strip(), cleanFreqs, cleanActive, momentary,
                Mth.clamp(strength, 1, MAX_STRENGTH), color, icon, false, 0, MAX_STRENGTH);
    }
}
