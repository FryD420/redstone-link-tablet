package com.modpack.linktablet.frequency;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.redstone.link.RedstoneLinkNetworkHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A two-item "frequency", identical in spirit to Create's Redstone Link
 * network: two items, order-independent, define a channel. A Tablet
 * "app" and a Signal Receiver block that share a frequency are linked.
 * <p>
 * Since 1.4.0 the frequency carries full {@link ItemStack}s (count
 * forced to 1) so component-bearing items — frequency cards and the
 * like — survive the round trip to Create's network. Channel identity
 * deliberately <b>delegates to Create's own
 * {@link RedstoneLinkNetworkHandler.Frequency}</b> (item + dyed-color
 * component, everything else ignored): if we distinguished stacks Create
 * doesn't, we could register two transmitters Create merges into one
 * channel — and we stay consistent with any mod that patches Create's
 * identity.
 */
public record Frequency(ItemStack stack1, ItemStack stack2) {

    public static final Frequency EMPTY = new Frequency(ItemStack.EMPTY, ItemStack.EMPTY);

    public Frequency {
        stack1 = normalize(stack1);
        stack2 = normalize(stack2);
    }

    private static ItemStack normalize(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
    }

    private static final Codec<Frequency> STACK_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack1", ItemStack.EMPTY).forGetter(Frequency::stack1),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("stack2", ItemStack.EMPTY).forGetter(Frequency::stack2)
    ).apply(instance, Frequency::new));

    /** Pre-1.4.0 format: two bare item IDs. Decoded forever, never written. */
    private static final Codec<Frequency> LEGACY_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item1")
                    .forGetter(f -> BuiltInRegistries.ITEM.getKey(f.stack1().getItem())),
            ResourceLocation.CODEC.fieldOf("item2")
                    .forGetter(f -> BuiltInRegistries.ITEM.getKey(f.stack2().getItem()))
    ).apply(instance, (item1, item2) -> new Frequency(
            new ItemStack(BuiltInRegistries.ITEM.get(item1)),
            new ItemStack(BuiltInRegistries.ITEM.get(item2)))));

    public static final Codec<Frequency> CODEC = Codec.withAlternative(STACK_CODEC, LEGACY_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, Frequency> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, Frequency::stack1,
            ItemStack.OPTIONAL_STREAM_CODEC, Frequency::stack2,
            Frequency::new
    );

    public static Frequency of(Item item1, Item item2) {
        return new Frequency(new ItemStack(item1), new ItemStack(item2));
    }

    public static Frequency of(ItemStack stack1, ItemStack stack2) {
        return new Frequency(stack1, stack2);
    }

    public ItemStack icon1() {
        return stack1.copy();
    }

    public ItemStack icon2() {
        return stack2.copy();
    }

    public boolean isEmpty() {
        return stack1.isEmpty() && stack2.isEmpty();
    }

    private static RedstoneLinkNetworkHandler.Frequency key(ItemStack stack) {
        return RedstoneLinkNetworkHandler.Frequency.of(stack);
    }

    /** Order-independent match — {A, B} matches {B, A}. */
    public boolean matches(Frequency other) {
        if (other == null) return false;
        RedstoneLinkNetworkHandler.Frequency a1 = key(stack1);
        RedstoneLinkNetworkHandler.Frequency a2 = key(stack2);
        RedstoneLinkNetworkHandler.Frequency b1 = key(other.stack1);
        RedstoneLinkNetworkHandler.Frequency b2 = key(other.stack2);
        return (a1.equals(b1) && a2.equals(b2)) || (a1.equals(b2) && a2.equals(b1));
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
        return key(stack1).hashCode() ^ key(stack2).hashCode();
    }
}
