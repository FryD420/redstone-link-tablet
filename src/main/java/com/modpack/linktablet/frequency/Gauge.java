package com.modpack.linktablet.frequency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * A single "gauge" saved on a tablet (1.10.0 OS suite): a READ-ONLY dial
 * that displays a value from some source. Gauges are a separate data
 * model from {@link Signal} on purpose (user decision) — signals write
 * to the world, gauges read from it, and future sources (velocity,
 * altitude, custom vehicle HUDs) won't be redstone at all.
 *
 * @param name      display name under the dial
 * @param source    where the value comes from (append-only enum)
 * @param frequency the Redstone Link frequency a {@link Source#LINK}
 *                  gauge listens on (ignored by future sources)
 * @param color     ARGB dial accent color
 * @param icon      optional custom icon item; when empty the frequency's
 *                  item pair stands in
 */
public record Gauge(String name, Source source, Frequency frequency, int color,
                    Optional<ResourceLocation> icon) {

    /**
     * Value source. Ordinal-coded on the wire and id-coded on disk:
     * append-only, never renumber. LINK (0) is the founding source —
     * listens on a Create Redstone Link frequency, shows received 0–15.
     */
    public enum Source {
        LINK(0, "link");

        private final byte id;
        private final String key;

        Source(int id, String key) {
            this.id = (byte) id;
            this.key = key;
        }

        public byte id() {
            return id;
        }

        public String key() {
            return key;
        }

        /** Unknown ids read as LINK — a downgrade shows a dead dial
         * rather than crashing. */
        public static Source byId(int id) {
            for (Source source : values()) {
                if (source.id == id) return source;
            }
            return LINK;
        }

        public static Source byKey(String key) {
            for (Source source : values()) {
                if (source.key.equals(key)) return source;
            }
            return LINK;
        }
    }

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_GAUGES = 8;
    public static final int MAX_VALUE = 15;
    public static final int DEFAULT_COLOR = 0xFF4ADE80;

    public static final Codec<Gauge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Gauge::name),
            Codec.STRING.optionalFieldOf("source", Source.LINK.key())
                    .forGetter(g -> g.source().key()),
            Frequency.CODEC.optionalFieldOf("frequency", Frequency.EMPTY).forGetter(Gauge::frequency),
            Codec.INT.optionalFieldOf("color", DEFAULT_COLOR).forGetter(Gauge::color),
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(Gauge::icon)
    ).apply(instance, (name, sourceKey, frequency, color, icon) ->
            new Gauge(name, Source.byKey(sourceKey), frequency, color, icon)));

    /** Hand-rolled from day one (the Signal lesson — composite tops out
     * around 6 fields). New fields go at the END of decode AND encode,
     * in the same order; every wire growth bumps the registrar. */
    public static final StreamCodec<RegistryFriendlyByteBuf, Gauge> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public Gauge decode(RegistryFriendlyByteBuf buf) {
            String name = ByteBufCodecs.STRING_UTF8.decode(buf);
            Source source = Source.byId(buf.readVarInt());
            Frequency frequency = Frequency.STREAM_CODEC.decode(buf);
            int color = buf.readInt();
            Optional<ResourceLocation> icon = buf.readOptional(FriendlyByteBuf::readResourceLocation);
            return new Gauge(name, source, frequency, color, icon);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf, Gauge gauge) {
            ByteBufCodecs.STRING_UTF8.encode(buf, gauge.name());
            buf.writeVarInt(gauge.source().id());
            Frequency.STREAM_CODEC.encode(buf, gauge.frequency());
            buf.writeInt(gauge.color());
            buf.writeOptional(gauge.icon(), FriendlyByteBuf::writeResourceLocation);
        }
    };

    /** Icon stack to render; falls back to the frequency's items. */
    public ItemStack iconStack() {
        return icon.map(id -> new ItemStack(BuiltInRegistries.ITEM.get(id)))
                .filter(s -> !s.isEmpty())
                .orElseGet(() -> frequency.anyIcon());
    }

    /** Sanitizes untrusted client input server-side. */
    public Gauge sanitized() {
        String cleanName = name.length() > MAX_NAME_LENGTH
                ? name.substring(0, MAX_NAME_LENGTH) : name;
        return new Gauge(cleanName.strip(), source, frequency, color, icon);
    }
}
