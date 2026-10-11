package io.github.brainage04.flashbackserverbound.mixin;

import io.github.brainage04.flashbackserverbound.ServerboundRecorder;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every packet the client sends to the server it plays on, with the protocol it is encoded in. */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @Unique
    private volatile @Nullable ProtocolInfo<?> flashbackServerbound$outbound;

    @Shadow
    public abstract PacketFlow getSending();

    @Shadow
    public abstract PacketListener getPacketListener();

    @Inject(method = "setupOutboundProtocol", at = @At("HEAD"))
    private void flashbackServerbound$outboundProtocol(ProtocolInfo<?> protocol, CallbackInfo ci) {
        this.flashbackServerbound$outbound = protocol;
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"))
    private void flashbackServerbound$send(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush,
                                           CallbackInfo ci) {
        ProtocolInfo<?> protocol = this.flashbackServerbound$outbound;
        if (protocol != null && this.getSending() == PacketFlow.SERVERBOUND
                && this.getPacketListener() instanceof ClientCommonPacketListenerImpl) {
            ServerboundRecorder.sent(packet, protocol);
        }
    }
}
