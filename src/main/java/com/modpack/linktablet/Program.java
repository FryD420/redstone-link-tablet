package com.modpack.linktablet;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * The BUILT-IN launchable programs (1.10.0 "OS" framework). Lives
 * outside the client package because kiosk tap routing on the server
 * derives the shown program from the same table. Addon programs join
 * these in {@link Programs} (the addon API) — every launcher/store/
 * roster consumer reads that unified table, not this enum.
 * <p>
 * KEYS are the persisted identity (roster component/NBT, kiosk nav,
 * client resume pref, overlay pin descriptors): append-only, frozen
 * forever. The int ids were the pre-API dev format and survive only in
 * {@link #byId}/{@link #fromIds} legacy decoding — never write them
 * again. LAUNCHER is the default and is never persisted — an absent tag
 * therefore boots to the home screen, the same default-never-written
 * idiom as {@code screen_rotation} and the theme.
 * <p>
 * Tile dress (chip color + icon item) lives here so the GUI launcher,
 * the kiosk face renderer, and the App Store all read one table.
 */
public enum Program implements com.modpack.linktablet.api.TabletProgram {
    LAUNCHER(0, "launcher", 0xFF3A4150, ""),
    SIGNALS(1, "signals", 0xFFC93C36, "minecraft:redstone"),
    CLOCK(2, "clock", 0xFFE0AA46, "minecraft:clock"),
    CALCULATOR(3, "calculator", 0xFF3E52C1, "minecraft:comparator"),
    GAUGES(4, "gauges", 0xFF2F855A, "create:redstone_link"),
    /** The App Store (user decision, first test pass): the door for
     * getting/removing apps — permanently the launcher's last tile,
     * never itself on the roster. */
    STORE(5, "store", 0xFF8932B8, "minecraft:emerald"),
    // Id 6 was the short-lived Arcade hub app (never released) —
    // RETIRED, do not reuse: dev-world rosters may still carry it.
    // The 19 formerly-secret games are each their OWN app instead
    // (user decision, third test pass). Keys double as the SecretGames
    // dispatch ids and the best-score pref keys — frozen. The secret
    // pip trigger still works as the easter-egg door.
    SNAKE(7, "snake", 0xFF80C71F, "minecraft:string", true),
    BREAKOUT(8, "breakout", 0xFFB02E26, "minecraft:brick", true),
    PONG(9, "pong", 0xFF169C9C, "minecraft:slime_ball", true),
    INVADERS(10, "invaders", 0xFF3C44AA, "minecraft:firework_rocket", true),
    FLAPPY(11, "flappy", 0xFFFED83D, "minecraft:feather", true),
    DODGE(12, "dodge", 0xFF9D9D97, "minecraft:arrow", true),
    RUNNER(13, "runner", 0xFF835432, "minecraft:leather_boots", true),
    CROSSING(14, "crossing", 0xFF5E7C16, "minecraft:lily_pad", true),
    STACKER(15, "stacker", 0xFFF9801D, "minecraft:scaffolding", true),
    WHACK(16, "whack", 0xFF474F52, "minecraft:mace", true),
    REFLEX(17, "reflex", 0xFF3AB3DA, "minecraft:sculk_sensor", true),
    SIMON(18, "simon", 0xFF8932B8, "minecraft:note_block", true),
    SWEEPER(19, "sweeper", 0xFFB02E26, "minecraft:tnt", true),
    LIGHTS(20, "lights", 0xFFFED83D, "minecraft:redstone_lamp", true),
    MEMORY(21, "memory", 0xFFC74EBD, "minecraft:map", true),
    G2048(22, "2048", 0xFFFED83D, "minecraft:gold_block", true),
    CRATES(23, "crates", 0xFF835432, "minecraft:barrel", true),
    LIFE(24, "life", 0xFF5E7C16, "minecraft:moss_block", true),
    PAINT(25, "paint", 0xFFF38BAA, "minecraft:painting", true),
    /** Frequency Monitor (1.11.0): read-only view of Create's link
     * network — who transmits on each channel the tablet uses. */
    MONITOR(26, "monitor", 0xFF16A0A0, "minecraft:spyglass"),
    /** Twitch Chat (1.11.0): read-only live chat of a selected channel
     * — anonymous, client-side only, no accounts. */
    TWITCH(27, "twitch", 0xFF9146FF, "minecraft:amethyst_shard");

    private final byte id;
    private final String key;
    /** Launcher tile chip color; SIGNALS wears Create's link red. */
    private final int chipColor;
    /** Item drawn on the tile chip, "" = none. */
    private final String iconItem;
    /** Arcade resident: the key doubles as the SecretGames dispatch id. */
    private final boolean game;

    Program(int id, String key, int chipColor, String iconItem) {
        this(id, key, chipColor, iconItem, false);
    }

    Program(int id, String key, int chipColor, String iconItem, boolean game) {
        this.id = (byte) id;
        this.key = key;
        this.chipColor = chipColor;
        this.iconItem = iconItem;
        this.game = game;
    }

    /** SecretGames dispatch id when this program is a game, else null. */
    @org.jetbrains.annotations.Nullable
    public String gameId() {
        return game ? key : null;
    }

    public byte id() {
        return id;
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public Component displayName() {
        return Component.translatable("program.linktablet." + key);
    }

    @Override
    public Component storeDescription() {
        return Component.translatable("program.linktablet." + key + ".desc");
    }

    @Override
    public int chipColor() {
        return chipColor;
    }

    /** Icon item id, or null for a plain chip. */
    @Override
    public ResourceLocation iconItem() {
        return iconItem.isEmpty() ? null : ResourceLocation.parse(iconItem);
    }

    // ------------------------------------------------------------------
    // Legacy int-id decoding (pre-API 1.10.0 dev format) — READ ONLY.
    // Nothing writes ids anymore; keys are the persisted identity.
    // ------------------------------------------------------------------

    /** Unknown ids fall back to home — a downgrade shows the launcher
     * rather than crashing or ghosting a program that no longer exists. */
    public static Program byId(int id) {
        for (Program program : values()) {
            if (program.id == id) return program;
        }
        return LAUNCHER;
    }

    /** Legacy stored id list → programs; unknown/launcher/store ids
     * drop silently, an empty result falls back to the default roster
     * (mirrors {@link Programs#fromKeys}). */
    public static List<com.modpack.linktablet.api.TabletProgram> fromIds(List<Integer> ids) {
        if (ids == null) return Programs.DEFAULT_HOME;
        List<com.modpack.linktablet.api.TabletProgram> home = new ArrayList<>();
        for (int id : ids) {
            Program program = byId(id);
            if (program != LAUNCHER && program != STORE && !home.contains(program)) {
                home.add(program);
            }
        }
        return home.isEmpty() ? Programs.DEFAULT_HOME : List.copyOf(home);
    }
}
