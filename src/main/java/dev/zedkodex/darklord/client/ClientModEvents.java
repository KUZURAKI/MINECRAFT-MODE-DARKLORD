package dev.zedkodex.darklord.client;

import dev.zedkodex.darklord.DarkLord;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = DarkLord.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof PlayerRenderer renderer) renderer.addLayer(new VoidLayer(renderer));
        }
    }

    @SubscribeEvent
    static void onGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "curse"), GripClient::overlay);
        event.registerAboveAll(WraithClient.LAYER, WraithClient::overlay);
        event.registerAboveAll(VoidClient.LAYER, VoidClient::overlay);
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "fx"), FxKit::overlay);
    }
}
