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
    }

    private static void save() {
        Properties props = new Properties();
        props.setProperty("listView", Boolean.toString(listView));
        try (var out = java.nio.file.Files.newOutputStream(FILE)) {
            props.store(out, "Link Tablet client display preferences");
        } catch (IOException ignored) {
        }
    }
}
