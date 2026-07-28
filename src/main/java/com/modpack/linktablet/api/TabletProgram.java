package com.modpack.linktablet.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * A launchable tablet program — the common (both-sides) half of an
 * addon app. Implement this and hand an instance to
 * {@link RegisterTabletProgramsEvent#register}; the visual half lives in
 * {@link com.modpack.linktablet.api.client.TabletProgramClient}.
 *
 * <p>Programs persist on tablets (home rosters, kiosk navigation, the
 * pinned overlay) by {@link #key()}, so the key is FOREVER: renaming it
 * orphans every tablet that stored it. If your addon is uninstalled,
 * stored keys degrade gracefully — roster entries drop, kiosk screens
 * fall back to the launcher — and return when it's reinstalled.
 */
public interface TabletProgram {

    /**
     * Stable identity, {@code "yourmodid:name"} (a valid
     * {@link ResourceLocation} string — registration rejects anything
     * else, including the {@code linktablet} namespace, which is
     * reserved for the built-in programs' bare keys).
     */
    String key();

    /** Launcher tile / App Store row title. */
    Component displayName();

    /** One-line App Store description. */
    Component storeDescription();

    /** Launcher tile chip color, {@code 0xAARRGGBB} (alpha is forced opaque). */
    int chipColor();

    /** Item drawn on the tile chip, or null for a plain color chip. */
    @Nullable
    ResourceLocation iconItem();
}
