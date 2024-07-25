package com.moulberry.flashback.exception;

import net.minecraft.network.protocol.Packet;

public class UnsupportedPacketException extends RuntimeException {

    public UnsupportedPacketException(Packet<?> packet) {
        super("Packet " + packet.type().id() + " is not supported");
    }

}
