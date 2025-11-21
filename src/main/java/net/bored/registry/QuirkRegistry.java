package net.bored.registry;

import net.bored.api.quirk.Quirk;
import net.bored.common.quirks.*;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.registry.RegistryKey;

// If you want a custom Registry, this is the Fabric way.
// Alternatively, you can just use a static Map<Identifier, Quirk> for simplicity.
public class QuirkRegistry {

    // Define the Registry Key
    public static final RegistryKey<Registry<Quirk>> QUIRK_REGISTRY_KEY =
            RegistryKey.ofRegistry(new Identifier("plusultra", "quirks"));

    // Create the Registry
    public static final Registry<Quirk> QUIRK =
            FabricRegistryBuilder.createSimple(QUIRK_REGISTRY_KEY).buildAndRegister();

    // Helper method to register quirks
    public static Quirk register(Quirk quirk) {
        return Registry.register(QUIRK, quirk.getId(), quirk);
    }

    public static Quirk get(Identifier id) {
        return QUIRK.get(id);
    }

    public static void init() {
        QuirkRegistry.register(new WarpGateQuirk());
        QuirkRegistry.register(new SuperRegenerationQuirk());
        QuirkRegistry.register(new AllForOneQuirk());
        QuirkRegistry.register(new FloatQuirk());
        QuirkRegistry.register(new AttractionQuirk());
        QuirkRegistry.register(new PropulsionQuirk());
        QuirkRegistry.register(new OverhaulQuirk());
    }
}