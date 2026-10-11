package io.github.brainage04.flashbackserverbound.mixin;

import io.github.brainage04.flashbackserverbound.ServerboundRecorder;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The mouse turns the local player through {@code turn}: the rotation it applied (pitch clamped). */
@Mixin(Entity.class)
public abstract class EntityMixin {
    @Unique
    private static float flashbackServerbound$yawBefore;
    @Unique
    private static float flashbackServerbound$pitchBefore;

    @Shadow
    public abstract float getYRot();

    @Shadow
    public abstract float getXRot();

    @Inject(method = "turn", at = @At("HEAD"))
    private void flashbackServerbound$beforeTurn(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            flashbackServerbound$yawBefore = this.getYRot();
            flashbackServerbound$pitchBefore = this.getXRot();
        }
    }

    @Inject(method = "turn", at = @At("RETURN"))
    private void flashbackServerbound$afterTurn(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            ServerboundRecorder.turned(this.getYRot() - flashbackServerbound$yawBefore,
                    this.getXRot() - flashbackServerbound$pitchBefore);
        }
    }
}
