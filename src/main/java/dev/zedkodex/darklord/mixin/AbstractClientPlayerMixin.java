package dev.zedkodex.darklord.mixin;

import dev.zedkodex.darklord.client.VoidClient;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void darklord$voidSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        PlayerSkin skin = VoidClient.skin((AbstractClientPlayer) (Object) this, cir.getReturnValue());
        if (skin != null) cir.setReturnValue(skin);
    }
}
