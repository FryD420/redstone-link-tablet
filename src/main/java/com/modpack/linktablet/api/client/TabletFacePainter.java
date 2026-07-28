package com.modpack.linktablet.api.client;

/**
 * Paints an addon program's face on a placed tablet's screen — called
 * every frame the kiosk shows the program (walls, merged surfaces,
 * swivel mounts all included; the context's size reflects the surface).
 *
 * <p>All drawing goes through the {@link TabletFaceContext}, which
 * buffers the calls and flushes them in the renderer's mandatory pass
 * order (all fills, then all items, then all text) — so paint in
 * whatever order reads best; layering within each pass follows call
 * order. Never touch the world renderer directly from here.
 */
@FunctionalInterface
public interface TabletFacePainter {

    void paint(TabletFaceContext ctx);
}
