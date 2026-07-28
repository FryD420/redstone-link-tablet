package com.modpack.linktablet.api.client;

import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.network.chat.Component;

/**
 * The tablet an addon program is currently running on — handed to every
 * {@link TabletProgramClient} factory. The handle stays live (a pinned
 * overlay's tablet can move between inventory slots), so read through it
 * each frame rather than caching values.
 */
public interface ProgramHost {

    /** The tablet's UI theme — draw with its colors to match the OS. */
    ScreenTheme theme();

    /** The tablet's (anvil) name, or the generic title when unnamed. */
    Component tabletName();

    /**
     * Navigates back to this tablet's launcher — call from your screen's
     * {@code onClose} so ESC lands on Home like the built-in apps.
     */
    void goHome();

    /** Whether the pinned overlay currently shows THIS program on THIS tablet. */
    boolean isPinned();

    /**
     * Pins the overlay to this program on this tablet, or unpins it if
     * {@link #isPinned()}; no-ops when the program has no
     * {@link TabletProgramClient#createOverlay overlay content}.
     */
    void togglePin();
}
