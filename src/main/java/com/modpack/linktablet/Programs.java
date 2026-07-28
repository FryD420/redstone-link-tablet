package com.modpack.linktablet;

import com.modpack.linktablet.api.TabletProgram;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The unified program table (addon API, 1.10.0): the {@link Program}
 * enum's built-ins plus every addon-registered
 * {@link TabletProgram}, in deterministic order — enum order first,
 * then addons sorted by key. Every launcher/store/roster/nav consumer
 * reads THIS table; the enum remains only for built-in identity
 * (dispatch switches) and legacy int-id decoding.
 *
 * <p>Registration happens through
 * {@link com.modpack.linktablet.api.RegisterTabletProgramsEvent} and
 * freezes at the end of common setup — worlds always load against the
 * complete table.
 */
public final class Programs {

    private static final Map<String, TabletProgram> TABLE = new LinkedHashMap<>();
    private static boolean frozen = false;

    static {
        for (Program program : Program.values()) {
            TABLE.put(program.key(), program);
        }
    }

    /** Home screen when the tablet has no roster data — Signals only. */
    public static final List<TabletProgram> DEFAULT_HOME = List.of(Program.SIGNALS);

    /** Addon registration (via the API event). Addon keys are namespaced
     * {@code modid:name}; the bare built-in keys and the linktablet
     * namespace are reserved. */
    public static synchronized void register(TabletProgram program) {
        if (frozen) {
            throw new IllegalStateException(
                    "Tablet programs are frozen — register during RegisterTabletProgramsEvent");
        }
        String key = program.key();
        ResourceLocation id = key == null ? null : ResourceLocation.tryParse(key);
        if (id == null || !key.contains(":")) {
            throw new IllegalArgumentException(
                    "Tablet program key must be a namespaced id (\"modid:name\"): " + key);
        }
        if (LinkTabletMod.MOD_ID.equals(id.getNamespace())) {
            throw new IllegalArgumentException(
                    "The linktablet namespace is reserved for built-in programs: " + key);
        }
        if (TABLE.containsKey(key)) {
            throw new IllegalArgumentException("Duplicate tablet program key: " + key);
        }
        TABLE.put(key, program);
    }

    /** Called once after the registration event; sorts addons by key so
     * the store shelf order never depends on mod-load order. */
    public static synchronized void freeze() {
        if (frozen) return;
        frozen = true;
        List<Map.Entry<String, TabletProgram>> addons = new ArrayList<>();
        for (var it = TABLE.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (!(entry.getValue() instanceof Program)) {
                addons.add(entry);
                it.remove();
            }
        }
        addons.sort(Map.Entry.comparingByKey());
        for (var entry : addons) {
            TABLE.put(entry.getKey(), entry.getValue());
        }
    }

    /** Whether the key names a registered ADDON program (client events
     * validate against this). */
    public static boolean isAddon(String key) {
        TabletProgram program = TABLE.get(key);
        return program != null && !(program instanceof Program);
    }

    /** Unknown keys fall back to home — an uninstalled addon (or a
     * downgrade) shows the launcher rather than crashing or ghosting a
     * program that no longer exists. */
    public static TabletProgram byKey(String key) {
        TabletProgram program = TABLE.get(key);
        return program != null ? program : Program.LAUNCHER;
    }

    /** Everything the App Store shelves — every program except the
     * launcher itself (the desk the tiles sit on) and the store (it
     * doesn't sell itself). */
    public static List<TabletProgram> catalog() {
        List<TabletProgram> catalog = new ArrayList<>();
        for (TabletProgram program : TABLE.values()) {
            if (program != Program.LAUNCHER && program != Program.STORE) {
                catalog.add(program);
            }
        }
        return catalog;
    }

    /** Stored key list → programs; unknown/launcher/store keys drop
     * silently (the downgrade story), an empty result falls back to the
     * default roster. */
    public static List<TabletProgram> fromKeys(List<String> keys) {
        if (keys == null) return DEFAULT_HOME;
        List<TabletProgram> home = new ArrayList<>();
        for (String key : keys) {
            TabletProgram program = TABLE.get(key);
            if (program != null && program != Program.LAUNCHER && program != Program.STORE
                    && !home.contains(program)) {
                home.add(program);
            }
        }
        return home.isEmpty() ? DEFAULT_HOME : List.copyOf(home);
    }

    public static List<String> keys(List<TabletProgram> programs) {
        List<String> keys = new ArrayList<>(programs.size());
        for (TabletProgram program : programs) keys.add(program.key());
        return keys;
    }

    private Programs() {
    }
}
