package io.github.brainage04.flashbackserverbound.mixin;

import com.moulberry.flashback.record.Recorder;
import io.github.brainage04.flashbackserverbound.ServerboundRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flashback's per-tick calls: {@code startTick} at the start of every client tick, {@code endTickWithContext} at its
 * end, before Flashback writes the tick. Writing the input there puts it in the tick it belongs to.
 */
@Mixin(Recorder.class)
public abstract class RecorderMixin {
    @Inject(method = "startTick", at = @At("HEAD"))
    private void flashbackServerbound$startTick(CallbackInfo ci) {
        ServerboundRecorder.tickStarted((Recorder) (Object) this);
    }

    @Inject(method = "endTickWithContext", at = @At("HEAD"))
    private void flashbackServerbound$endTick(boolean close, CallbackInfo ci) {
        ServerboundRecorder.tickEnded((Recorder) (Object) this, close);
    }
}
