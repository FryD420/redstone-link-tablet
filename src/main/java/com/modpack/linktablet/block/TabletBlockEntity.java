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
import net.minecraft.util.Mth;
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
     * Kiosk nav (1.10.0): the program this screen is showing, persisted
     * by KEY (string keys since the addon API — addon programs count;
     * the pre-API byte id decodes forever). LAUNCHER is the default and
     * is never persisted — an absent tag boots to Home, including every
     * pre-1.10 kiosk (user decision). Controller-only on merged
     * surfaces, synced via the update tag like everything else; never
     * travels on the item.
     */
    private com.modpack.linktablet.api.TabletProgram screenProgram =
            com.modpack.linktablet.Program.LAUNCHER;
    /**
     * Settable roster (1.10.0): the programs on this tablet's home
     * screen, in tile order. {@code Programs.DEFAULT_HOME} (Signals
     * only) is the default and is never persisted; travels item↔block
     * like the theme.
     */
    private List<com.modpack.linktablet.api.TabletProgram> homeApps =
            com.modpack.linktablet.Programs.DEFAULT_HOME;
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

    /** Frequency Monitor probe channel (1.11.0); {@link Frequency#EMPTY}
     * when unset. */
    private Frequency monitorProbe = Frequency.EMPTY;

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
    public com.modpack.linktablet.api.TabletProgram currentProgram() {
        return screenProgram;
    }

    /** Kiosk nav: taps mutate this on BOTH sides (vanilla replays the
     * use-packet), so routing always derives from agreed state. */
    public void setCurrentProgram(com.modpack.linktablet.api.TabletProgram program) {
        if (screenProgram == program) return;
        screenProgram = program;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public List<com.modpack.linktablet.api.TabletProgram> getHomeApps() {
        return homeApps;
    }

    public void setHomeApps(List<com.modpack.linktablet.api.TabletProgram> homeApps) {
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
     * Always applies and syncs — the wrench gesture's shape; the follow
     * tick goes through {@link #computeAim} + a threshold instead.
     */
    public void aimAt(net.minecraft.world.phys.Vec3 eye) {
        float[] angles = computeAim(eye);
        if (angles == null) return;
        this.mountPitch = angles[0];
        this.mountYaw = angles[1];
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Beyond this eye→pivot distance the aim is refused (guard, 1.10.1):
     * reach is ~5 blocks and follow range 8 — anything past this is a
     * coordinate-frame mismatch (Sable sub-level without working compat)
     * and clamping it would overwrite the stored aim with garbage. */
    private static final double AIM_GUARD_DIST_SQ = 32 * 32;

    /** The one aim derivation (wrench AND follow): eye point → clamped
     * {pitch, yaw}, or null when the eye sits on the pivot — or (1.10.1)
     * when it's implausibly far after Sable localization (see guard). */
    @Nullable
    private float[] computeAim(net.minecraft.world.phys.Vec3 eye) {
        net.minecraft.core.Direction attach = mountAttachNormal();
        net.minecraft.world.phys.Vec3 pivot =
                TabletScreenMath.MountBasis.pivot(worldPosition, attach);
        // Sable sub-level (1.10.1): the block may live in the plot frame
        // while the player is in world space — bring the eye over first
        if (level != null) {
            eye = com.modpack.linktablet.compat.SableCompat.localizeNear(
                    level, worldPosition, eye);
        }
        net.minecraft.world.phys.Vec3 toEye = eye.subtract(pivot);
        if (toEye.lengthSqr() < 1.0E-6
                || toEye.lengthSqr() > AIM_GUARD_DIST_SQ) return null;
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
        return new float[]{
                (float) Math.toDegrees(-Math.atan2(normal.y, horiz)),
                (float) Math.toDegrees(Math.atan2(-normal.x, normal.z))};
    }

    // ---- Redstone follow mode (1.10.0) -------------------------------

    /** Follow cadence/feel — server-side; tune with the user's feel-test. */
    private static final int FOLLOW_INTERVAL_TICKS = 3;
    private static final double FOLLOW_RANGE = 8.0;
    /** Below this angular move the tick applies nothing: aimAt syncs the
     * FULL BE NBT, so the threshold is the packet-spam guard. */
    private static final float FOLLOW_THRESHOLD_DEG = 2.0f;

    /**
     * Redstone power near the mount — TRANSIENT (never saved): pushed by
     * {@link TabletBlock#neighborChanged}, re-derived in {@link #onLoad}.
     */
    private boolean followPowered;

    public void setFollowPowered(boolean powered) {
        this.followPowered = powered;
    }

    /**
     * Server tick for MOUNTED tablets only (the mod's first BE ticker —
     * TabletBlock.getTicker gates it on the blockstate, so unmounted
     * tablets don't tick at all). A POWERED mount tracks the nearest
     * player like the enchanting table's book; unpowered stays put.
     */
    public void followTick() {
        if (!followPowered || level == null || level.isClientSide) return;
        if ((level.getGameTime() % FOLLOW_INTERVAL_TICKS) != 0) return;
        net.minecraft.world.phys.Vec3 pivot =
                TabletScreenMath.MountBasis.pivot(worldPosition, mountAttachNormal());
        // Sable sub-level (1.10.1): players live in world space — search
        // where the block APPEARS, not its plot position (computeAim
        // brings the found eye back into the plot frame)
        net.minecraft.world.phys.Vec3 searchAt =
                com.modpack.linktablet.compat.SableCompat.toWorldPoint(
                        level, worldPosition, pivot);
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(
                searchAt.x, searchAt.y, searchAt.z, FOLLOW_RANGE, false);
        if (nearest == null || nearest.isSpectator()) return;
        float[] angles = computeAim(nearest.getEyePosition());
        if (angles == null) return;
        float dPitch = Math.abs(Mth.degreesDifference(mountPitch, angles[0]));
        float dYaw = Math.abs(Mth.degreesDifference(mountYaw, angles[1]));
        if (dPitch < FOLLOW_THRESHOLD_DEG && dYaw < FOLLOW_THRESHOLD_DEG) return;
        this.mountPitch = angles[0];
        this.mountYaw = angles[1];
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    // ---- Client-only render glide (follow mode, 1.10.0) --------------
    // The RENDERER's smoothed angles — hit tests keep the synced
    // mountPitch/mountYaw (mountBasis()), so clicks stay server-agreed;
    // pixels glide toward them. NaN = snap on first frame.

    /** @see com.modpack.linktablet.client.render.TabletBlockEntityRenderer */
    public float renderPitch = Float.NaN;
    public float renderYaw = Float.NaN;
    public long renderLerpMillis;

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
            // Create's add-time evaluation never reaches a non-
            // LinkBehaviour member (1.10.2 fix) — read the channel once
            // ourselves so a gauge placed on a static signal shows it
            // immediately instead of freezing at 0 until the next change
            receiver.readInitial();
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

    // ---- Frequency Monitor probe (1.11.0) -----------------------------

    public Frequency getMonitorProbe() {
        return monitorProbe;
    }

    public void setMonitorProbe(Frequency newProbe) {
        if (monitorProbe.equals(newProbe)) return;
        this.monitorProbe = newProbe;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Copies signals, case color, screen layout, theme, and rotation from the placed item. */
    public void loadFromItem(ItemStack stack) {
        this.caseColor = stack.get(ModDataComponents.CASE_COLOR.get());
        this.customName = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        this.screenList = stack.getOrDefault(ModDataComponents.SCREEN_LIST.get(), false);
        this.theme = stack.getOrDefault(ModDataComponents.THEME.get(), ScreenTheme.DARK);
        this.screenRotation = stack.getOrDefault(ModDataComponents.SCREEN_ROTATION.get(), 0) & 3;
        this.homeApps = com.modpack.linktablet.Programs.fromKeys(
                stack.get(ModDataComponents.HOME_APPS.get()));
        setGauges(stack.getOrDefault(ModDataComponents.TABLET_GAUGES.get(), List.of()));
        setMonitorProbe(stack.getOrDefault(ModDataComponents.MONITOR_PROBE.get(), Frequency.EMPTY));
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
        if (!homeApps.equals(com.modpack.linktablet.Programs.DEFAULT_HOME)) {
            stack.set(ModDataComponents.HOME_APPS.get(),
                    com.modpack.linktablet.Programs.keys(homeApps));
        }
        if (!gauges.isEmpty()) {
            stack.set(ModDataComponents.TABLET_GAUGES.get(), gauges);
        }
        if (!monitorProbe.isEmpty()) {
            stack.set(ModDataComponents.MONITOR_PROBE.get(), monitorProbe);
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
        // Follow mode (1.10.0): the powered flag is transient — re-read
        // the neighborhood on load (neighborChanged keeps it live after)
        this.followPowered = serverLevel.hasNeighborSignal(worldPosition);
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
        if (!monitorProbe.isEmpty()) {
            Frequency.CODEC.encodeStart(NbtOps.INSTANCE, monitorProbe)
                    .result().ifPresent(t -> tag.put("monitor_probe", t));
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
        // Keys since the addon API (addon programs must persist);
        // loadAdditional type-sniffs the pre-API byte/int-array forms
        if (screenProgram != com.modpack.linktablet.Program.LAUNCHER) {
            tag.putString("screen_program", screenProgram.key());
        }
        if (!homeApps.equals(com.modpack.linktablet.Programs.DEFAULT_HOME)) {
            net.minecraft.nbt.ListTag keys = new net.minecraft.nbt.ListTag();
            for (com.modpack.linktablet.api.TabletProgram program : homeApps) {
                keys.add(net.minecraft.nbt.StringTag.valueOf(program.key()));
            }
            tag.put("home_apps", keys);
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
        Tag monitorProbeTag = tag.get("monitor_probe");
        this.monitorProbe = monitorProbeTag == null ? Frequency.EMPTY
                : Frequency.CODEC.parse(NbtOps.INSTANCE, monitorProbeTag).result().orElse(Frequency.EMPTY);
        this.caseColor = tag.contains("case_color") ? DyeColor.byName(tag.getString("case_color"), null) : null;
        this.screenList = tag.getBoolean("screen_list");
        this.soloScreen = tag.getBoolean("solo_screen");
        this.theme = ScreenTheme.byName(tag.getString("theme"));
        this.screenRotation = tag.getInt("screen_rotation") & 3;
        // Type sniff: string forms are current, byte/int-array forms are
        // the pre-API 1.10.0 dev format (decoded forever, never rewritten
        // until the next save)
        this.screenProgram = tag.contains("screen_program", Tag.TAG_STRING)
                ? com.modpack.linktablet.Programs.byKey(tag.getString("screen_program"))
                : com.modpack.linktablet.Program.byId(tag.getByte("screen_program"));
        if (tag.contains("home_apps", Tag.TAG_LIST)) {
            net.minecraft.nbt.ListTag keys = tag.getList("home_apps", Tag.TAG_STRING);
            List<String> list = new java.util.ArrayList<>(keys.size());
            for (int i = 0; i < keys.size(); i++) list.add(keys.getString(i));
            this.homeApps = com.modpack.linktablet.Programs.fromKeys(list);
        } else if (tag.contains("home_apps", Tag.TAG_INT_ARRAY)) {
            this.homeApps = com.modpack.linktablet.Program.fromIds(
                    java.util.Arrays.stream(tag.getIntArray("home_apps")).boxed().toList());
        } else {
            this.homeApps = com.modpack.linktablet.Programs.DEFAULT_HOME;
        }
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
