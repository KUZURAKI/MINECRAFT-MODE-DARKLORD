package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VoidPayload(int entityId, boolean on) implements CustomPacketPayload {
    public static final Type<VoidPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "void"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VoidPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, VoidPayload::entityId,
            ByteBufCodecs.BOOL, VoidPayload::on,
            VoidPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
