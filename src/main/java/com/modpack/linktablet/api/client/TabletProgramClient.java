package com.modpack.linktablet.api.client;

import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

/**
 * The client-side half of an addon program: what opens when the tile is
 * tapped, plus the optional pinned-overlay body and kiosk face.
 * Register during {@link RegisterTabletProgramClientsEvent} under the
 * same key as the common {@link com.modpack.linktablet.api.TabletProgram}.
 */
public interface TabletProgramClient {

    /**
     * The program's full screen — opened from the launcher tile, a kiosk
     * glass tap, or an overlay right-click. Call {@link ProgramHost#goHome}
     * from your screen's {@code onClose} to return to the launcher
     * (recommended); closing plainly leaves the tablet entirely.
     */
    Screen createScreen(ProgramHost host);

    /**
     * Body pane for the pinned overlay window, or null when this program
     * can't be pinned (then {@link ProgramHost#togglePin} no-ops and
     * {@link ProgramHost#isPinned} stays false).
     */
    @Nullable
    default OverlayContent createOverlay(ProgramHost host) {
        return null;
    }

    /**
     * Painter for this program's face on PLACED tablets (walls, merged
     * surfaces, swivel mounts), or null for the default face — the
     * program's name centered on the glass. Either way, tapping the
     * glass opens {@link #createScreen}.
     */
    @Nullable
    default TabletFacePainter facePainter() {
        return null;
    }
}
