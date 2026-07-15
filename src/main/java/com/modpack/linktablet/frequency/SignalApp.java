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
 * @param strength    transmitted signal strength, 1..15
 * @param color       ARGB tile background color
 * @param icon        optional custom icon item; when empty the first
 *                    frequency's item pair is drawn as the icon instead
 */
public record SignalApp(String name, List<Frequency> frequencies, boolean active, boolean momentary,
                        int strength, int color, Optional<ResourceLocation> icon) {

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
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(SignalApp::icon)
    ).apply(instance, SignalApp::new));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<Frequency>> FREQ_LIST_STREAM_CODEC =
            Frequency.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_FREQUENCIES));

    /** Hand-rolled: too many fields for StreamCodec.composite. */
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
            return new SignalApp(name, frequencies, active, momentary, strength, color, icon);
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
        }
    };

    public SignalApp withActive(boolean newActive) {
        return new SignalApp(name, frequencies, newActive, momentary, strength, color, icon);
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
        // Momentary apps never persist an active state
        boolean cleanActive = !momentary && active;
        return new SignalApp(cleanName.strip(), cleanFreqs, cleanActive, momentary,
                Mth.clamp(strength, 1, MAX_STRENGTH), color, icon);
    }
}
