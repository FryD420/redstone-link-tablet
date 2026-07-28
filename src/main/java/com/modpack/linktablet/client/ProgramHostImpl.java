package com.modpack.linktablet.client;

import com.modpack.linktablet.api.TabletProgram;
import com.modpack.linktablet.api.client.ProgramHost;
import com.modpack.linktablet.api.client.TabletProgramClient;
import com.modpack.linktablet.theme.ScreenTheme;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

/**
 * The {@link ProgramHost} handed to addon program factories: a live
 * view supplier (pinned overlays re-bind slots on self-heal) plus the
 * standard nav/pin plumbing every built-in screen uses.
 */
public record ProgramHostImpl(Supplier<SignalView> view, TabletProgram program)
        implements ProgramHost {

    public ProgramHostImpl(SignalView view, TabletProgram program) {
        this(() -> view, program);
    }

    @Override
    public ScreenTheme theme() {
        return view.get().theme();
    }

    @Override
    public Component tabletName() {
        return view.get().displayName();
    }

    @Override
    public void goHome() {
        ClientHooks.returnHome(view.get());
    }

    @Override
    public boolean isPinned() {
        return OverlayPin.isPinned(view.get(), program);
    }

    @Override
    public void togglePin() {
        if (isPinned()) {
            OverlayPin.unpin();
            return;
        }
        // Pinning only makes sense with overlay content to show
        TabletProgramClient client = ProgramClients.get(program.key());
        if (client != null && client.createOverlay(this) != null) {
            OverlayPin.pin(view.get(), program);
        }
    }
}
