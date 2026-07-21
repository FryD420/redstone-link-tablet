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
    /**
     * Solo screen (1.7.0): the GUI's link toggle. A solo tablet never
     * joins a merged surface — the scanner's flood skips it entirely.
     * Block-only, like the surface roles: never travels on the item.
     */
    private boolean soloScreen;
    /** UI theme; DARK is the default and is never persisted. */
    private ScreenTheme theme = ScreenTheme.DARK;
    /** Screen content rotation, quarter turns CW; 0 is never persisted. */
    private int screenRotation;

    // Multiblock surface role (1.7.0). Offsets run along screenRight/
    // screenDown to the controller; (0,0) = controller or standalone.
    // Dims live on the controller; (1,1) = standalone. Roles are
    // assigned by TabletSurfaceScanner, synced via the update tag, and
    // NEVER travel on the item (toItemStack/loadFromItem ignore them).
    private byte surfaceDx, surfaceDy;
    private byte surfaceW = 1, surfaceH = 1;

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

    public boolean isSoloScreen() {
        return soloScreen;
    }

    public void setSoloScreen(boolean soloScreen) {
        if (this.soloScreen == soloScreen) return;
        this.soloScreen = soloScreen;
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

    public int getScreenRotation() {
        return screenRotation;
    }

    /** One wrench click: turn the screen content a quarter turn CW. */
    public void rotateScreen() {
        screenRotation = (screenRotation + 1) & 3;
        setChanged();
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

    /** Copies apps, case color, screen layout, theme, and rotation from the placed item. */
    public void loadFromItem(ItemStack stack) {
        this.caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        this.screenList = stack.getOrDefault(ModDataComponents.SCREEN_LIST.get(), false);
        this.theme = stack.getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        this.screenRotation = stack.getOrDefault(ModDataComponents.SCREEN_ROTATION.get(), 0) & 3;
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
        if (screenRotation != 0) {
            stack.set(ModDataComponents.SCREEN_ROTATION.get(), screenRotation);
        }
        return stack;
    }

    // ------------------------------------------------------------------
    // Multiblock surface role (1.7.0)
    // ------------------------------------------------------------------

    /** Whether this tablet is a non-controller member of a merged surface. */
    public boolean isSurfacePart() {
        return surfaceDx != 0 || surfaceDy != 0;
    }

    /** Whether this tablet is the controller of a multi-member surface. */
    public boolean isSurfaceController() {
        return !isSurfacePart() && (surfaceW > 1 || surfaceH > 1);
    }

    public boolean isMerged() {
        return isSurfacePart() || isSurfaceController();
    }

    public int getSurfaceDx() {
        return surfaceDx;
    }

    public int getSurfaceDy() {
        return surfaceDy;
    }

    public int getSurfaceW() {
        return surfaceW;
    }

    public int getSurfaceH() {
        return surfaceH;
    }

    public int memberCount() {
        return surfaceW * surfaceH;
    }

    /** App cap: every merged member adds a full tablet's worth. */
    public int maxApps() {
        return com.modpack.linktablet.network.ModNetworking.MAX_APPS * memberCount();
    }

    /**
     * Content rotation the surface actually renders/hit-tests with.
     * Merged surfaces support 180° flips always, and full 90° steps
     * only when SQUARE (a rotated oblong logical glass can't map onto
     * the fixed physical span) — odd quarter-turns on a non-square
     * surface clamp down to the nearest flip. Standalone tablets are
     * unrestricted, and a part's own stored rotation stays dormant.
     */
    public int effectiveRotation() {
        if (!isMerged()) return screenRotation;
        if (surfaceW == surfaceH) return screenRotation;
        return screenRotation & 2;
    }

    /**
     * Glass-wrench on a merged surface: advance the CONTROLLER's
     * rotation to the next step its shape allows (square: quarter
     * turns; oblong: half turns).
     */
    public void rotateScreenSurface() {
        int step = surfaceW == surfaceH ? 1 : 2;
        screenRotation = (effectiveRotation() + step) & 3;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Controller position derived from this member's own offset + state. */
    public BlockPos getControllerPos() {
        if (!isSurfacePart()) return worldPosition;
        BlockState state = getBlockState();
        return worldPosition
                .relative(TabletScreenMath.screenRight(state), -surfaceDx)
                .relative(TabletScreenMath.screenDown(state), -surfaceDy);
    }

    /**
     * The controller BE this part belongs to, or null when it can't be
     * resolved (unloaded chunk, stale roles). Validates that the target
     * actually claims a surface covering this member's offset.
     */
    @Nullable
    public TabletBlockEntity getController() {
        if (!isSurfacePart()) return this;
        if (level == null) return null;
        BlockPos pos = getControllerPos();
        if (!level.isLoaded(pos)) return null;
        if (!(level.getBlockEntity(pos) instanceof TabletBlockEntity controller)) return null;
        if (controller.isSurfacePart()) return null;
        if (surfaceDx >= controller.surfaceW || surfaceDy >= controller.surfaceH) return null;
        return controller;
    }

    /**
     * Where clicks/edits on this tablet actually land: itself when
     * standalone or controller, the controller when a part — null when
     * the part is orphaned (treat as inert).
     */
    @Nullable
    public TabletBlockEntity resolveController() {
        return isSurfacePart() ? getController() : this;
    }

    /** Scanner entry point: assigns (or clears) this member's role. */
    public void setSurfaceRole(int dx, int dy, int w, int h) {
        if (surfaceDx == dx && surfaceDy == dy && surfaceW == w && surfaceH == h) return;
        surfaceDx = (byte) dx;
        surfaceDy = (byte) dy;
        surfaceW = (byte) w;
        surfaceH = (byte) h;
        clearHeldPips();
        setChanged();
        // Role changes hit MANY BEs in one tick (a whole surface merges
        // or dissolves at once), and vanilla's batched multi-block
        // update path DROPS block-entity data — sendBlockUpdated with an
        // unchanged state only reaches clients on the single-block path.
        // Send the BE packet explicitly to every tracking player.
        if (level instanceof ServerLevel serverLevel) {
            ClientboundBlockEntityDataPacket packet = getUpdatePacket();
            serverLevel.getChunkSource().chunkMap
                    .getPlayers(new net.minecraft.world.level.ChunkPos(worldPosition), false)
                    .forEach(player -> player.connection.send(packet));
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        if (isSurfacePart()) {
            clearTransmitters();
        } else {
            refreshTransmitters();
            updateLit();
        }
    }

    // ------------------------------------------------------------------
    // Transmitters
    // ------------------------------------------------------------------

    private void refreshTransmitters() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        // Parts are dormant: the controller broadcasts for the surface
        if (isSurfacePart()) {
            clearTransmitters();
            return;
        }

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
        // Parts never own the lit computation; the controller lights
        // the whole surface at once.
        if (isSurfacePart()) return;
        boolean lit = apps.stream().anyMatch(a -> a.active() && !a.momentary());
        if (isSurfaceController()) {
            BlockState state = getBlockState();
            for (int dx = 0; dx < surfaceW; dx++) {
                for (int dy = 0; dy < surfaceH; dy++) {
                    BlockPos member = worldPosition
                            .relative(TabletScreenMath.screenRight(state), dx)
                            .relative(TabletScreenMath.screenDown(state), dy);
                    setLitAt(member, lit);
                }
            }
        } else {
            setLitAt(worldPosition, lit);
        }
    }

    /** Sets LIT on one member pos (LIT-only diffs skip the merge scanner). */
    private void setLitAt(BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(TabletBlock.LIT)) return;
        if (state.getValue(TabletBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(TabletBlock.LIT, lit), 3);
        }
    }

    /** Recomputes surface lighting after a role change (scanner hook). */
    public void updateSurfaceLit() {
        updateLit();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        refreshTransmitters();
        // Load-time self-heal against stale roles, whatever their
        // origin (missed removal hooks, lost scheduled ticks): a part
        // whose loaded controller no longer claims it, or a CONTROLLER
        // whose members no longer point back, reschedules a rescan.
        // Unloaded neighbors defer judgment — their own onLoad re-checks.
        if (!(level instanceof ServerLevel serverLevel)) return;
        boolean stale =
                (isSurfacePart()
                        && serverLevel.isLoaded(getControllerPos())
                        && getController() == null)
                || (isSurfaceController() && !surfaceIntact(serverLevel));
        if (stale) {
            serverLevel.scheduleTick(worldPosition, getBlockState().getBlock(), 1);
        }
    }

    /** Whether every loaded member of this controller's surface still claims it. */
    private boolean surfaceIntact(ServerLevel serverLevel) {
        BlockState state = getBlockState();
        var right = TabletScreenMath.screenRight(state);
        var down = TabletScreenMath.screenDown(state);
        for (int dx = 0; dx < surfaceW; dx++) {
            for (int dy = 0; dy < surfaceH; dy++) {
                if (dx == 0 && dy == 0) continue;
                BlockPos member = worldPosition.relative(right, dx).relative(down, dy);
                if (!serverLevel.isLoaded(member)) continue;
                if (!(serverLevel.getBlockEntity(member) instanceof TabletBlockEntity be)
                        || be.getSurfaceDx() != dx || be.getSurfaceDy() != dy) {
                    return false;
                }
            }
        }
        return true;
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
        if (soloScreen) {
            tag.putBoolean("solo_screen", true);
        }
        if (theme != ScreenTheme.DARK) {
            tag.putString("theme", theme.getSerializedName());
        }
        if (screenRotation != 0) {
            tag.putInt("screen_rotation", screenRotation);
        }
        if (surfaceDx != 0 || surfaceDy != 0) {
            tag.putByte("surface_dx", surfaceDx);
            tag.putByte("surface_dy", surfaceDy);
        }
        if (surfaceW != 1 || surfaceH != 1) {
            tag.putByte("surface_w", surfaceW);
            tag.putByte("surface_h", surfaceH);
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
        this.soloScreen = tag.getBoolean("solo_screen");
        this.theme = ScreenTheme.byName(tag.getString("theme"));
        this.screenRotation = tag.getInt("screen_rotation") & 3;
        this.surfaceDx = tag.getByte("surface_dx");
        this.surfaceDy = tag.getByte("surface_dy");
        this.surfaceW = tag.contains("surface_w") ? tag.getByte("surface_w") : 1;
        this.surfaceH = tag.contains("surface_h") ? tag.getByte("surface_h") : 1;
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
