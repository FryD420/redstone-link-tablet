package com.modpack.linktablet.client.screen;

import com.modpack.linktablet.client.AppView;
import net.minecraft.client.gui.screens.Screen;

/**
 * 🕹️ Client dispatch for the secret games — ids come from
 * {@code SignalApp.secretGameId()}; extend BOTH tables together.
 */
public final class SecretGames {

    public static Screen create(String id, AppView view, boolean returnToTablet) {
        return switch (id) {
            case "lights" -> new LightsOutScreen(view, returnToTablet);
            case "sweeper" -> new SweeperScreen(view, returnToTablet);
            case "whack" -> new WhackScreen(view, returnToTablet);
            case "breakout" -> new BreakoutScreen(view, returnToTablet);
            case "simon" -> new SimonScreen(view, returnToTablet);
            case "reflex" -> new ReflexScreen(view, returnToTablet);
            case "pong" -> new PongScreen(view, returnToTablet);
            case "flappy" -> new FlappyScreen(view, returnToTablet);
            case "dodge" -> new DodgeScreen(view, returnToTablet);
            case "memory" -> new PairsScreen(view, returnToTablet);
            case "2048" -> new G2048Screen(view, returnToTablet);
            case "crossing" -> new CrossingScreen(view, returnToTablet);
            case "runner" -> new RunnerScreen(view, returnToTablet);
            case "stacker" -> new StackerScreen(view, returnToTablet);
            case "invaders" -> new InvadersScreen(view, returnToTablet);
            case "crates" -> new CratesScreen(view, returnToTablet);
            case "life" -> new LifeScreen(view, returnToTablet);
            case "paint" -> new PaintScreen(view, returnToTablet);
            default -> new SnakeScreen(view, returnToTablet);
        };
    }

    private SecretGames() {
    }
}
