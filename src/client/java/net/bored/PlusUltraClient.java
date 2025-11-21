package net.bored;

import net.bored.api.data.IQuirkData;
import net.bored.client.gui.AllForOneRadialScreen;
import net.bored.client.gui.QuirkHud;
import net.bored.client.gui.StatScreen;
import net.bored.network.PlusUltraNetworking;
import net.bored.network.PlusUltraClientNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.client.render.entity.ZombieEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class PlusUltraClient implements ClientModInitializer {

	public static KeyBinding activateKey;
	public static KeyBinding cycleKey;
	public static KeyBinding wheelKey;
	public static KeyBinding statKey;

	private boolean wasActivatePressed = false;

	@Override
	public void onInitializeClient() {
		PlusUltraClientNetworking.init();
		HudRenderCallback.EVENT.register(new QuirkHud());

		EntityRendererRegistry.register(PlusUltra.WARP_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(PlusUltra.VILLAIN_ENTITY, ZombieEntityRenderer::new);

		activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.activate", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, "category.plusultra"));
		cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.cycle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.plusultra"));
		wheelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.wheel", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.plusultra"));
		statKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.plusultra.stats", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.plusultra"));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) return;

			IQuirkData data = (IQuirkData) client.player;
			boolean isPropulsion = data.hasQuirk() && data.getQuirk().getId().toString().equals("plusultra:propulsion");
			boolean isOverhaul = data.hasQuirk() && data.getQuirk().getId().toString().equals("plusultra:overhaul");
			int selectedSlot = data.getSelectedSlot();

			// Allow charging for both Propulsion AND Overhaul on Slot 0
			if ((isPropulsion || isOverhaul) && selectedSlot == 0) {
				boolean isPressed = activateKey.isPressed();
				if (isPressed && !wasActivatePressed) {
					PacketByteBuf buf = PacketByteBufs.create();
					buf.writeBoolean(true);
					ClientPlayNetworking.send(PlusUltraNetworking.CHARGE_ABILITY_PACKET, buf);

					// FIX: Set local state immediately for client-side prediction (HUD)
					data.setCharging(true);
				}
				if (wasActivatePressed && !isPressed) {
					ClientPlayNetworking.send(PlusUltraNetworking.ACTIVATE_ABILITY_PACKET, PacketByteBufs.create());

					// FIX: Reset local state on release
					data.setCharging(false);
				}
				wasActivatePressed = isPressed;
				while(activateKey.wasPressed());
			} else {
				while (activateKey.wasPressed()) {
					ClientPlayNetworking.send(PlusUltraNetworking.ACTIVATE_ABILITY_PACKET, PacketByteBufs.create());
				}
				wasActivatePressed = activateKey.isPressed();
			}

			while (wheelKey.wasPressed()) {
				// Logic: Must have AFO OR (at least 1 stored quirk AND (has active quirk or >1 stored))
				// Simplified: Total Quirks Available (Active + Stored) >= 2
				int totalOptions = data.getStolenQuirks().size();
				if (data.hasQuirk() && !data.getStolenQuirks().contains(data.getQuirk().getId().toString())) {
					totalOptions++;
				}

				if (data.isAllForOne() || totalOptions >= 2) {
					client.setScreen(new AllForOneRadialScreen(data));
				} else {
					client.player.sendMessage(Text.literal("You need at least 2 quirks to switch.").formatted(Formatting.RED), true);
				}
			}
			while (statKey.wasPressed()) {
				if (client.currentScreen == null) {
					client.setScreen(new StatScreen());
				}
			}
		});
	}

	public static boolean onScroll(double horizontal, double vertical) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) return false;
		if (cycleKey.isPressed()) {
			int direction = (vertical > 0) ? -1 : 1;
			PacketByteBuf buf = PacketByteBufs.create();
			buf.writeInt(direction);
			buf.writeBoolean(client.player.isSneaking());
			ClientPlayNetworking.send(PlusUltraNetworking.CYCLE_ABILITY_PACKET, buf);
			return true;
		}
		return false;
	}
}