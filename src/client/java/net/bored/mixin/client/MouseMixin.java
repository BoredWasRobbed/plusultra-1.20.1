package net.bored.mixin.client;

import net.bored.PlusUltraClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {

    @Inject(method = "onMouseScroll", at = @At(value = "HEAD"), cancellable = true)
    private void onScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        // Calls our client logic. If it returns true, we cancel the vanilla scroll event
        // so the hotbar doesn't change while we cycle abilities.
        if (PlusUltraClient.onScroll(horizontal, vertical)) {
            ci.cancel();
        }
    }
}