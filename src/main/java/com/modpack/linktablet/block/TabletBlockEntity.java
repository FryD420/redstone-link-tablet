package com.modpack.linktablet.block;

import com.modpack.linktablet.compat.VirtualTransmitter;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.registry.ModBlockEntities;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A mounted tablet. Stores the same app list and case color as the item,
 * and keeps {@link VirtualTransmitter}s registered on Create's link
 * network for every toggled-ON app — broadcasting from the block's own
 * position, for as long as the chunk is loaded. The LIT blockstate (and
 * with it the glowing screen model) tracks whether any app is on.
 */
public class TabletBlockEntity extends BlockEntity {

    private List<SignalApp> apps = List.of();
    @Nullable
    private DyeColor caseColor;

    /** Server-side transmitters keyed by frequency (max strength wins). */
    private final Map<Frequency, VirtualTransmitter> transmitters = new HashMap<>();

    public TabletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TABLET.get(), pos, state);
    }

    public List<SignalApp> getApps() {
        return apps;
    }

    @Nullable
    public DyeColor getCaseColor() {
        return caseColor;
    }

    public void setApps(List<SignalApp> newApps) {
        this.apps = List.copyOf(newApps);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        updateLit();
        refreshTransmitters();
    }

    /** Copies apps + case color from the tablet item being placed. */
    public void loadFromItem(ItemStack stack) {
        this.caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        setApps(stack.getOrDefault(ModDataComponents.TABLET_APPS.get(), List.of()));
    }

    /** Builds the tablet item this block turns back into. */
    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(ModItems.TABLET.get());
        if (!apps.isEmpty()) {
            stack.set(ModDataComponents.TABLET_APPS.get(), apps);
        }
        if (caseColor != null) {
            stack.set(ModDataComponents.CASE_COLOR.get(), caseColor);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Transmitters
    // ------------------------------------------------------------------

    private void refreshTransmitters() {
        if (!(level instanceof ServerLevel serverLevel)) return;

        Map<Frequency, Integer> wanted = new HashMap<>();
        for (SignalApp app : apps) {
            if (!app.active() || app.momentary()) continue;
            for (Frequency freq : app.frequencies()) {
                if (!freq.isEmpty()) {
                    wanted.merge(freq, app.strength(), Math::max);
                }
            }
        }

        Iterator<Map.Entry<Frequency, VirtualTransmitter>> it = transmitters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Frequency, VirtualTransmitter> entry = it.next();
            if (!wanted.containsKey(entry.getKey())) {
                entry.getValue().removeFromNetwork();
                it.remove();
            }
        }
        for (Map.Entry<Frequency, Integer> entry : wanted.entrySet()) {
            VirtualTransmitter transmitter = transmitters.get(entry.getKey());
            if (transmitter == null) {
                transmitter = new VirtualTransmitter(entry.getKey(), serverLevel, worldPosition, entry.getValue());
                Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, transmitter);
                transmitters.put(entry.getKey(), transmitter);
            } else {
                transmitter.update(serverLevel, worldPosition, entry.getValue());
            }
        }
    }

    private void clearTransmitters() {
        transmitters.values().forEach(VirtualTransmitter::removeFromNetwork);
        transmitters.clear();
    }

    private void updateLit() {
        if (level == null) return;
        BlockState state = getBlockState();
        if (!state.hasProperty(TabletBlock.LIT)) return;
        boolean lit = apps.stream().anyMatch(a -> a.active() && !a.momentary());
        if (state.getValue(TabletBlock.LIT) != lit) {
            level.setBlock(worldPosition, state.setValue(TabletBlock.LIT, lit), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshTransmitters();
    }

    @Override
    public void setRemoved() {
        clearTransmitters();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        clearTransmitters();
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------
    // Save / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!apps.isEmpty()) {
            SignalApp.CODEC.listOf().encodeStart(NbtOps.INSTANCE, apps)
                    .result().ifPresent(t -> tag.put("apps", t));
        }
        if (caseColor != null) {
            tag.putString("case_color", caseColor.getName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Tag appsTag = tag.get("apps");
        this.apps = appsTag == null ? List.of()
                : SignalApp.CODEC.listOf().parse(NbtOps.INSTANCE, appsTag).result().orElse(List.of());
        this.caseColor = tag.contains("case_color") ? DyeColor.byName(tag.getString("case_color"), null) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
