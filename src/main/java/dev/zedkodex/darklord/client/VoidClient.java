package dev.zedkodex.darklord.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.AbilityPayload;
import dev.zedkodex.darklord.net.ActionPayload;
import dev.zedkodex.darklord.net.VoidPayload;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class VoidClient {
    private VoidClient() {}

    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "void");
    private static final float FORM_MS = 1400f;
    private static final String[] NAMES = {"СТИРАНИЕ", "ТЕНЕВОЙ РЫВОК", "ДЛАНИ БЕЗДНЫ", "ПРЕИСПОДНЯЯ", "РАЗРУБ", "ПОЖИРАНИЕ", "СИНГУЛЯРНОСТЬ"};
    private static final String[] HINTS = {"стереть цель в тень", "рывок сквозь тьму", "руки из порталов", "утащить цель под землю",
            "клинок тьмы перед собой", "выпить жизнь вокруг", "чёрная дыра по взгляду"};
    private static final long[] lastCast = new long[AbilityPayload.COUNT];
    private static int selected;
    private static long scrollMs;
    private static float menu;
    private static float slide;
    private static long lastFrame;

    private record State(boolean on, long changeMs) {}

    private static final Map<Integer, State> states = new HashMap<>();
    private static long abilityMs;

    public static void apply(VoidPayload msg) {
        State old = states.get(msg.entityId());
        if (old != null && old.on() == msg.on()) return;
        states.put(msg.entityId(), new State(msg.on(), Util.getMillis()));
    }

    public static float form(Player p) {
        State s = states.get(p.getId());
        if (s == null) return 0f;
        float t = Gfx.clamp((Util.getMillis() - s.changeMs()) / FORM_MS);
        return s.on() ? Gfx.smooth(t) : 1f - Gfx.smooth(t);
    }

    public static boolean isOn(Player p) {
        State s = states.get(p.getId());
        return s != null && s.on();
    }

    private static final boolean[] held = new boolean[7];

    private static boolean down(Minecraft mc, int key) {
        return InputConstants.isKeyDown(mc.getWindow().getWindow(), key);
    }

    private static boolean edge(int slot, boolean now) {
        boolean fired = now && !held[slot];
        held[slot] = now;
        return fired;
    }

    @SubscribeEvent
    static void onTickPre(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) {
            java.util.Arrays.fill(held, false);
            return;
        }
        boolean ctrl = down(mc, GLFW.GLFW_KEY_LEFT_CONTROL);
        boolean alt = altDown(mc);
        if (edge(0, ctrl && alt)) WraithClient.toggle();
        if (!alt || ctrl) {
            held[1] = false;
            return;
        }
        while (mc.options.keyHotbarSlots[0].consumeClick()) {
        }
        if (edge(1, down(mc, GLFW.GLFW_KEY_1))) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.VOID_TOGGLE));
    }

    private static boolean altDown(Minecraft mc) {
        return down(mc, GLFW.GLFW_KEY_LEFT_ALT) || down(mc, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null || !isOn(p) || GripClient.gripping || !altDown(mc)) return;
        double d = event.getScrollDeltaY();
        if (d == 0) return;
        selected = Math.floorMod(selected + (d > 0 ? -1 : 1), AbilityPayload.COUNT);
        scrollMs = Util.getMillis();
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || !isOn(p) || GripClient.gripping || !event.isAttack()) return;
        event.setCanceled(true);
        event.setSwingHand(true);
        long now = Util.getMillis();
        if (now - lastCast[selected] < AbilityPayload.COOLDOWN[selected] * 50L) return;
        int id = GripClient.pick(mc.level, p);
        PacketDistributor.sendToServer(new AbilityPayload(selected, id));
        lastCast[selected] = now;
        abilityMs = now;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            states.clear();
            VoidSkins.retain(java.util.Set.of());
            return;
        }
        long now = Util.getMillis();
        states.entrySet().removeIf(e -> !e.getValue().on() && now - e.getValue().changeMs() > FORM_MS);
        VoidSkins.retain(states.keySet());
        for (Player pl : mc.level.players()) {
            if (isOn(pl) && !pl.onGround()) pl.walkAnimation.setSpeed(0f);
        }
    }

    @SubscribeEvent
    static void onLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (event.getEntity() instanceof Player pl && isOn(pl) && !pl.onGround()) pl.walkAnimation.setSpeed(0f);
    }

    public static final ResourceLocation VOID_SKIN = ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "textures/entity/void_skin.png");

    public static PlayerSkin skin(AbstractClientPlayer p, PlayerSkin original) {
        float f = form(p);
        if (original == null || f <= 0.01f) return null;
        ResourceLocation id = VoidSkins.get(p.getId(), original.texture(), f);
        if (id == null) return null;
        return new PlayerSkin(id, null, f > 0.3f ? null : original.capeTexture(), original.elytraTexture(), original.model(), original.secure());
    }

    private static void menu(GuiGraphics g, Minecraft mc, long now) {
        float dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
        lastFrame = now;
        boolean on = isOn(mc.player);
        boolean open = on && (altDown(mc) || now - scrollMs < 1400);
        menu += ((open ? 1f : 0f) - menu) * (1f - (float) Math.exp(-dt / 110f));
        slide += ((on ? 1f : 0f) - slide) * (1f - (float) Math.exp(-dt / 180f));
        if (slide < 0.01f) return;
        int w = g.guiWidth();
        int cw = 118;
        int ch = 15;
        int gap = 3;
        int x0 = w - cw - 8 + (int) ((1f - slide) * (cw + 20));
        int y0 = 8;
        for (int i = 0; i < AbilityPayload.COUNT; i++) {
            boolean sel = i == selected;
            float vis = sel ? 1f : menu;
            if (vis < 0.02f) continue;
            int row = sel && menu < 0.5f ? 0 : i;
            int y = y0 + (int) (row * (ch + gap) * (sel ? menu : 1f));
            int x = x0 + (sel ? 0 : (int) ((1f - menu) * 30));
            float a = vis * slide;
            g.fill(x - 1, y - 1, x + cw + 1, y + ch + 1, Gfx.rgba(0x000000, 0.9f * a));
            g.fill(x, y, x + cw, y + ch, Gfx.rgba(sel ? 0x1A0F26 : 0x12161F, 0.75f * a));
            g.fill(x, y, x + cw, y + 1, Gfx.rgba(0xFFFFFF, 0.28f * a));
            g.fill(x, y, x + 1, y + ch, Gfx.rgba(0xFFFFFF, 0.28f * a));
            g.fill(x, y + ch - 1, x + cw, y + ch, Gfx.rgba(0x000000, 0.35f * a));
            if (sel) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(now / 220.0);
                g.fill(x, y, x + 2, y + ch, Gfx.rgba(0xB070FF, a * pulse));
            }
            float cd = Gfx.clamp(1f - (now - lastCast[i]) / (AbilityPayload.COOLDOWN[i] * 50f));
            if (cd > 0f) g.fill(x + 1, y + ch - 3, x + 1 + (int) ((cw - 2) * cd), y + ch - 1, Gfx.rgba(0x7A3CC8, 0.9f * a));
            Gfx.text(g, NAMES[i], x + 6, y + 3, Gfx.rgba(sel ? 0xFFFFFF : 0xB8B8C8, a), 0.75f, false);
            if (sel && menu > 0.5f) Gfx.text(g, HINTS[i], x - Gfx.width(HINTS[i]) * 0.6f - 8, y + 4, Gfx.rgba(0xC8A8FF, 0.8f * a * menu), 0.6f, true);
        }
        if (on && menu < 0.5f) {
            Gfx.text(g, "ALT + КОЛЕСО", x0 + 6, y0 + ch + 4, Gfx.rgba(0x8A7AA8, 0.6f * slide * (1f - menu * 2f)), 0.55f, false);
        }
    }

    public static void overlay(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        menu(g, mc, Util.getMillis());
        float f = form(mc.player);
        long now = Util.getMillis();
        State s = states.get(mc.player.getId());
        float change = s == null ? 0f : 1f - Gfx.clamp((now - s.changeMs()) / FORM_MS);
        float cast = 1f - Gfx.clamp((now - abilityMs) / 350f);
        if (f <= 0.01f && change <= 0f && cast <= 0f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        int n = 18;
        int wb = (int) (w * 0.16f);
        for (int i = 0; i < n; i++) {
            float k = 1f - i / (float) n;
            int col = Gfx.rgba(0x000000, (0.5f * f + 0.3f * cast) * k * k);
            g.fill(i * wb / n, 0, (i + 1) * wb / n, h, col);
            g.fill(w - (i + 1) * wb / n, 0, w - i * wb / n, h, col);
        }
        if (change > 0f) {
            float wave = (float) Math.sin(change * Math.PI);
            g.fill(0, 0, w, h, Gfx.rgba(0x000000, 0.65f * wave));
            for (int i = 0; i < 40; i++) {
                int seed = Gfx.hash(i * 53 + 7);
                int x = seed % Math.max(1, w);
                int len = (int) ((40 + seed % 120) * wave);
                g.fill(x, 0, x + 1 + seed % 2, len, Gfx.rgba(0x000000, 0.9f * wave));
                g.fill(x, h - len, x + 1 + seed % 2, h, Gfx.rgba(0x000000, 0.9f * wave));
            }
        }
    }
}
