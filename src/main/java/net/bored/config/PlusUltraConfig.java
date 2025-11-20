package net.bored.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Identifier;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PlusUltraConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("plusultra.json");

    private static PlusUltraConfig INSTANCE;

    // --- CONFIG FIELDS ---

    // Should players get a random quirk when they join for the first time?
    public boolean enableQuirkOnJoin = true;

    // List of Quirks allowed in the random join pool
    public List<String> starterQuirks = new ArrayList<>(Arrays.asList(
            "plusultra:super_regeneration",
            "plusultra:warp_gate",
            "plusultra:all_for_one"
    ));

    // List of Quirks that are completely disabled (cannot be equipped)
    public List<String> disabledQuirks = new ArrayList<>();

    // --- LOGIC ---

    public static PlusUltraConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, PlusUltraConfig.class);
            } catch (IOException e) {
                e.printStackTrace();
                INSTANCE = new PlusUltraConfig();
            }
        } else {
            INSTANCE = new PlusUltraConfig();
            save();
        }
    }

    public static void save() {
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isQuirkDisabled(Identifier id) {
        if (id == null) return false;
        return disabledQuirks.contains(id.toString());
    }
}