package dev.zedkodex.darklord.net;

import dev.zedkodex.darklord.DarkLord;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record AbilityPayload(int ability, int target) implements CustomPacketPayload {
    public static final int ERASE = 0;
    public static final int DASH = 1;
    public static final int HANDS = 2;
    public static final int ABYSS = 3;
    public static final int CLEAVE = 4;
    public static final int DEVOUR = 5;
    public static final int SINGULARITY = 6;
    public static final int LIGHTNING = 7;
    public static final int CATACLYSM = 8;
    public static final int COUNT = 9;
    public static final int[] COOLDOWN = {10, 20, 80, 120, 40, 100, 240, 60, 600};

    public static final Type<AbilityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "ability"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AbilityPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AbilityPayload::ability,
            ByteBufCodecs.VAR_INT, AbilityPayload::target,
            AbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
