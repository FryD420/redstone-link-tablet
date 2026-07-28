package com.modpack.linktablet.frequency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Signal-link propagation (1.10.0) — THE one helper both mutation
 * sites call (ModNetworking.handleToggle and TabletBlock's world-tap
 * toggle branch); never fork the traversal. Runs server-side only, on
 * the already-toggled list.
 *
 * <p>Single BFS pass with a visited-id set: loops die (A→B→A applies
 * each signal once per tap), chains work (A→B→C — each changed toggle
 * propagates onward with ITS new state as the follow reference).
 * Toggle targets change persisted state; Timer targets fire their
 * pulse via the callback when the action is an activation and are
 * TERMINAL (a pulse is transient — it has no new state to chain);
 * momentary and slider targets are skipped (v1 scope, user-approved).
 */
public final class SignalLinks {

    /** Fires a Timer target's pulse — the caller closes over its
     * player/hand/pos context (the startTimed shape differs per host). */
    @FunctionalInterface
    public interface TimedStarter {
        void start(int index, Signal target);
    }

    /**
     * Applies the tapped signal's links. @param signals the list AFTER
     * the source toggle was applied; mutated in place via set().
     * @return true when any toggle target changed (caller saves once).
     */
    public static boolean propagate(List<Signal> signals, int sourceIndex, TimedStarter timed) {
        Signal source = signals.get(sourceIndex);
        if (source.links().isEmpty()) return false;
        boolean changed = false;
        Set<Integer> visited = new HashSet<>();
        visited.add(source.linkId());
        // Queue entries: the index of an already-applied signal whose
        // links still need walking
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(sourceIndex);
        while (!queue.isEmpty()) {
            Signal from = signals.get(queue.poll());
            for (Signal.Link link : from.links()) {
                if (link.targetId() == 0 || !visited.add(link.targetId())) continue;
                int targetIndex = indexOfId(signals, link.targetId());
                if (targetIndex < 0) continue; // deleted target — stale link, skip
                Signal target = signals.get(targetIndex);
                boolean activate = switch (link.mode()) {
                    case ON -> true;
                    case OFF -> false;
                    case FOLLOW -> from.active();
                };
                if (target.timed()) {
                    if (activate) timed.start(targetIndex, target);
                    continue; // terminal — a pulse has no state to chain
                }
                if (target.momentary() || target.slider()) continue;
                if (target.active() == activate) continue; // already there — no chain
                signals.set(targetIndex, target.withActive(activate));
                changed = true;
                queue.add(targetIndex);
            }
        }
        return changed;
    }

    private static int indexOfId(List<Signal> signals, int id) {
        for (int i = 0; i < signals.size(); i++) {
            if (signals.get(i).linkId() == id) return i;
        }
        return -1;
    }

    /**
     * handleUpsert's ensure-ids pass: every signal on the tablet gets a
     * nonzero, list-unique linkId (retrofits pre-links tablets one edit
     * at a time). Returns the same list instance, mutated.
     */
    public static List<Signal> ensureIds(List<Signal> signals, net.minecraft.util.RandomSource random) {
        Set<Integer> taken = new HashSet<>();
        for (Signal signal : signals) {
            if (signal.linkId() != 0) taken.add(signal.linkId());
        }
        for (int i = 0; i < signals.size(); i++) {
            Signal signal = signals.get(i);
            if (signal.linkId() != 0) continue;
            int id;
            do {
                id = random.nextInt();
            } while (id == 0 || !taken.add(id));
            signals.set(i, signal.withLinkId(id));
        }
        return signals;
    }

    private SignalLinks() {
    }
}
