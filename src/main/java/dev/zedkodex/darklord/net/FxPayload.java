package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FxPayload(int kind, int caster, int target, double x, double y, double z, float yaw) implements CustomPacketPayload {
    public static final int HANDS = 1;
    public static final int ABYSS = 2;
    public static final int CLEAVE = 3;

    public static final Type<FxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "fx"));

    public static final StreamCodec<ByteBuf, FxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeInt(p.caster);
                buf.writeInt(p.target);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeFloat(p.yaw);
            },
            buf -> new FxPayload(buf.readByte(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
