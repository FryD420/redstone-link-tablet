package com.modpack.linktablet.client;

import com.modpack.linktablet.Program;
import com.modpack.linktablet.client.screen.TabletScreen;
import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.Signal;
import com.modpack.linktablet.menu.SignalEditMenu;
import com.modpack.linktablet.network.ModNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Client-only entry points. Only ever invoked from code paths guarded by
 * {@code level.isClientSide}, so this class is never loaded on a
 * dedicated server.
 */
public class ClientHooks {

    public static void openTabletScreen(InteractionHand hand) {
        openResumed(new SignalView.Hand(hand));
    }

    /** Held/slot open (1.10.0): resumes wherever the tablet was last —
     * first ever open lands on the launcher. */
    public static void openResumed(SignalView view) {
        openProgram(com.modpack.linktablet.Programs.byKey(ClientPrefs.lastProgram()), view);
    }

    /** Opens a program screen with the open sound, remembering it as
     * the resume target (held/slot views only — kiosk GUIs don't count,
     * their nav lives on the block entity). */
    public static void openProgram(com.modpack.linktablet.api.TabletProgram program, SignalView view) {
        UISounds.open();
        showProgram(program, view);
    }

    /** Return trip into a program (onClose paths) — no open sound. */
    public static void returnToProgram(com.modpack.linktablet.api.TabletProgram program, SignalView view) {
        showProgram(program, view);
    }

    /** Game exits: the kiosk face stays where it was — a wall left on
     * Paint keeps its mural (spec acceptance); only explicit navigation
     * (launching an app, Home from a non-game screen) repoints the
     * face. Mirrors {@link #showProgram} minus the Block-branch
     * {@code SetProgramPayload} send. */
    public static void returnToProgramSilently(com.modpack.linktablet.api.TabletProgram program, SignalView view) {
        if (!(view instanceof SignalView.Block)) {
            ClientPrefs.setLastProgram(program.key());
        }
        Minecraft.getInstance().setScreen(screenFor(program, view));
    }

    public static void openHome(SignalView view) {
        openProgram(Program.LAUNCHER, view);
    }

