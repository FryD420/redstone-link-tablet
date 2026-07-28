package com.modpack.linktablet.api;

/**
 * Entry point constants for the Link Tablet addon API.
 *
 * <p><b>Stability contract</b>: everything under {@code
 * com.modpack.linktablet.api} is a public commitment. Interfaces here
 * only ever GROW (new methods arrive with default implementations);
 * existing signatures are never changed or removed. {@link #API_VERSION}
 * bumps once per release that grows the surface, so addons can assert a
 * minimum at runtime.
 *
 * <p><b>Getting started</b>: register a {@link TabletProgram} during
 * {@link RegisterTabletProgramsEvent} (fired on every mod's event bus,
 * BOTH sides — your addon must be installed on server and client; the
 * server silently drops roster entries whose key it doesn't know). Then
 * register the visual half, a
 * {@link com.modpack.linktablet.api.client.TabletProgramClient}, during
 * {@link com.modpack.linktablet.api.client.RegisterTabletProgramClientsEvent}
 * (client only). Your program then appears in the tablet's App Store
 * automatically.
 */
public final class LinkTabletApi {

    /** Grows by one with every published addition to the api package. */
    public static final int API_VERSION = 1;

    private LinkTabletApi() {
    }
}
