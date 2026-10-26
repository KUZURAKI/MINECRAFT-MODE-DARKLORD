package dev.zedkodex.darklord.client;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.ActionPayload;
import dev.zedkodex.darklord.net.WraithPayload;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class WraithClient {
    private WraithClient() {}

    public static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "wraith");
    private static final float FORM_MS = 700f;

    private static final class State {
        boolean on;
        long changeMs;
        Vec3 pos;
        Vec3 prev;
        float yaw;
        long updateMs;
    }

    private static final Map<Integer, State> states = new HashMap<>();
    private static long selfChangeMs;
    private static boolean selfOn;

    public static void apply(WraithPayload msg) {
        State s = states.computeIfAbsent(msg.entityId(), k -> new State());
        Vec3 p = new Vec3(msg.x(), msg.y(), msg.z());
        long now = Util.getMillis();
        if (msg.state() != WraithPayload.MOVE) {
            boolean on = msg.state() == WraithPayload.ON;
            if (on != s.on) s.changeMs = now;
            s.on = on;
            LocalPlayer me = Minecraft.getInstance().player;
            if (me != null && me.getId() == msg.entityId()) {
                if (on != selfOn) selfChangeMs = now;
                selfOn = on;
            }
        } else if (!s.on) {
            s.on = true;
            s.changeMs = now;
        }
        s.prev = s.pos == null ? p : s.pos;
        s.pos = p;
        s.yaw = msg.yaw();
        s.updateMs = now;
    }

    private static float form(State s, long now) {
        float t = Gfx.clamp((now - s.changeMs) / FORM_MS);
        return s.on ? Gfx.smooth(t) : 1f - Gfx.smooth(t);
    }

    public static void toggle() {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        if (selfOn) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.WRAITH_OFF));
        else if (!p.isSpectator()) PacketDistributor.sendToServer(new ActionPayload(ActionPayload.WRAITH_ON));
    }

    public static boolean selfOn() {
        return selfOn;
    }

    public static boolean selfActive() {
        return selfOn || Util.getMillis() - selfChangeMs < FORM_MS;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) {
            states.clear();
            selfOn = false;
            return;
        }
        long now = Util.getMillis();
        Iterator<Map.Entry<Integer, State>> it = states.entrySet().iterator();
        while (it.hasNext()) {
            State s = it.next().getValue();
            if ((!s.on && now - s.changeMs > FORM_MS) || (s.on && now - s.updateMs > 3000)) it.remove();
        }
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (selfOn) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (selfActive() && !event.getName().equals(LAYER)) event.setCanceled(true);
    }

    @SubscribeEvent
    static void onFov(ViewportEvent.ComputeFov event) {
        long now = Util.getMillis();
        float burst = 1f - Gfx.clamp((now - selfChangeMs) / 900f);
        if (burst <= 0f && !selfOn) return;
        float wave = (float) Math.sin(burst * Math.PI);
        event.setFOV(event.getFOV() * (1.0 + 0.18 * wave + (selfOn ? 0.06 : 0)));
    }

    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!selfOn) return;
        float t = Util.getMillis() / 1000f;
        event.setRoll(event.getRoll() + (float) Math.sin(t * 0.9) * 2.5f);
    }

    public static void overlay(GuiGraphics g, DeltaTracker delta) {
        long now = Util.getMillis();
        float burst = 1f - Gfx.clamp((now - selfChangeMs) / 700f);
        float k = selfOn ? Gfx.smooth(Gfx.clamp((now - selfChangeMs) / FORM_MS)) : 1f - Gfx.smooth(Gfx.clamp((now - selfChangeMs) / FORM_MS));
        if (k <= 0.01f && burst <= 0f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float beat = (float) Math.pow(Math.max(0, Math.sin(now / 1000.0 * Math.PI * 1.2)), 8);
        g.fill(0, 0, w, h, Gfx.rgba(0x0A0012, 0.22f * k));
        int n = 24;
        int wb = (int) (w * 0.3f);
        for (int i = 0; i < n; i++) {
            float f = 1f - i / (float) n;
            int col = Gfx.rgba(0x000000, k * (0.85f + 0.1f * beat) * f * f);
            int x0 = i * wb / n;
            int x1 = (i + 1) * wb / n;
            g.fill(x0, 0, x1, h, col);
            g.fill(w - x1, 0, w - x0, h, col);
        }
        int hb = (int) (h * 0.32f);
        g.fillGradient(0, 0, w, hb, Gfx.rgba(0x000000, 0.85f * k), Gfx.rgba(0x000000, 0f));
        g.fillGradient(0, h - hb, w, h, Gfx.rgba(0x000000, 0f), Gfx.rgba(0x000000, 0.85f * k));
        for (int i = 0; i < 14; i++) {
            int seed = Gfx.hash(i * 131 + (int) (now / 700));
            boolean left = (seed & 1) == 0;
            int y = seed % Math.max(1, h);
            int len = 30 + seed % 90;
            int x = left ? 0 : w;
            int dir = left ? 1 : -1;
            int col = (seed & 2) == 0 ? 0x5A0A1A : 0x3A0A5C;
            for (int s = 0; s < len; s += 2) {
                int jx = x + dir * s;
                int jy = y + (int) (Math.sin((s + seed) * 0.25) * 6);
                g.fill(jx, jy, jx + 2, jy + 1, Gfx.rgba(col, k * (0.6f + 0.4f * beat) * (1f - s / (float) len)));
            }
        }
        g.fill(0, 0, w, h, Gfx.rgba(0x50000A, 0.12f * beat * k));
        if (burst > 0f) {
            g.fill(0, 0, w, h, Gfx.rgba(0x000000, 0.8f * burst));
            float r = (1f - burst) * Math.max(w, h);
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI * 2 / 64;
                int x = (int) (w / 2f + Math.cos(a) * r);
                int y = (int) (h / 2f + Math.sin(a) * r * 0.6f);
                g.fill(x, y, x + 3, y + 3, Gfx.rgba(0x8A2BE2, burst));
            }
        }
    }

    private static Matrix4f view = new Matrix4f();
    private static Vector3f camRight = new Vector3f();
    private static Vector3f camUp = new Vector3f();

    private static void blob(VertexConsumer vc, Vec3 c, float size, int r, int g, int b, int a) {
        int seg = 10;
        float cx = (float) c.x, cy = (float) c.y, cz = (float) c.z;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg;
            double a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) Math.cos(a0) * size, y0 = (float) Math.sin(a0) * size;
            float x1 = (float) Math.cos(a1) * size, y1 = (float) Math.sin(a1) * size;
            vc.addVertex(view, cx, cy, cz).setColor(r, g, b, a);
            vc.addVertex(view, cx, cy, cz).setColor(r, g, b, a);
            vc.addVertex(view, cx + camRight.x * x1 + camUp.x * y1, cy + camRight.y * x1 + camUp.y * y1, cz + camRight.z * x1 + camUp.z * y1).setColor(r, g, b, 0);
            vc.addVertex(view, cx + camRight.x * x0 + camUp.x * y0, cy + camRight.y * x0 + camUp.y * y0, cz + camRight.z * x0 + camUp.z * y0).setColor(r, g, b, 0);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || states.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null) return;
        long now = Util.getMillis();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        view = new Matrix4f(event.getPoseStack().last().pose());
        camRight = new Vector3f(event.getCamera().getLeftVector()).mul(-1f);
        camUp = new Vector3f(event.getCamera().getUpVector());
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer dark = bs.getBuffer(RenderType.debugQuads());
        float time = now / 1000f;
        for (Map.Entry<Integer, State> en : states.entrySet()) {
            State s = en.getValue();
            boolean self = en.getKey() == me.getId();
            if (self && mc.options.getCameraType().isFirstPerson()) continue;
            float f = form(s, now);
            if (f <= 0.01f || s.pos == null) continue;
            Vec3 pos = self ? me.getPosition(pt) : s.prev.lerp(s.pos, Gfx.clamp((now - s.updateMs) / 50f));
            Vec3 vel = s.pos.subtract(s.prev);
            Vec3 tail = vel.lengthSqr() > 1e-4 ? vel.normalize().scale(-1) : new Vec3(0, -1, 0);
            Vec3 base = pos.subtract(cam);
            float yaw = self ? me.getYRot() : s.yaw;
            Vec3 fwd = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            int seed = en.getKey() * 7919;
            int puffs = 38;
            for (int k = 0; k < puffs; k++) {
                float u = k / (float) (puffs - 1);
                float y = 1.95f - u * 2.3f;
                float rad = u < 0.12f ? 0.3f : u < 0.38f ? 0.52f : 0.52f * (1f - (u - 0.38f) / 0.62f) + 0.08f;
                double ang = k * 2.39996 + time * (1.2 + (Gfx.hash(seed + k) % 100) / 120.0);
                double spread = rad * 0.55;
                double wave = Math.sin(time * 3 + u * 7 + seed) * 0.18 * u;
                Vec3 c = base.add(Math.cos(ang) * spread, y * f + (1 - f) * 1.0, Math.sin(ang) * spread)
                        .add(tail.scale(u * u * 1.4 * f)).add(side.scale(wave));
                float size = rad * (1.3f + 0.35f * (float) Math.sin(time * 2.3 + k)) * (0.4f + 0.6f * f);
                int a = (int) (170 * f * (1f - u * 0.55f));
                blob(dark, c, size, 4, 0, 10, a);
            }
            for (int t = 0; t < 5; t++) {
                double ang = t * Math.PI * 2 / 5 + time * 0.7 + seed;
                Vec3 prev = base.add(0, 1.15 * f, 0);
                for (int i = 1; i <= 9; i++) {
                    float u = i / 9f;
                    Vec3 c = base.add(Math.cos(ang + Math.sin(time * 2 + i) * 0.4) * u * 1.3 * f,
                            (1.15 - u * 1.1 + Math.sin(time * 4 + i + t) * 0.12) * f,
                            Math.sin(ang + Math.sin(time * 2 + i) * 0.4) * u * 1.3 * f);
                    blob(dark, c, 0.12f * (1f - u * 0.7f) * f, 2, 0, 6, (int) (200 * f * (1f - u)));
                    prev = c;
                }
            }
        }
        bs.endBatch(RenderType.debugQuads());
        VertexConsumer glow = bs.getBuffer(RenderType.lightning());
        for (Map.Entry<Integer, State> en : states.entrySet()) {
            State s = en.getValue();
            boolean self = en.getKey() == me.getId();
            if (self && mc.options.getCameraType().isFirstPerson()) continue;
            float f = form(s, now);
            if (f <= 0.01f || s.pos == null) continue;
            Vec3 pos = self ? me.getPosition(pt) : s.prev.lerp(s.pos, Gfx.clamp((now - s.updateMs) / 50f));
            Vec3 base = pos.subtract(cam);
            float yaw = self ? me.getYRot() : s.yaw;
            Vec3 fwd = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            Vec3 side = new Vec3(-fwd.z, 0, fwd.x);
            float flick = 0.75f + 0.25f * (float) Math.sin(time * 17 + en.getKey());
            int blink = (Gfx.hash((int) (now / 180) + en.getKey()) % 23 == 0) ? 0 : 1;
            for (int e = -1; e <= 1; e += 2) {
                Vec3 eye = base.add(0, 1.68 * f, 0).add(fwd.scale(0.3)).add(side.scale(0.1 * e));
                blob(glow, eye, 0.2f * f, 180, 10, 20, (int) (90 * flick * blink));
                blob(glow, eye, 0.06f * f, 255, 60, 50, (int) (255 * flick * blink));
            }
            for (int i = 0; i < 4; i++) {
                Vec3 c = base.add(Math.cos(time * 0.8 + i * 1.6) * 0.3, (0.5 + i * 0.4) * f, Math.sin(time * 0.8 + i * 1.6) * 0.3);
                blob(glow, c, 0.9f * f, 50, 6, 80, (int) (55 * f));
            }
            for (int i = 0; i < 3; i++) {
                double a = time * 3 + i * 2.1;
                Vec3 c = base.add(Math.cos(a) * 0.7, (0.3 + ((time * 0.6 + i * 0.33) % 1.0) * 1.8) * f, Math.sin(a) * 0.7);
                blob(glow, c, 0.14f * f, 40, 90, 255, (int) (200 * f));
            }
        }
        bs.endBatch(RenderType.lightning());
    }
}
