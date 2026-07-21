package com.modpack.linktablet.client;

import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Tiny client-side preference store for display options (not synced to the
 * server or the item — purely how this player likes their tablet to look).
 * Persisted to {@code config/linktablet-client.properties}.
 */
public class ClientPrefs {

    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("linktablet-client.properties");

    private static boolean loaded = false;
    private static boolean listView = false;

    // Pinned overlay (1.7.0): "" = no pin, "slot:<n>" = inventory slot,
    // "block:<x>,<y>,<z>" = placed tablet. Position is the window's
    // last-dragged top-left corner (-1 = never moved, use default).
    private static String overlayPin = "";
    private static int overlayX = -1;
    private static int overlayY = -1;

    /** Whether the tablet home screen shows the app list instead of the icon grid. */
    public static boolean listView() {
        load();
        return listView;
    }

    public static void setListView(boolean value) {
        load();
        if (listView == value) return;
        listView = value;
        save();
    }

    /** The persisted overlay pin descriptor ("" when nothing is pinned). */
    public static String overlayPin() {
        load();
        return overlayPin;
    }

    public static void setOverlayPin(String value) {
        load();
        if (overlayPin.equals(value)) return;
        overlayPin = value;
        save();
    }

    /** Last-dragged overlay window corner; x == -1 means "use default". */
    public static int overlayX() {
        load();
        return overlayX;
    }

    public static int overlayY() {
        load();
        return overlayY;
    }

    public static void setOverlayPos(int x, int y) {
        load();
        if (overlayX == x && overlayY == y) return;
        overlayX = x;
        overlayY = y;
        save();
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        Properties props = new Properties();
        try (var in = java.nio.file.Files.newInputStream(FILE)) {
            props.load(in);
        } catch (IOException ignored) {
            return; // first run: keep defaults
        }
        listView = Boolean.parseBoolean(props.getProperty("listView", "false"));
        overlayPin = props.getProperty("overlayPin", "");
        overlayX = parseInt(props.getProperty("overlayX"), -1);
        overlayY = parseInt(props.getProperty("overlayY"), -1);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty("listView", Boolean.toString(listView));
        props.setProperty("overlayPin", overlayPin);
        props.setProperty("overlayX", Integer.toString(overlayX));
        props.setProperty("overlayY", Integer.toString(overlayY));
        try (var out = java.nio.file.Files.newOutputStream(FILE)) {
            props.store(out, "Link Tablet client display preferences");
        } catch (IOException ignored) {
        }
    }
}
