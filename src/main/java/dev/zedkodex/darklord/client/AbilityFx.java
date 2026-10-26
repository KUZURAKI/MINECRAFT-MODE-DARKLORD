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

    private record Fx(FxPayload data, long start) {}

    private static final List<Fx> list = new ArrayList<>();

    public static void add(FxPayload p) {
        list.add(new Fx(p, Util.getMillis()));
    }

    private static long duration(int kind) {
        return switch (kind) {
            case FxPayload.HANDS -> 1300;
            case FxPayload.ABYSS -> 2400;
            default -> 520;
        };
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
