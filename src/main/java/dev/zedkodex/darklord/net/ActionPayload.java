package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ActionPayload(int action) implements CustomPacketPayload {
    public static final int WRAITH_ON = 6;
    public static final int WRAITH_OFF = 7;
    public static final int VOID_TOGGLE = 8;

    public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionPayload::action,
            ActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
