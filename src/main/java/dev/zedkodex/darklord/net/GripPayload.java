package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GripPayload(int action, int entityId, float value) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int STOP = 1;
    public static final int DISTANCE = 2;
    public static final int CRUSH = 3;
    public static final int ERASE = 4;

    public static final Type<GripPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "grip"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GripPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GripPayload::action,
            ByteBufCodecs.VAR_INT, GripPayload::entityId,
            ByteBufCodecs.FLOAT, GripPayload::value,
            GripPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
