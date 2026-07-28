package com.modpack.linktablet.client;

import com.modpack.linktablet.Programs;
import com.modpack.linktablet.api.client.TabletProgramClient;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Client twin of {@link Programs}: which
 * {@link TabletProgramClient} serves each ADDON program. Built-ins
 * never appear here — their screens/overlays/faces dispatch through
 * the {@link com.modpack.linktablet.Program} enum switches, which are
 * always checked first.
 */
public final class ProgramClients {

    private static final Map<String, TabletProgramClient> TABLE = new HashMap<>();

    /** Addon registration (via the API client event). */
    public static synchronized void register(String key, TabletProgramClient client) {
        if (!Programs.isAddon(key)) {
            throw new IllegalArgumentException(
                    "No addon tablet program registered under key: " + key);
        }
        if (TABLE.putIfAbsent(key, client) != null) {
            throw new IllegalArgumentException("Duplicate tablet program client: " + key);
        }
    }

    @Nullable
    public static TabletProgramClient get(String key) {
        return TABLE.get(key);
    }

    private ProgramClients() {
    }
}
