package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FxPayload(int kind, int caster, int target, double x, double y, double z, double x2, double y2, double z2, float yaw)
        implements CustomPacketPayload {
    public static final int HANDS = 1;
    public static final int ABYSS = 2;
    public static final int CLEAVE = 3;
    public static final int CAST = 4;
    public static final int DASH = 5;
    public static final int ERASE = 6;
    public static final int DEVOUR = 7;
    public static final int SINGULARITY = 8;
    public static final int LIGHTNING = 9;
    public static final int CATACLYSM = 10;

    public static final Type<FxPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "fx"));

    public static final StreamCodec<ByteBuf, FxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeInt(p.caster);
                buf.writeInt(p.target);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeDouble(p.x2);
                buf.writeDouble(p.y2);
                buf.writeDouble(p.z2);
                buf.writeFloat(p.yaw);
            },
            buf -> new FxPayload(buf.readByte(), buf.readInt(), buf.readInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
