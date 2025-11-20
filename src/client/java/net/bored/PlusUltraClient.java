package net.bored;

import net.bored.api.data.IQuirkData;
import net.bored.client.gui.AllForOneRadialScreen;
import net.bored.client.gui.QuirkHud;
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
import org.lwjgl.glfw.GLFW;

public class PlusUltraClient implements ClientModInitializer {

	public static KeyBinding activateKey;
	public static KeyBinding cycleKey;
	public static KeyBinding wheelKey;

	@Override
	public void onInitializeClient() {
		PlusUltraClientNetworking.init();
		HudRenderCallback.EVENT.register(new QuirkHud());

		EntityRendererRegistry.register(PlusUltra.WARP_PROJECTILE, FlyingItemEntityRenderer::new);
		EntityRendererRegistry.register(PlusUltra.VILLAIN_ENTITY, ZombieEntityRenderer::new);

		activateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.plusultra.activate",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_Z,
				"category.plusultra"
		));

		cycleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.plusultra.cycle",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_R,
				"category.plusultra"
		));

		wheelKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"key.plusultra.wheel",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_V,
				"category.plusultra"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (activateKey.wasPressed()) {
				ClientPlayNetworking.send(PlusUltraNetworking.ACTIVATE_ABILITY_PACKET, PacketByteBufs.create());
			}

			while (wheelKey.wasPressed()) {
				// UPDATED: Allow opening if player has ANY quirks in storage OR is AFO
				if (client.player != null) {
					IQuirkData data = (IQuirkData)client.player;
					if (data.isAllForOne() || !data.getStolenQuirks().isEmpty()) {
						client.setScreen(new AllForOneRadialScreen(data));
					}
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