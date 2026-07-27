package com.modpack.linktablet.block;

import com.modpack.linktablet.compat.VirtualTransmitter;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.Signal;
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
 * A mounted tablet. Stores the same signal list and case color as the item,
 * and keeps {@link VirtualTransmitter}s registered on Create's link
 * network for every toggled-ON signal — broadcasting from the block's own
 * position, for as long as the chunk is loaded. The LIT blockstate (and
 * with it the glowing screen model) tracks whether any signal is on.
 */
public class TabletBlockEntity extends BlockEntity {

    private List<Signal> signals = List.of();
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
    /**
     * Kiosk nav (1.10.0): id of the {@link com.modpack.linktablet.Program}
     * this screen is showing. 0 (launcher) is the default and is never
     * persisted — an absent tag boots to Home, including every pre-1.10
     * kiosk (user decision). Controller-only on merged surfaces, synced
     * via the update tag like everything else; never travels on the item.
     */
    private byte screenProgram;
    /**
     * Settable roster (1.10.0): the programs on this tablet's home
     * screen, in tile order. {@code Program.DEFAULT_HOME} (Signals
     * only) is the default and is never persisted; travels item↔block
     * like the theme.
     */
    private List<com.modpack.linktablet.Program> homeApps =
            com.modpack.linktablet.Program.DEFAULT_HOME;
    /** Screen content rotation, quarter turns CW; 0 is never persisted. */
    private int screenRotation;
    /** Custom (anvil) item name (1.8.0); survives the place/pickup trip. */
    @Nullable
    private net.minecraft.network.chat.Component customName;
    /**
     * Swivel mount angles (1.8.0), vanilla pitch/yaw semantics — only
     * meaningful while the MOUNTED blockstate is set. Block-only, like
     * the surface roles: never travel on the item.
     */
    private float mountPitch;
    private float mountYaw;

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
     * Gauges (1.10.0 OS suite): read-only dials with their own data
     * model. Item↔block round-trip like signals; parts stay dormant —
     * the controller listens for the whole surface.
     */
    private List<com.modpack.linktablet.frequency.Gauge> gauges = List.of();

    /** Server-side receivers keyed by listened frequency (1.10.0). */
    private final Map<Frequency, com.modpack.linktablet.compat.VirtualReceiver> receivers = new HashMap<>();

    /**
     * Latest received strength per listened frequency. Transient like
     * the held pips: synced via the update tag (as a per-gauge array),
     * never written to disk — a fresh load re-registers receivers and
     * Create pushes current values straight back in.
     */
    private final Map<Frequency, Integer> gaugeValues = new HashMap<>();

    /**
     * Momentary pips currently held down, purely for the screen visual.
     * Transient: synced to clients via the update tag but never written
     * to disk, so a crash can't leave a pip stuck lit.
     */
    private final Set<Integer> heldPips = new HashSet<>();