    /** Kiosk launcher-face tap (1.10.0): opens the block-bound launcher
     * GUI — the device settings menu — never the signals grid. Resolves
     * the controller like {@link #openTabletBlockScreen}. */
    public static void openBlockHome(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(pos) instanceof com.modpack.linktablet.block.TabletBlockEntity be) {
            var controller = be.resolveController();
            if (controller != null) {
                pos = controller.getBlockPos();
            }
        }
        openHome(new SignalView.Block(pos));
    }

    public static void returnHome(SignalView view) {
        returnToProgram(Program.LAUNCHER, view);
    }

    /** Kiosk glass tap on a non-signals program face (1.10.0 suite):
     * opens that program's screen bound to the block, client-side only —
     * the secret-game precedent. Resolves the controller like
     * {@link #openTabletBlockScreen}. */
    public static void openBlockProgram(com.modpack.linktablet.api.TabletProgram program, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(pos) instanceof com.modpack.linktablet.block.TabletBlockEntity be) {
            var controller = be.resolveController();
            if (controller != null) {
                pos = controller.getBlockPos();
            }
        }
        openProgram(program, new SignalView.Block(pos));
    }

    private static void showProgram(com.modpack.linktablet.api.TabletProgram program, SignalView view) {
        if (!(view instanceof SignalView.Block)) {
            ClientPrefs.setLastProgram(program.key());
        } else {
            // Kiosk follows GUI navigation (user feedback, 1.10.0):
            // switching apps or going Home in a block-bound GUI points
            // the wall screen at the same program. Server no-ops when
            // already there (face taps arrive pre-switched).
            PacketDistributor.sendToServer(new ModNetworking.SetProgramPayload(
                    view.target(), program.key()));
        }
        Minecraft.getInstance().setScreen(screenFor(program, view));
    }

    private static net.minecraft.client.gui.screens.Screen screenFor(
            com.modpack.linktablet.api.TabletProgram program, SignalView view) {
        if (program instanceof Program builtin) {
            // Game apps (third test pass: every game is its own program):
            // launch through the SecretGames dispatch; ESC goes Home — the
            // tile is the door, unlike secret-pip launches which return to
            // the signals grid they hide on
            String game = builtin.gameId();
            if (game != null) {
                return com.modpack.linktablet.client.screen.SecretGames.createApp(game, view);
            }
            return switch (builtin) {
                case SIGNALS -> new TabletScreen(view);
                case CLOCK -> new com.modpack.linktablet.client.screen.ClockScreen(view);
                case CALCULATOR -> new com.modpack.linktablet.client.screen.CalculatorScreen(view);
                case GAUGES -> new com.modpack.linktablet.client.screen.GaugesScreen(view);
                case STORE -> new com.modpack.linktablet.client.screen.StoreScreen(view);
                case MONITOR -> new com.modpack.linktablet.client.screen.MonitorScreen(view);
                case TWITCH -> new com.modpack.linktablet.client.screen.TwitchScreen(view);
                case ARCADE -> new com.modpack.linktablet.client.screen.ArcadeHubScreen(view);
                default -> new com.modpack.linktablet.client.screen.LauncherScreen(view);
            };
        }
        // Addon programs (the API): the registered client makes the
        // screen; a program whose client mod is missing degrades to the
        // launcher — the same downgrade story as unknown keys
        var client = ProgramClients.get(program.key());
        if (client != null) {
            return client.createScreen(new ProgramHostImpl(view, program));
        }
        return new com.modpack.linktablet.client.screen.LauncherScreen(view);
    }

    public static void openTabletBlockScreen(BlockPos pos) {
        UISounds.open();
        // Merged surfaces: the GUI always shows the controller's data
        // (belt-and-suspenders — SignalView.Block also resolves)
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null
                && mc.level.getBlockEntity(pos) instanceof com.modpack.linktablet.block.TabletBlockEntity be) {
            var controller = be.resolveController();
            if (controller != null) {
                pos = controller.getBlockPos();
            }
        }
        mc.setScreen(new TabletScreen(new SignalView.Block(pos)));
    }

    /** 🕹️ World tap on a secret-game pip: straight into the game.
     * ESC returns to the world — the player never asked for the GUI. */
    public static void openSecretGame(String id, BlockPos controllerPos) {
        UISounds.open();
        Minecraft.getInstance().setScreen(com.modpack.linktablet.client.screen.SecretGames
                .create(id, new SignalView.Block(controllerPos), false));
    }

    /** Begins a placed-tablet slider drag (client drives it from here). */
    public static void startBlockSliderDrag(BlockPos memberPos, BlockPos controllerPos, int index) {
        BlockSliderDrag.start(memberPos, controllerPos, index);
    }

    /**
     * Right-clicked a Redstone Link with the tablet: open the signal editor
     * pre-filled with the link's frequency (full stacks — components
     * intact). If a signal already carries that frequency, edit it instead
     * of duplicating.
     */
    public static void openLinkPrefill(InteractionHand hand, ItemStack stack1, ItemStack stack2) {
        Minecraft mc = Minecraft.getInstance();
        SignalView view = new SignalView.Hand(hand);
        List<Signal> signals = view.signals();
        Frequency freq = Frequency.of(stack1, stack2);
        ModNetworking.SignalTarget target = view.target();

        for (int i = 0; i < signals.size(); i++) {
            Signal signal = signals.get(i);
            if (signal.frequencies().contains(freq)) {
                mc.player.displayClientMessage(Component.translatable(
                        "message.linktablet.link_already_added", signal.name()), true);
                UISounds.open();
                PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                        SignalEditMenu.EditContext.plain(target, i)));
                return;
            }
        }
        if (signals.size() >= ModNetworking.MAX_SIGNALS) {
            mc.player.displayClientMessage(
                    Component.translatable("message.linktablet.tablet_full"), true);
            return;
        }
        String name = (stack1.isEmpty() ? stack2 : stack1).getHoverName().getString();
        if (name.length() > Signal.MAX_NAME_LENGTH) {
            name = name.substring(0, Signal.MAX_NAME_LENGTH);
        }
        UISounds.open();
        // A full pair prefills the ghost staging slots (the classic flow —
        // Save auto-commits); a half-set link commits its lone-item
        // frequency directly (screen-side), since staging needs both.
        PacketDistributor.sendToServer(new ModNetworking.OpenEditMenuPayload(
                new SignalEditMenu.EditContext(target, -1, stack1, stack2, name)));
    }
}
