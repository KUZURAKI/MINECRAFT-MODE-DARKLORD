package dev.zedkodex.darklord.server;

import dev.zedkodex.darklord.net.ActionPayload;
import net.minecraft.server.level.ServerPlayer;

public final class Actions {
    private Actions() {}

    public static void handle(ServerPlayer p, int action) {
        switch (action) {
            case ActionPayload.WRAITH_ON -> Wraith.toggle(p, true);
            case ActionPayload.WRAITH_OFF -> Wraith.toggle(p, false);
            case ActionPayload.VOID_TOGGLE -> VoidForm.toggle(p);
            default -> {
            }
        }
    }
}