    public TabletBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TABLET.get(), pos, state);
    }

    public List<Signal> getSignals() {
        return signals;
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

    /** Drops every held-pip visual (signal reorder invalidates indices). */
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

    /** The program this kiosk screen is showing (1.10.0). */
    public com.modpack.linktablet.Program currentProgram() {
        return com.modpack.linktablet.Program.byId(screenProgram);
    }

    /** Kiosk nav: taps mutate this on BOTH sides (vanilla replays the
     * use-packet), so routing always derives from agreed state. */
    public void setCurrentProgram(com.modpack.linktablet.Program program) {
        if (screenProgram == program.id()) return;
        screenProgram = program.id();
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public List<com.modpack.linktablet.Program> getHomeApps() {
        return homeApps;
    }

    public void setHomeApps(List<com.modpack.linktablet.Program> homeApps) {
        if (this.homeApps.equals(homeApps)) return;
        this.homeApps = List.copyOf(homeApps);
        setChanged();
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

    // ---- Swivel mount (1.8.0) ----------------------------------------

    public boolean isMounted() {
        BlockState state = getBlockState();
        return state.hasProperty(TabletBlock.MOUNTED) && state.getValue(TabletBlock.MOUNTED);
    }

    public float getMountPitch() {
        return mountPitch;
    }

    public float getMountYaw() {
        return mountYaw;
    }

    /** The mounted screen frame — renderer and both hit paths share it. */
    public TabletScreenMath.MountBasis mountBasis() {
        return TabletScreenMath.mountBasis(worldPosition,
                mountAttachNormal(), mountPitch, mountYaw,
                getBlockState().getValue(TabletBlock.LANDSCAPE));
    }

    /** Normal of the face the stand is attached to. */
    public net.minecraft.core.Direction mountAttachNormal() {
        return TabletScreenMath.screenFace(getBlockState());
    }

    /**
     * Face-me aiming: points the screen from the ball pivot at the given
     * eye position, clamped so the panel never tilts more than
     * {@link TabletScreenMath#MOUNT_MAX_TILT} away from the attach face.
     */
    public void aimAt(net.minecraft.world.phys.Vec3 eye) {
        net.minecraft.core.Direction attach = mountAttachNormal();
        net.minecraft.world.phys.Vec3 pivot =
                TabletScreenMath.MountBasis.pivot(worldPosition, attach);
        net.minecraft.world.phys.Vec3 toEye = eye.subtract(pivot);
        if (toEye.lengthSqr() < 1.0E-6) return;
        net.minecraft.world.phys.Vec3 normal = toEye.normalize();

        // Clamp the tilt toward the attach face's normal
        net.minecraft.world.phys.Vec3 attachN =
                net.minecraft.world.phys.Vec3.atLowerCornerOf(attach.getNormal());
        double cosMax = Math.cos(Math.toRadians(TabletScreenMath.MOUNT_MAX_TILT));
        double dot = normal.dot(attachN);
        if (dot < cosMax) {
            net.minecraft.world.phys.Vec3 tangent = normal.subtract(attachN.scale(dot));
            if (tangent.lengthSqr() < 1.0E-8) {
                normal = attachN; // aiming straight through the wall — give up flat
            } else {
                double sinMax = Math.sin(Math.toRadians(TabletScreenMath.MOUNT_MAX_TILT));
                normal = attachN.scale(cosMax).add(tangent.normalize().scale(sinMax)).normalize();
            }
        }

        double horiz = Math.sqrt(normal.x * normal.x + normal.z * normal.z);
        this.mountPitch = (float) Math.toDegrees(-Math.atan2(normal.y, horiz));
        this.mountYaw = (float) Math.toDegrees(Math.atan2(-normal.x, normal.z));
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

    public void setSignals(List<Signal> newSignals) {
        this.signals = List.copyOf(newSignals);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        updateLit();
        refreshTransmitters();
    }

    @Nullable
    public net.minecraft.network.chat.Component getCustomName() {
        return customName;
    }

    // ---- Gauges (1.10.0) ---------------------------------------------

    public List<com.modpack.linktablet.frequency.Gauge> getGauges() {
        return gauges;
    }

    public void setGauges(List<com.modpack.linktablet.frequency.Gauge> newGauges) {
        this.gauges = List.copyOf(newGauges);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        refreshReceivers();
    }

    /**
     * Reading for one gauge, 0–15. Server: live receiver state. Client:
     * the last update-tag sync. Non-LINK sources (future) read 0 here.
     */
    public int gaugeReading(int index) {
        if (index < 0 || index >= gauges.size()) return 0;
        Frequency freq = gauges.get(index).frequency();
        return gaugeValues.getOrDefault(freq, 0);
    }

    /** Keeps one receiver per listened frequency (controller only). */
    private void refreshReceivers() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (isSurfacePart()) {
            clearReceivers();
            return;
        }
        Set<Frequency> wanted = new HashSet<>();
        for (com.modpack.linktablet.frequency.Gauge gauge : gauges) {
            if (gauge.source() == com.modpack.linktablet.frequency.Gauge.Source.LINK
                    && !gauge.frequency().isEmpty()) {
                wanted.add(gauge.frequency());
            }
        }
        Iterator<Map.Entry<Frequency, com.modpack.linktablet.compat.VirtualReceiver>> it =
                receivers.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (!wanted.contains(entry.getKey())) {
                entry.getValue().removeFromNetwork();
                gaugeValues.remove(entry.getKey());
                it.remove();
            }
        }
        for (Frequency freq : wanted) {
            if (receivers.containsKey(freq)) continue;
            var receiver = new com.modpack.linktablet.compat.VirtualReceiver(
                    freq, serverLevel, worldPosition,
                    power -> onGaugeReading(freq, power));
            Create.REDSTONE_LINK_NETWORK_HANDLER.addToNetwork(serverLevel, receiver);
            receivers.put(freq, receiver);
            // addToNetwork re-evaluates the channel, so the initial
            // value arrives through the change hook right away
            gaugeValues.put(freq, receiver.getReceivedStrength());
        }
    }

    /** Change hook from a receiver: store + sync (no disk write —
     * readings are transient, like the held pips). */
    private void onGaugeReading(Frequency freq, int power) {
        Integer old = gaugeValues.put(freq, power);
        if (old != null && old == power) return;
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void clearReceivers() {
        receivers.values().forEach(com.modpack.linktablet.compat.VirtualReceiver::removeFromNetwork);
        receivers.clear();
        gaugeValues.clear();
    }

    /** Copies signals, case color, screen layout, theme, and rotation from the placed item. */
    public void loadFromItem(ItemStack stack) {
        this.caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        this.customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        this.screenList = stack.getOrDefault(ModDataComponents.SCREEN_LIST.get(), false);
        this.theme = stack.getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        this.screenRotation = stack.getOrDefault(ModDataComponents.SCREEN_ROTATION.get(), 0) & 3;
        this.homeApps = com.modpack.linktablet.Program.fromIds(
                stack.get(ModDataComponents.HOME_APPS.get()));
        setGauges(stack.getOrDefault(ModDataComponents.TABLET_GAUGES.get(), List.of()));
        setSignals(stack.getOrDefault(ModDataComponents.TABLET_SIGNALS.get(), List.of()));
    }

    /** Builds the tablet item this block turns back into. */
    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(ModItems.TABLET.get());
        if (!signals.isEmpty()) {
            stack.set(ModDataComponents.TABLET_SIGNALS.get(), signals);
        }
        if (caseColor != null) {
            stack.set(ModDataComponents.CASE_COLOR.get(), caseColor);
        }
        if (customName != null) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, customName);
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
        if (!homeApps.equals(com.modpack.linktablet.Program.DEFAULT_HOME)) {
            stack.set(ModDataComponents.HOME_APPS.get(),
                    com.modpack.linktablet.Program.ids(homeApps));
        }
        if (!gauges.isEmpty()) {
            stack.set(ModDataComponents.TABLET_GAUGES.get(), gauges);
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

    /** Signal cap: every merged member adds a full tablet's worth. */
    public int maxSignals() {
        return com.modpack.linktablet.network.ModNetworking.MAX_SIGNALS * memberCount();
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
            clearReceivers();
        } else {
            refreshTransmitters();
            refreshReceivers();
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
        for (Signal signal : signals) {
            if (!signal.active() || signal.momentary()) continue;
            for (Frequency freq : signal.frequencies()) {
                if (!freq.isEmpty()) {
                    wanted.merge(freq, signal.strength(), Math::max);
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
        boolean lit = signals.stream().anyMatch(a -> a.active() && !a.momentary());
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
        refreshReceivers();
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
        clearReceivers();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        clearTransmitters();
        clearReceivers();
        super.onChunkUnloaded();
    }

    // ------------------------------------------------------------------
    // Save / sync
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!signals.isEmpty()) {
            // NBT key predates the 1.10.0 apps→signals rename and is
            // FROZEN — renaming it wipes every placed tablet's config
            Signal.CODEC.listOf().encodeStart(NbtOps.INSTANCE, signals)
                    .result().ifPresent(t -> tag.put("apps", t));
        }
        if (!gauges.isEmpty()) {
            com.modpack.linktablet.frequency.Gauge.CODEC.listOf()
                    .encodeStart(NbtOps.INSTANCE, gauges)
                    .result().ifPresent(t -> tag.put("gauges", t));
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
        if (screenProgram != 0) {
            tag.putByte("screen_program", screenProgram);
        }
        if (!homeApps.equals(com.modpack.linktablet.Program.DEFAULT_HOME)) {
            tag.putIntArray("home_apps",
                    com.modpack.linktablet.Program.ids(homeApps).stream()
                            .mapToInt(Integer::intValue).toArray());
        }
        if (mountPitch != 0 || mountYaw != 0) {
            tag.putFloat("mount_pitch", mountPitch);
            tag.putFloat("mount_yaw", mountYaw);
        }
        if (customName != null) {
            tag.putString("custom_name",
                    net.minecraft.network.chat.Component.Serializer.toJson(customName, registries));
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
        Tag signalsTag = tag.get("apps"); // frozen pre-rename key
        this.signals = signalsTag == null ? List.of()
                : Signal.CODEC.listOf().parse(NbtOps.INSTANCE, signalsTag).result().orElse(List.of());
        Tag gaugesTag = tag.get("gauges");
        this.gauges = gaugesTag == null ? List.of()
                : com.modpack.linktablet.frequency.Gauge.CODEC.listOf()
                        .parse(NbtOps.INSTANCE, gaugesTag).result().orElse(List.of());
        this.caseColor = tag.contains("case_color") ? DyeColor.byName(tag.getString("case_color"), null) : null;
        this.screenList = tag.getBoolean("screen_list");
        this.soloScreen = tag.getBoolean("solo_screen");
        this.theme = ScreenTheme.byName(tag.getString("theme"));
        this.screenRotation = tag.getInt("screen_rotation") & 3;
        this.screenProgram = tag.getByte("screen_program");
        this.homeApps = tag.contains("home_apps")
                ? com.modpack.linktablet.Program.fromIds(
                        java.util.Arrays.stream(tag.getIntArray("home_apps")).boxed().toList())
                : com.modpack.linktablet.Program.DEFAULT_HOME;
        this.mountPitch = tag.getFloat("mount_pitch");
        this.mountYaw = tag.getFloat("mount_yaw");
        this.customName = tag.contains("custom_name")
                ? net.minecraft.network.chat.Component.Serializer.fromJson(
                        tag.getString("custom_name"), registries)
                : null;
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
        // Gauge readings ride sync tags only (transient like held pips):
        // a per-gauge int array, mapped back onto the frequencies
        gaugeValues.clear();
        int[] readings = tag.getIntArray("gauge_readings");
        for (int i = 0; i < readings.length && i < gauges.size(); i++) {
            gaugeValues.put(gauges.get(i).frequency(), readings[i]);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        if (!heldPips.isEmpty()) {
            tag.putIntArray("held_pips", heldPips.stream().mapToInt(Integer::intValue).toArray());
        }
        if (!gauges.isEmpty()) {
            int[] readings = new int[gauges.size()];
            for (int i = 0; i < gauges.size(); i++) {
                readings[i] = gaugeReading(i);
            }
            tag.putIntArray("gauge_readings", readings);
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
