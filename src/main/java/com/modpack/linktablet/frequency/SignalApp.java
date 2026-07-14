package com.modpack.linktablet.frequency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * A single "app" saved on a Tablet. An app is a scene button: toggling it
 * broadcasts on <em>all</em> of its frequencies at once.
 *
 * @param name        display name shown under the tile
 * @param frequencies the Redstone Link frequencies this app transmits on
 *                    (1..{@link #MAX_FREQUENCIES}, all toggled together)
 * @param active      current on/off state (persisted on the tablet item)
 * @param color       ARGB tile background color
 * @param icon        optional custom icon item; when empty the first
 *                    frequency's item pair is drawn as the icon instead
 */
public record SignalApp(String name, List<Frequency> frequencies, boolean active, int color,
                        Optional<ResourceLocation> icon) {

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_FREQUENCIES = 8;
    public static final int DEFAULT_COLOR = 0xFF3A3F4B;

    public static final Codec<SignalApp> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(SignalApp::name),
            Frequency.CODEC.listOf().fieldOf("frequencies").forGetter(SignalApp::frequencies),
            Codec.BOOL.fieldOf("active").forGetter(SignalApp::active),
            Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(SignalApp::color),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(SignalApp::icon)
    ).apply(instance, SignalApp::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalApp> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SignalApp::name,
            Frequency.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FREQUENCIES)), SignalApp::frequencies,
            ByteBufCodecs.BOOL, SignalApp::active,
            ByteBufCodecs.INT, SignalApp::color,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), SignalApp::icon,
            SignalApp::new
    );

    public SignalApp withActive(boolean newActive) {
        return new SignalApp(name, frequencies, newActive, color, icon);
    }

    /** First frequency, used as the default tile icon. */
    public Frequency primaryFrequency() {
        return frequencies.isEmpty() ? Frequency.EMPTY : frequencies.getFirst();
    }

    /** Icon stack to render; falls back to the first frequency item. */
    public ItemStack iconStack() {
        return icon.map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id)))
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> primaryFrequency().icon1());
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
        return new SignalApp(cleanName.strip(), cleanFreqs, active, color, icon);
    }
}
