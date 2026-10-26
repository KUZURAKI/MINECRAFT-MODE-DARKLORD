package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record WraithPayload(int entityId, int state, double x, double y, double z, float yaw) implements CustomPacketPayload {
    public static final int OFF = 0;
    public static final int ON = 1;
    public static final int MOVE = 2;

    public static final Type<WraithPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "wraith"));

    public static final StreamCodec<ByteBuf, WraithPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeInt(p.entityId);
                buf.writeByte(p.state);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yaw);
            },
            buf -> new WraithPayload(buf.readInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
