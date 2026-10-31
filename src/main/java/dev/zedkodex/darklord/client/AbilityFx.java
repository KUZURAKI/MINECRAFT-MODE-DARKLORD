package dev.zedkodex.darklord.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.FxPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class AbilityFx {
    private AbilityFx() {}

    private static final class Fx {
        final FxPayload data;
        final long start;
        boolean fired;

        Fx(FxPayload data, long start) {
            this.data = data;
            this.start = start;
        }

        FxPayload data() {
            return data;
        }

        long start() {
            return start;
        }
    }

    private static final List<Fx> list = new ArrayList<>();
    private static final MultiBufferSource.BufferSource DARK = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 20));
    private static final MultiBufferSource.BufferSource GLOW = MultiBufferSource.immediate(new com.mojang.blaze3d.vertex.ByteBufferBuilder(1 << 20));

    public static void add(FxPayload p) {
        list.add(new Fx(p, Util.getMillis()));
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        switch (p.kind()) {
            case FxPayload.HANDS -> {
                FxKit.burst(lv, at, p.kind());
                FxKit.shake(0.5f * FxKit.falloff(at, 30), 500);
            }
            case FxPayload.ABYSS -> {
                FxKit.burst(lv, at, p.kind());
                FxKit.shake(1.0f * FxKit.falloff(at, 40), 1600);
            }
            case FxPayload.CLEAVE -> {
                FxKit.burst(lv, at, p.kind());
                FxKit.shake(0.7f * FxKit.falloff(at, 20), 350);
            }
            case FxPayload.DASH -> {
                FxKit.burst(lv, at, p.kind());
                FxKit.burst(lv, new Vec3(p.x2(), p.y2(), p.z2()), p.kind());
                if (mc.player != null && p.caster() == mc.player.getId()) FxKit.flash(0x08000E, 0.6f);
            }
            case FxPayload.ERASE -> {
                FxKit.burst(lv, at, p.kind());
                EraseShadows.add(lv.getEntity(p.target()));
            }
            case FxPayload.HEARTBEAT -> Heartbeat.start();
            case FxPayload.LIGHTNING -> {
                FxKit.shake(0.6f * FxKit.falloff(at, 40), 500);
                FxKit.flash(0xE8DDFF, 0.35f * FxKit.falloff(at, 60));
                if (mc.player != null && p.caster() == mc.player.getId()) GripClient.castMs = Util.getMillis();
            }
            case FxPayload.CATACLYSM -> FxKit.cataclysm();
            default -> {
            }
        }
        if (mc.player != null && p.caster() == mc.player.getId() && p.kind() != FxPayload.CATACLYSM && p.kind() != FxPayload.HEARTBEAT
                && p.kind() != FxPayload.CAST) {
            FxKit.flash(0x06000C, 0.22f);
        }
    }

    private static long duration(int kind) {
        return switch (kind) {
            case FxPayload.HANDS -> 1300;
            case FxPayload.ABYSS -> 2400;
            case FxPayload.CLEAVE -> 760;
            case FxPayload.CAST -> 500;
            case FxPayload.DASH -> 900;
            case FxPayload.ERASE -> 1100;
            case FxPayload.DEVOUR -> 900;
            case FxPayload.SINGULARITY -> 4400;
            case FxPayload.LIGHTNING -> 1600;
            case FxPayload.CATACLYSM -> 8200;
            default -> 520;
        };
    }

    private static Vec3 handOf(AbstractClientPlayer p, float pt) {
        Vec3 look = p.getViewVector(pt);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        boolean self = p == Minecraft.getInstance().player && Minecraft.getInstance().options.getCameraType().isFirstPerson();
        return p.getEyePosition(pt).add(look.scale(self ? 1.1 : 0.7)).add(right.scale(0.36)).add(0, -0.3, 0);
    }

    @SubscribeEvent
    static void onArmPose(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p) || !(event.getRenderer().getModel() instanceof net.minecraft.client.model.PlayerModel<?> model)) return;
        long now = Util.getMillis();
        for (Fx f : list) {
            if (f.data.kind() == FxPayload.LIGHTNING && f.data.caster() == p.getId() && now - f.start < 1400) {
                model.rightArmPose = net.minecraft.client.model.HumanoidModel.ArmPose.SPYGLASS;
                return;
            }
        }
    }

    private static void extra(MultiBufferSource.BufferSource bs, ClientLevel lv, Fx f, float pt, long now) {
        FxPayload d = f.data;
        float t = (now - f.start) / (float) duration(d.kind());
        float ts = (now - f.start) / 1000f;
        Vec3 at = new Vec3(d.x(), d.y(), d.z());
        Vec3 at2 = new Vec3(d.x2(), d.y2(), d.z2());
        Entity target = lv.getEntity(d.target());
        AbstractClientPlayer caster = lv.getEntity(d.caster()) instanceof AbstractClientPlayer cp ? cp : null;
        VertexConsumer dark = DARK.getBuffer(RenderType.debugQuads());
        VertexConsumer glow = GLOW.getBuffer(RenderType.lightning());
        Vec3 upv = new Vec3(0, 1, 0);
        Vec3 hand = caster != null ? handOf(caster, pt) : null;
        switch (d.kind()) {
            case FxPayload.CAST -> {
            }
            case FxPayload.HANDS -> {
                float k = 1f - Gfx.clamp(t / 0.6f);
                FxKit.ring(dark, at.add(0, 0.04, 0), upv, 0.1f, 0.4f + 2.2f * Gfx.smooth(Gfx.clamp(t / 0.35f)), 4, 0, 8, (int) (220 * k), 0);
                FxKit.ring(glow, at.add(0, 0.06, 0), upv, 0.9f + 2.4f * t, 1.1f + 2.6f * t, 120, 40, 255, (int) (160 * k), 0);
                if (hand != null && t < 0.5f) GripFx.beam(bs, FxKit.cam, hand, at.add(0, 0.1, 0), Gfx.clamp(t / 0.12f), ts, 0x5A1EFF, 0xC8A0FF, 1f - t / 0.5f);
            }
            case FxPayload.ABYSS -> {
                float k = 1f - Gfx.smooth(Gfx.clamp((t - 0.75f) / 0.25f));
                Vec3 grab = target != null ? target.getPosition(pt).add(0, target.getBbHeight() * 0.6, 0) : at.add(0, 1.2, 0);
                if (hand != null && t < 0.8f) GripFx.beam(bs, FxKit.cam, hand, grab, Gfx.clamp(t / 0.1f), ts, 0x5A1EFF, 0xC8A0FF, k);
                FxKit.blob(glow, at.add(0, 1.2, 0), 2.4f, 90, 20, 200, (int) (60 * k));
                FxKit.ring(glow, at.add(0, 0.08, 0), upv, 2.2f, 3.0f, 140, 50, 255, (int) (120 * k), 0);
            }
            case FxPayload.CLEAVE -> {
                if (t > 0.08f) cleave(bs, FxKit.cam, at, d.yaw() - 6f, t - 0.08f);
                if (t > 0.16f) cleave(bs, FxKit.cam, at, d.yaw() + 6f, t - 0.16f);
            }
            case FxPayload.DASH -> {
                float k = 1f - Gfx.smooth(t);
                GripFx.beam(bs, FxKit.cam, at, at2, 1f, ts, 0x5A1EFF, 0xC8A0FF, k);
                FxKit.ring(glow, at.add(0, -0.9, 0), upv, 0.2f + 2.6f * t, 0.5f + 3f * t, 150, 60, 255, (int) (200 * k), 0);
                FxKit.ring(glow, at2.add(0, -0.9, 0), upv, 0.2f + 2.6f * t, 0.5f + 3f * t, 150, 60, 255, (int) (200 * k), 0);
            }
            case FxPayload.ERASE -> {
                Vec3 base = target != null ? target.getPosition(pt) : at;
                float h = target != null ? target.getBbHeight() : 1.8f;
                float w = target != null ? target.getBbWidth() : 0.6f;
                Vec3 c = base.add(0, h / 2, 0);
                if (t < 0.55f) {
                    float k = Gfx.clamp(t / 0.15f);
                    if (hand != null) GripFx.beam(bs, FxKit.cam, hand, c, Gfx.clamp(t / 0.1f), ts, 0x5A1EFF, 0xC8A0FF, 1f);
                    for (int i = 0; i < 3; i++) {
                        Vec3 tip = base.add((Math.sin(i * 7 + ts * 9)) * w * 0.5, h * (0.4 + 0.5 * Math.abs(Math.sin(i * 3 + ts * 5))), Math.cos(i * 5 + ts * 7) * w * 0.5);
                        FxKit.bolt(dark, base, tip, now / 60 + i * 31L, 0.035f, 5, 0, 10, (int) (230 * k), 0.35f);
                        FxKit.bolt(glow, base, tip, now / 60 + i * 31L, 0.012f, 160, 80, 255, (int) (160 * k), 0.35f);
                    }
                    FxKit.ring(glow, base.add(0, 0.05, 0), upv, w * 1.4f * (1 - t), w * 1.7f * (1 - t) + 0.2f, 140, 50, 255, (int) (200 * k), 0);
                } else {
                    float u = (t - 0.55f) / 0.45f;
                    FxKit.ring(dark, base.add(0, 0.04, 0), upv, 0f, w * 1.6f + 0.6f * u, 2, 0, 5, (int) (200 * (1 - u)), 0);
                    FxKit.blob(glow, c, 0.5f + 2.5f * u, 120, 60, 220, (int) (120 * (1 - u)));
                    if (!f.fired) {
                        f.fired = true;
                        FxKit.shake(0.4f * FxKit.falloff(c, 30), 300);
                    }
                }
            }
            case FxPayload.DEVOUR -> {
                Vec3 a = target != null ? target.getPosition(pt).add(0, target.getBbHeight() / 2, 0) : at;
                Vec3 b = caster != null ? caster.getPosition(pt).add(0, 1, 0) : at2;
                float k = 1f - Gfx.smooth(Gfx.clamp((t - 0.6f) / 0.4f));
                GripFx.beam(bs, FxKit.cam, a, b, 1f, ts, 0xDC1428, 0xFF8C96, k);
                FxKit.blob(glow, b, 0.9f, 200, 10, 30, (int) (90 * k));
            }
            case FxPayload.SINGULARITY -> {
                float appear = Gfx.smooth(Gfx.clamp(t / 0.08f));
                if (hand != null && t < 0.2f) GripFx.beam(bs, FxKit.cam, hand, at, Gfx.clamp(t / 0.05f), ts, 0x5A1EFF, 0xC8A0FF, 1f - t / 0.2f);
                if (t < 0.9f) {
                    float r = (1.0f + 0.12f * (float) Math.sin(ts * 9)) * appear;
                    FxKit.blob(glow, at, 4.5f * appear, 90, 20, 200, 70);
                    FxKit.sphere(dark, at, r, 1, 0, 3, 250);
                    Vec3 n = new Vec3(0.35, 1, 0.2);
                    FxKit.ring(glow, at, n, 1.3f * appear, 3.4f * appear, 170, 70, 255, 210, 0);
                    FxKit.ring(glow, at, n, 1.1f * appear, 1.4f * appear, 255, 220, 255, 0, 230);
                    Vec3 nn = n.normalize();
                    Vec3 u = nn.cross(new Vec3(1, 0, 0)).normalize();
                    Vec3 v = nn.cross(u).normalize();
                    for (int i = 0; i < 36; i++) {
                        double rr = 1.5 + (i % 6) * 0.3;
                        double a = ts * (5 - (i % 6) * 0.5) + i * 1.7;
                        Vec3 q = at.add(u.scale(Math.cos(a) * rr)).add(v.scale(Math.sin(a) * rr));
                        FxKit.blob(glow, q, 0.12f, 230, 180, 255, (int) (200 * appear));
                    }
                } else {
                    float u = (t - 0.9f) / 0.1f;
                    FxKit.sphere(glow, at, 8f * Gfx.smooth(u), 220, 190, 255, (int) (200 * (1 - u)));
                    FxKit.blob(glow, at, 10f * u, 255, 255, 255, (int) (220 * (1 - u)));
                    if (!f.fired) {
                        f.fired = true;
                        FxKit.shake(1.6f * FxKit.falloff(at, 60), 900);
                        FxKit.flash(0xEADCFF, 0.5f * FxKit.falloff(at, 60));
                    }
                }
            }
            case FxPayload.LIGHTNING -> {
                Vec3 from = hand != null ? hand : at.add(0, 1.4, 0);
                Vec3 aim = target != null ? target.getPosition(pt).add(0, target.getBbHeight() * 0.55, 0) : at2;
                if (t < 0.44f) {
                    float k = Gfx.clamp(t / 0.1f);
                    FxKit.blob(glow, from, 0.3f + 0.15f * (float) Math.sin(ts * 40), 190, 150, 255, (int) (230 * k));
                    for (int i = 0; i < 5; i++) {
                        long s = now / 40 + i * 17L;
                        java.util.Random r = new java.util.Random(s);
                        Vec3 tip = from.add((r.nextDouble() - 0.5) * 1.4, (r.nextDouble() - 0.5) * 1.4, (r.nextDouble() - 0.5) * 1.4);
                        FxKit.bolt(glow, from, tip, s, 0.025f, 210, 190, 255, (int) (220 * k), 0.45f);
                    }
                } else if (t < 0.88f) {
                    float k = 1f - Gfx.clamp((t - 0.7f) / 0.18f);
                    for (int i = 0; i < 3; i++) {
                        long s = now / 45 + i * 101L;
                        FxKit.bolt(glow, from, aim, s, 0.32f, 120, 70, 255, (int) (90 * k), 0.22f);
                        FxKit.bolt(glow, from, aim, s, 0.08f, 255, 255, 255, (int) (240 * k), 0.22f);
                    }
                    FxKit.blob(glow, aim, 1.8f, 210, 190, 255, (int) (200 * k));
                    FxKit.blob(glow, from, 0.6f, 230, 220, 255, (int) (220 * k));
                    if (!f.fired) {
                        f.fired = true;
                        FxKit.shake(0.9f * FxKit.falloff(aim, 40), 600);
                        FxKit.flash(0xE8DDFF, 0.45f * FxKit.falloff(aim, 60));
                        FxKit.burst(lv, aim.subtract(0, 1, 0), FxPayload.LIGHTNING);
                    }
                }
            }
            case FxPayload.CATACLYSM -> cataclysm(bs, dark, glow, lv, f, at, at2, ts, hand);
            default -> {
            }
        }
    }

    private static void cataclysm(MultiBufferSource bs, VertexConsumer dark, VertexConsumer glow, ClientLevel lv, Fx f, Vec3 c, Vec3 eye, float ts, Vec3 hand) {
        Vec3 n = eye.subtract(c);
        Vec3 upv = new Vec3(0, 1, 0);
        if (ts < 2.0f && hand != null) {
            float k = 1f - Gfx.smooth(Gfx.clamp((ts - 1.6f) / 0.4f));
            GripFx.beam(bs, FxKit.cam, hand, c, Gfx.clamp(ts / 0.5f), ts, 0xB4C8FF, 0xFFFFFF, k);
        }
        if (ts >= 1.0f && ts < 2.0f) {
            float k = Gfx.smooth((ts - 1.0f) / 1.0f);
            float r = 3.5f * k;
            FxKit.ring(glow, c, n, r * 0.82f, r, 255, 255, 255, (int) (230 * k), (int) (60 * k));
            FxKit.ring(glow, c, n, r * 1.05f, r * 1.25f, 210, 220, 255, (int) (90 * k), 0);
            FxKit.blob(glow, c, 1.6f * k, 255, 255, 255, (int) (160 * k));
        } else if (ts >= 2.0f && ts < 2.7f) {
            float u = (ts - 2.0f) / 0.7f;
            float e = u * u * u;
            float r = 3.5f + 70f * e;
            FxKit.ring(glow, c, n, r * 0.9f, r, 255, 255, 255, (int) (255 * (1 - u * 0.4f)), 0);
            FxKit.blob(glow, c, 2f + 8f * e, 255, 255, 255, 230);
            FxKit.flash(0xFFFFFF, 0.65f * u * Math.max(0.3f, FxKit.falloff(c, 260)));
        }
        if (ts >= 2.7f) {
            if (!f.fired) {
                f.fired = true;
                float near = Math.max(0.35f, FxKit.falloff(c, 260));
                FxKit.flash(0xFFFFFF, 0.95f * near);
                FxKit.shake(3.2f * Math.max(0.2f, FxKit.falloff(c, 200)), 2400);
                FxKit.burst(lv, c, FxPayload.CATACLYSM);
            }
            float u = Gfx.clamp((ts - 2.7f) / 1.4f);
            float e = 1f - (1f - u) * (1f - u) * (1f - u);
            float radius = 36f * e;
            float fade = 1f - Gfx.smooth(Gfx.clamp((ts - 3.1f) / 2.2f));
            if (fade > 0.01f) {
                FxKit.sphere(glow, c, radius, 255, 255, 255, (int) (70 * fade));
                FxKit.sphere(glow, c, radius * 0.8f, 235, 240, 255, (int) (90 * fade));
                FxKit.sphere(glow, c, radius * 0.45f, 255, 255, 255, (int) (200 * fade));
                FxKit.blob(glow, c, radius * 1.2f, 255, 255, 255, (int) (160 * fade));
                float w = Gfx.clamp((ts - 2.7f) / 1.8f);
                float gr = 80f * (1f - (1f - w) * (1f - w));
                FxKit.ring(glow, c, upv, gr * 0.93f, gr, 255, 255, 255, (int) (200 * (1 - w)), 0);
                FxKit.ring(glow, c, upv, gr * 0.6f, gr * 0.66f, 220, 230, 255, (int) (140 * (1 - w)), 0);
            }
        }
    }

    private static float envelope(float t, float in, float out) {
        return Gfx.smooth(Gfx.clamp(t / in)) * (1f - Gfx.smooth(Gfx.clamp((t - (1f - out)) / out)));
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || list.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null || mc.player == null) {
            list.clear();
            return;
        }
        long now = Util.getMillis();
        list.removeIf(f -> now - f.start() > duration(f.data().kind()));
        if (list.isEmpty()) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        GripFx.view = new Matrix4f(event.getPoseStack().last().pose());
        FxKit.setup(event.getCamera());
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        List<ResourceLocation> skins = new ArrayList<>();
        for (Fx f : list) {
            FxPayload d = f.data();
            float t = (now - f.start()) / (float) duration(d.kind());
            float time = (now - f.start()) / 1000f;
            Vec3 at = new Vec3(d.x(), d.y(), d.z());
            Entity target = lv.getEntity(d.target());
            AbstractClientPlayer caster = lv.getEntity(d.caster()) instanceof AbstractClientPlayer cp ? cp : mc.player;
            switch (d.kind()) {
                case FxPayload.HANDS -> {
                    float open = envelope(t, 0.12f, 0.25f);
                    GripFx.portal(bs, cam, at.add(0, 0.03, 0), new Vec3(0, 1, 0), 0.95f * open, time);
                    Vec3 aimAt = target != null ? target.getPosition(pt).add(0, target.getBbHeight() * 0.5, 0) : at.add(0, 1.6, 0);
                    float rise = Gfx.smooth(Gfx.clamp((t - 0.06f) / 0.22f)) * (1f - Gfx.smooth(Gfx.clamp((t - 0.72f) / 0.28f)));
                    for (int k = 0; k < 2; k++) {
                        double a = k * Math.PI + d.target();
                        Vec3 root = at.add(Math.cos(a) * 0.3, 0, Math.sin(a) * 0.3);
                        arm(pose, bs, caster, cam, root, aimAt, rise, time, k, 1.6f);
                    }
                    skins.add(caster.getSkin().texture());
                }
                case FxPayload.ABYSS -> {
                    float open = envelope(t, 0.1f, 0.15f);
                    GripFx.portal(bs, cam, at.add(0, 0.03, 0), new Vec3(0, 1, 0), (1.5f + (target != null ? target.getBbWidth() * 0.5f : 0f)) * open, time);
                    GripFx.portal(bs, cam, at.add(0, 0.05, 0), new Vec3(0, 1, 0), 0.6f * open, -time * 1.7f);
                    Vec3 aimAt = target != null ? target.getPosition(pt).add(0, target.getBbHeight() * 0.6, 0) : at.add(0, 1.2, 0);
                    float rise = Gfx.smooth(Gfx.clamp((t - 0.04f) / 0.15f)) * (1f - Gfx.smooth(Gfx.clamp((t - 0.82f) / 0.18f)));
                    for (int k = 0; k < 5; k++) {
                        double a = k * Math.PI * 2 / 5 + d.target();
                        Vec3 root = at.add(Math.cos(a) * 0.9, -0.2, Math.sin(a) * 0.9);
                        arm(pose, bs, caster, cam, root, aimAt, rise, time, k, 1.4f);
                    }
                    skins.add(caster.getSkin().texture());
                }
                case FxPayload.CLEAVE -> cleave(bs, cam, at, d.yaw(), t);
                default -> {
                }
            }
            extra(bs, lv, f, pt, now);
            DARK.endBatch();
            GLOW.endBatch();
        }
        bs.endBatch(RenderType.debugQuads());
        bs.endBatch(RenderType.lightning());
        for (ResourceLocation skin : skins) bs.endBatch(RenderType.entityTranslucent(skin));
    }

    private static void arm(PoseStack pose, MultiBufferSource.BufferSource bs, AbstractClientPlayer p, Vec3 cam, Vec3 root, Vec3 target,
                            float emerge, float time, int index, float maxScale) {
        if (emerge <= 0.01f) return;
        PlayerRenderer renderer = (PlayerRenderer) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(p);
        ModelPart arm = renderer.getModel().rightArm;
        Vec3 dir = target.subtract(root);
        double dist = dir.length();
        dir = dir.normalize();
        float s = (float) Mth.clamp(dist / 0.75, 0.8, maxScale * 2.2);
        double len = 0.75 * s;
        Vec3 base = root.subtract(dir.scale(len * (1 - emerge))).subtract(cam);
        pose.pushPose();
        pose.translate(base.x, base.y, base.z);
        pose.mulPose(new Quaternionf().rotationTo(0f, -1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z));
        pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(time * 14 + index) * 4f));
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.cos(time * 12 + index * 2) * 4f));
        pose.scale(-s * 0.9f, -s, s * 0.9f);
        float x = arm.x, y = arm.y, z = arm.z, xr = arm.xRot, yr = arm.yRot, zr = arm.zRot;
        boolean vis = arm.visible;
        arm.x = 0;
        arm.y = 0;
        arm.z = 0;
        arm.xRot = 0;
        arm.yRot = 0;
        arm.zRot = 0;
        arm.visible = true;
        int alpha = (int) (240 * Gfx.clamp(emerge * 1.4f));
        arm.render(pose, bs.getBuffer(RenderType.entityTranslucent(p.getSkin().texture())), 0xF000F0, OverlayTexture.NO_OVERLAY, (alpha << 24) | 0x0B0812);
        arm.x = x;
        arm.y = y;
        arm.z = z;
        arm.xRot = xr;
        arm.yRot = yr;
        arm.zRot = zr;
        arm.visible = vis;
        pose.popPose();
    }

    private static void cleave(MultiBufferSource.BufferSource bs, Vec3 cam, Vec3 at, float yawDeg, float t) {
        float yaw = yawDeg * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-fwd.z, 0, fwd.x);
        Vec3 tilt = new Vec3(0, 1, 0).scale(0.35);
        float sweep = Gfx.smooth(Gfx.clamp(t / 0.45f));
        float fade = 1f - Gfx.smooth(Gfx.clamp((t - 0.45f) / 0.55f));
        int n = 40;
        double start = -1.3;
        double end = start + 2.6 * sweep;
        Matrix4f m = GripFx.view;
        VertexConsumer dark = bs.getBuffer(RenderType.debugQuads());
        VertexConsumer glow = bs.getBuffer(RenderType.lightning());
        for (int i = 0; i < n; i++) {
            double a0 = start + (end - start) * i / n;
            double a1 = start + (end - start) * (i + 1) / n;
            float w0 = (float) Math.sin(Math.PI * i / n);
            float w1 = (float) Math.sin(Math.PI * (i + 1) / n);
            for (int layer = 0; layer < 2; layer++) {
                double r = 3.2;
                double thick = layer == 0 ? 0.55 : 0.18;
                Vec3 o0 = at.add(fwd.scale(Math.cos(a0) * r - r * 0.6)).add(right.scale(Math.sin(a0) * r)).add(tilt.scale(Math.sin(a0)));
                Vec3 o1 = at.add(fwd.scale(Math.cos(a1) * r - r * 0.6)).add(right.scale(Math.sin(a1) * r)).add(tilt.scale(Math.sin(a1)));
                Vec3 i0 = at.add(fwd.scale(Math.cos(a0) * (r - thick * w0) - r * 0.6)).add(right.scale(Math.sin(a0) * (r - thick * w0))).add(tilt.scale(Math.sin(a0)));
                Vec3 i1 = at.add(fwd.scale(Math.cos(a1) * (r - thick * w1) - r * 0.6)).add(right.scale(Math.sin(a1) * (r - thick * w1))).add(tilt.scale(Math.sin(a1)));
                VertexConsumer vc = layer == 0 ? dark : glow;
                int al = (int) (255 * fade * (layer == 0 ? 0.92f : 0.85f));
                int r0 = layer == 0 ? 4 : 170;
                int g0 = layer == 0 ? 0 : 60;
                int b0 = layer == 0 ? 10 : 255;
                put(vc, m, o0.subtract(cam), r0, g0, b0, al);
                put(vc, m, o1.subtract(cam), r0, g0, b0, al);
                put(vc, m, i1.subtract(cam), r0, g0, b0, 0);
                put(vc, m, i0.subtract(cam), r0, g0, b0, 0);
            }
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, Vec3 v, int r, int g, int b, int a) {
        vc.addVertex(m, (float) v.x, (float) v.y, (float) v.z).setColor(r, g, b, a);
    }
}
