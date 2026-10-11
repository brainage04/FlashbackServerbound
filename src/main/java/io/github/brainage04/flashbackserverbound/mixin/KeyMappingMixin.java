package io.github.brainage04.flashbackserverbound.mixin;

import io.github.brainage04.flashbackserverbound.ServerboundRecorder;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Clicks handled: the game consumes each attack and use click once, in the tick it acts on it. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "consumeClick", at = @At("RETURN"))
    private void flashbackServerbound$consumeClick(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            ServerboundRecorder.clicked((KeyMapping) (Object) this);
        }
    }
}
