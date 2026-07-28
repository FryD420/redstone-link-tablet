package com.modpack.linktablet.api;

import com.modpack.linktablet.Programs;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired on every mod's event bus (both sides) during Link Tablet's
 * common setup: register your {@link TabletProgram}s here. After the
 * event the program table freezes — late registration throws.
 *
 * <pre>{@code
 * // in your mod's constructor:
 * modEventBus.addListener((RegisterTabletProgramsEvent e) ->
 *         e.register(MY_PROGRAM));
 * }</pre>
 */
public class RegisterTabletProgramsEvent extends Event implements IModBusEvent {

    /**
     * Registers a program into the tablet OS. Throws if the key is not a
     * valid namespaced {@code "modid:name"} id, collides with an
     * existing program, or uses the reserved {@code linktablet}
     * namespace.
     */
    public void register(TabletProgram program) {
        Programs.register(program);
    }
}
