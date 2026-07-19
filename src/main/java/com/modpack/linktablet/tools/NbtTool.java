package com.modpack.linktablet.tools;

import com.modpack.linktablet.frequency.Frequency;
import com.modpack.linktablet.frequency.SignalApp;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dev-only tool (excluded from the jar): {@code dump <file>} prints a
 * structure .nbt as SNBT; {@code gen <file>} writes the ponder scene
 * schematic. Run via {@code ./gradlew nbtTool --args="gen <path>"} —
 * use a space-free output path, Gradle's arg parsing splits on spaces.
 */
public final class NbtTool {

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "dump" -> {
                CompoundTag tag = NbtIo.readCompressed(Path.of(args[1]), NbtAccounter.unlimitedHeap());
                System.out.println(NbtUtils.structureToSnbt(tag));
            }
            case "gen" -> gen(Path.of(args[1]));
            default -> throw new IllegalArgumentException("dump|gen <file>");
        }
    }

    /**
     * The ponder scene: 5×5 checkered baseplate (Create's convention),
     * a Redstone Link receiver with a torch/torch frequency, a lamp
     * behind it, and a floor-mounted tablet carrying the matching app.
     */
    private static void gen(Path out) throws Exception {
        SharedConstants.tryDetectVersion();

        CompoundTag root = new CompoundTag();
        root.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        root.put("size", intList(5, 2, 5));
        root.put("entities", new ListTag());

        ListTag palette = new ListTag();
        int concrete = addState(palette, "minecraft:white_concrete", Map.of());
        int snow = addState(palette, "minecraft:snow_block", Map.of());
        int link = addState(palette, "create:redstone_link",
                Map.of("facing", "up", "powered", "false", "receiver", "true"));
        int lamp = addState(palette, "minecraft:redstone_lamp", Map.of("lit", "false"));
        int tablet = addState(palette, "linktablet:tablet",
                Map.of("face", "floor", "facing", "west", "lit", "false"));
        root.put("palette", palette);

        ListTag blocks = new ListTag();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                blocks.add(block(x, 0, z, (x + z) % 2 == 0 ? concrete : snow, null));
            }
        }
        blocks.add(block(1, 1, 2, link, linkNbt()));
        blocks.add(block(1, 1, 3, lamp, null));
        blocks.add(block(3, 1, 2, tablet, tabletNbt()));
        root.put("blocks", blocks);

        NbtIo.writeCompressed(root, out);
        System.out.println("wrote " + out.toAbsolutePath());
    }

    /** Receiver with a redstone-torch/redstone-torch frequency. */
    private static CompoundTag linkNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "create:redstone_link");
        tag.put("FrequencyFirst", itemStack("minecraft:redstone_torch"));
        tag.put("FrequencyLast", itemStack("minecraft:redstone_torch"));
        tag.putBoolean("Transmitter", false);
        tag.putBoolean("ReceivedChanged", false);
        tag.putInt("Receive", 0);
        tag.putInt("Transmit", 0);
        return tag;
    }

    /** Tablet pre-configured with the matching "Lamp" app, toggled off. */
    private static CompoundTag tabletNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "linktablet:tablet");
        SignalApp app = new SignalApp("Lamp",
                List.of(Frequency.of(Items.REDSTONE_TORCH, Items.REDSTONE_TORCH)),
                false, false, SignalApp.MAX_STRENGTH, 0xFFF9801D, Optional.empty(), false,
                0, SignalApp.MAX_STRENGTH, "", false, SignalApp.DEFAULT_PULSE_TICKS);
        SignalApp.CODEC.listOf().encodeStart(NbtOps.INSTANCE, List.of(app))
                .result().ifPresent(t -> tag.put("apps", t));
        return tag;
    }

    private static CompoundTag itemStack(String id) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putInt("count", 1);
        return tag;
    }

    private static int addState(ListTag palette, String name, Map<String, String> props) {
        CompoundTag state = new CompoundTag();
        state.putString("Name", name);
        if (!props.isEmpty()) {
            CompoundTag properties = new CompoundTag();
            props.forEach(properties::putString);
            state.put("Properties", properties);
        }
        palette.add(state);
        return palette.size() - 1;
    }

    private static CompoundTag block(int x, int y, int z, int state, CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", intList(x, y, z));
        tag.putInt("state", state);
        if (nbt != null) tag.put("nbt", nbt);
        return tag;
    }

    private static ListTag intList(int... values) {
        ListTag list = new ListTag();
        for (int v : values) list.add(IntTag.valueOf(v));
        return list;
    }
}
