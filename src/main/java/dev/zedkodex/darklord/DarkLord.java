package dev.zedkodex.darklord;

import dev.zedkodex.darklord.net.AbilityPayload;
import dev.zedkodex.darklord.net.ActionPayload;
import dev.zedkodex.darklord.net.FxPayload;
import dev.zedkodex.darklord.net.GripPayload;
import dev.zedkodex.darklord.net.VoidPayload;
import dev.zedkodex.darklord.net.WraithPayload;
import dev.zedkodex.darklord.server.Actions;
import dev.zedkodex.darklord.server.Grip;
import dev.zedkodex.darklord.server.VoidForm;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(DarkLord.MODID)
public class DarkLord {
    public static final String MODID = "darklord";

    public DarkLord(IEventBus bus) {
        bus.addListener(DarkLord::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        var reg = event.registrar("1");
        reg.playToClient(WraithPayload.TYPE, WraithPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.zedkodex.darklord.client.WraithClient.apply(payload)));
        reg.playToClient(VoidPayload.TYPE, VoidPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.zedkodex.darklord.client.VoidClient.apply(payload)));
        reg.playToClient(FxPayload.TYPE, FxPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> dev.zedkodex.darklord.client.AbilityFx.add(payload)));
        reg.playToServer(GripPayload.TYPE, GripPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) Grip.handle(sp, payload);
                }));
        reg.playToServer(AbilityPayload.TYPE, AbilityPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) VoidForm.ability(sp, payload.ability(), payload.target());
                }));
        reg.playToServer(ActionPayload.TYPE, ActionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> {
                    if (ctx.player() instanceof ServerPlayer sp) Actions.handle(sp, payload.action());
                }));
    }
}
