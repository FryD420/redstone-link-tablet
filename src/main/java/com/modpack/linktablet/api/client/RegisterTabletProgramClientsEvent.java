package com.modpack.linktablet.api.client;

import com.modpack.linktablet.client.ProgramClients;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired on every mod's event bus during Link Tablet's CLIENT setup:
 * register the {@link TabletProgramClient} for each program you
 * registered in
 * {@link com.modpack.linktablet.api.RegisterTabletProgramsEvent}.
 * A program without a client still shelves in the App Store, but its
 * tile opens nothing — always register both halves.
 */
public class RegisterTabletProgramClientsEvent extends Event implements IModBusEvent {

    /**
     * Binds a client to a registered addon program's key. Throws if the
     * key isn't a registered addon program or already has a client.
     */
    public void register(String key, TabletProgramClient client) {
        ProgramClients.register(key, client);
    }
}
