package com.modpack.linktablet.block;

import com.modpack.linktablet.compat.VirtualTransmitter;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import com.modpack.linktablet.registry.ModBlockEntities;
import com.modpack.linktablet.registry.ModDataComponents;
import com.modpack.linktablet.registry.ModItems;
import com.modpack.linktablet.theme.ScreenTheme;
import com.simibubi.create.Create;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    /** Physical mini-screen layout: true = switch list, false = pip grid. */
    private boolean screenList;
    /** UI theme; DARK is the default and is never persisted. */
    private ScreenTheme theme = ScreenTheme.DARK;

    /** Server-side transmitters keyed by frequency (max strength wins). */
    private final Map<Frequency, VirtualTransmitter> transmitters = new HashMap<>();

    /**
     * Momentary pips currently held down, purely for the screen visual.
     * Transient: synced to clients via the update tag but never written
     * to disk, so a crash can't leave a pip stuck lit.
     */
    private final Set<Integer> heldPips = new HashSet<>();

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

    public boolean isScreenList() {
        return screenList;
    }

    public void setScreenList(boolean screenList) {
        if (this.screenList == screenList) return;
        this.screenList = screenList;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public Set<Integer> getHeldPips() {
        return heldPips;
    }

    /** Lights/unlights one momentary pip on the screen (server side). */
    public void setPipHeld(int index, boolean held) {
        boolean changed = held ? heldPips.add(index) : heldPips.remove(index);
        if (changed) syncHeldPips();
    }

    /** Drops every held-pip visual (app reorder invalidates indices). */
    public void clearHeldPips() {
        if (heldPips.isEmpty()) return;
        heldPips.clear();
        syncHeldPips();
    }

    private void syncHeldPips() {
        // No setChanged: nothing to persist, only clients care
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public ScreenTheme getTheme() {
        return theme;
    }

    public void setTheme(ScreenTheme theme) {
        if (this.theme == theme) return;
        this.theme = theme;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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

    /** Copies apps, case color, screen layout, and theme from the placed item. */
    public void loadFromItem(ItemStack stack) {
        this.caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        this.screenList = stack.getOrDefault(ModDataComponents.SCREEN_LIST.get(), false);
        this.theme = stack.getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
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
        if (screenList) {
            stack.set(ModDataComponents.SCREEN_LIST.get(), true);
        }
        if (theme != ScreenTheme.DARK) {
            stack.set(ModDataComponents.THEME.get(), theme);
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
        if (screenList) {
            tag.putBoolean("screen_list", true);
        }
        if (theme != ScreenTheme.DARK) {
            tag.putString("theme", theme.getSerializedName());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Tag appsTag = tag.get("apps");
        this.apps = appsTag == null ? List.of()
                : SignalApp.CODEC.listOf().parse(NbtOps.INSTANCE, appsTag).result().orElse(List.of());
        this.caseColor = tag.contains("case_color") ? DyeColor.byName(tag.getString("case_color"), null) : null;
        this.screenList = tag.getBoolean("screen_list");
        this.theme = ScreenTheme.byName(tag.getString("theme"));
        // Only ever present in sync tags (see getUpdateTag) — a disk load
        // always clears the transient held-pip visuals.
        heldPips.clear();
        for (int index : tag.getIntArray("held_pips")) {
            heldPips.add(index);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        if (!heldPips.isEmpty()) {
            tag.putIntArray("held_pips", heldPips.stream().mapToInt(Integer::intValue).toArray());
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt,
                             HolderLookup.Provider registries) {
        super.onDataPacket(net, pkt, registries);
        // The chunk mesh bakes the case tint, but on placement it is
        // built before this data arrives — request a re-render so the
        // dyed bezel shows immediately instead of on the next update.
        if (level != null && level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 8);
        }
    }
}
