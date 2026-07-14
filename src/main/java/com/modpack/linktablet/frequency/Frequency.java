package com.modpack.linktablet.frequency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * A two-item "frequency", identical in spirit to Create's Redstone Link
 * network: two item IDs, order-independent, define a channel. A Tablet
 * "app" and a Signal Receiver block that share a frequency are linked.
 */
public record Frequency(ResourceLocation item1, ResourceLocation item2) {

    public static final Frequency EMPTY = new Frequency(
            ResourceLocation.withDefaultNamespace("air"),
            ResourceLocation.withDefaultNamespace("air")
    );

    public static final Codec<Frequency> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item1").forGetter(Frequency::item1),
            ResourceLocation.CODEC.fieldOf("item2").forGetter(Frequency::item2)
    ).apply(instance, Frequency::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Frequency> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, Frequency::item1,
            ResourceLocation.STREAM_CODEC, Frequency::item2,
            Frequency::new
    );

    public static Frequency of(Item item1, Item item2) {
        return new Frequency(BuiltInRegistries.ITEM.getKey(item1), BuiltInRegistries.ITEM.getKey(item2));
    }

    public ItemStack icon1() {
        return new ItemStack(BuiltInRegistries.ITEM.get(item1));
    }

    public ItemStack icon2() {
        return new ItemStack(BuiltInRegistries.ITEM.get(item2));
    }

    public boolean isEmpty() {
        return item1.equals(EMPTY.item1) && item2.equals(EMPTY.item2);
    }

    /** Order-independent match — {A, B} matches {B, A}. */
    public boolean matches(Frequency other) {
        if (other == null) return false;
        return (item1.equals(other.item1) && item2.equals(other.item2))
                || (item1.equals(other.item2) && item2.equals(other.item1));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Frequency other)) return false;
        return matches(other);
    }

    @Override
    public int hashCode() {
        // Order-independent hash so {A,B} and {B,A} land in the same bucket.
        return Objects.hashCode(item1) ^ Objects.hashCode(item2);
    }
}
