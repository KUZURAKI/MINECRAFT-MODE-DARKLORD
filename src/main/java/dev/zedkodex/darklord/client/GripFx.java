package dev.zedkodex.darklord.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.zedkodex.darklord.DarkLord;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class GripFx {
    private GripFx() {}

    private static final int SEGMENTS = 48;
    private static final int FULL_BRIGHT = 0xF000F0;

    static Matrix4f view = new Matrix4f();

    private static Vec3 hand(LocalPlayer p, float pt, boolean firstPerson) {
        Vec3 eye = p.getEyePosition(pt);
        Vec3 look = p.getViewVector(pt);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        return eye.add(look.scale(firstPerson ? 1.4 : 0.55)).add(right.scale(0.38)).add(0, -0.32, 0);
    }

    static void particles(ClientLevel lv, LocalPlayer p, Entity e) {
        var r = lv.random;
        Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
        double rad = Math.max(0.6, e.getBbWidth() * 0.9);
        double time = (Util.getMillis() - GripClient.gripMs) / 1000.0;
        for (int i = 0; i < 5; i++) {
            double a = time * 4 + i * Math.PI * 2 / 5 + r.nextDouble() * 0.4;
            double y = (r.nextDouble() - 0.5) * e.getBbHeight();
            Vec3 q = c.add(Math.cos(a) * rad, y, Math.sin(a) * rad);
            Vec3 v = c.subtract(q).scale(0.06);
            lv.addParticle(ParticleTypes.SOUL_FIRE_FLAME, q.x, q.y, q.z, v.x, v.y + 0.01, v.z);
        }
        if (r.nextInt(3) == 0) lv.addParticle(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 0, 0.04, 0);
        if (GripClient.variant == 0) {
            Vec3 h = hand(p, 1f, Minecraft.getInstance().options.getCameraType().isFirstPerson());
            for (int i = 0; i < 3; i++) {
                double t = 0.35 + r.nextDouble() * 0.65;
                Vec3 q = curve(h, c, t, 0.0);
                lv.addParticle(r.nextInt(3) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SQUID_INK, q.x, q.y, q.z,
                        (r.nextDouble() - 0.5) * 0.02, 0.01, (r.nextDouble() - 0.5) * 0.02);
            }
        } else {
            for (Vec3 portal : portals(c, e.getBbHeight(), e.getBbWidth())) {
                if (r.nextInt(2) == 0) {
                    lv.addParticle(ParticleTypes.REVERSE_PORTAL, portal.x + (r.nextDouble() - 0.5) * 0.8, portal.y + (r.nextDouble() - 0.5) * 0.8,
                            portal.z + (r.nextDouble() - 0.5) * 0.8, 0, 0, 0);
                }
                if (r.nextInt(4) == 0) lv.addParticle(ParticleTypes.SQUID_INK, portal.x, portal.y, portal.z, 0, 0.02, 0);
            }
        }
    }

    private static Vec3 curve(Vec3 a, Vec3 b, double t, double lift) {
        Vec3 c = a.add(b).scale(0.5).add(0, 0.5 + lift, 0);
        double u = 1 - t;
        return a.scale(u * u).add(c.scale(2 * u * t)).add(b.scale(t * t));
    }

    private static Vec3[] portals(Vec3 c, float h, float w) {
        int seed = GripClient.seed;
        if (GripClient.variant == 2) {
            return new Vec3[]{c.add(0, -h / 2 - 0.9, 0)};
        }
        int n = 3 + seed % 2;
        Vec3[] out = new Vec3[n];
        double base = (seed % 360) * Math.PI / 180;
        double rad = 1.7 + w * 0.6;
        for (int i = 0; i < n; i++) {
            double a = base + i * Math.PI * 2 / n;
            double y = ((Gfx.hash(seed + i * 31) % 100) / 100.0 - 0.3) * (h + 0.8);
            out[i] = c.add(Math.cos(a) * rad, y, Math.sin(a) * rad);
        }
        return out;
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel lv = mc.level;
        if (p == null || lv == null) return;
        long now = Util.getMillis();
        boolean active = GripClient.gripping;
        long endMs = Math.max(GripClient.crushMs, GripClient.releaseMs);
        float fade = 1f - Gfx.clamp((now - endMs) / 600f);
        if (!active && fade <= 0f) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 target = GripClient.lastTarget;
        float h = GripClient.lastHeight;
        float w = GripClient.lastWidth;
        if (active) {
            Entity e = lv.getEntity(GripClient.gripId);
            if (e != null) target = e.getPosition(pt).add(0, e.getBbHeight() / 2, 0);
        }
        float open = active ? Gfx.smooth(Gfx.clamp((now - GripClient.gripMs) / 450f)) : fade;
        boolean crushing = !active && GripClient.crushMs >= GripClient.releaseMs;
        Vec3 cam = event.getCamera().getPosition();
        view = new Matrix4f(event.getPoseStack().last().pose());
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        float time = (now - GripClient.gripMs) / 1000f;

        if (GripClient.variant == 0) {
            beam(bs, cam, hand(p, pt, mc.options.getCameraType().isFirstPerson()), target, open, time);
        } else {
            Vec3[] ps = portals(target, h, w);
            PoseStack pose = event.getPoseStack();
            for (int i = 0; i < ps.length; i++) {
                Vec3 normal = GripClient.variant == 2 ? new Vec3(0, 1, 0) : target.subtract(ps[i]).normalize();
                float size = (GripClient.variant == 2 ? 1.1f + w * 0.6f : 0.75f) * open;
                portal(bs, cam, ps[i], normal, size, time + i);
            }
            bs.endBatch(RenderType.debugQuads());
            bs.endBatch(RenderType.lightning());
            for (int i = 0; i < ps.length; i++) {
                if (GripClient.variant == 2) {
                    for (int k = 0; k < 3; k++) {
                        double a = k * Math.PI * 2 / 3 + GripClient.seed;
                        Vec3 root = ps[i].add(Math.cos(a) * 0.45, 0, Math.sin(a) * 0.45);
                        arm(pose, bs, p, cam, root, target, open, crushing, now, k);
                    }
                } else {
                    arm(pose, bs, p, cam, ps[i], target, open, crushing, now, i);
                }
            }
        }
        bs.endBatch(RenderType.debugQuads());
        bs.endBatch(RenderType.lightning());
        bs.endBatch(RenderType.entityTranslucent(p.getSkin().texture()));
    }

    private static void ribbon(VertexConsumer vc, Vec3 cam, Vec3[] pts, float[] widths, int r, int g, int b, float[] alpha) {
        Matrix4f m = view;
        Vec3 prevSide = null;
        for (int i = 0; i < pts.length - 1; i++) {
            Vec3 a = pts[i];
            Vec3 c = pts[i + 1];
            Vec3 tan = c.subtract(a);
            Vec3 side = tan.cross(cam.subtract(a)).normalize();
            if (prevSide == null) prevSide = side;
            Vec3 a0 = a.subtract(prevSide.scale(widths[i])).subtract(cam);
            Vec3 a1 = a.add(prevSide.scale(widths[i])).subtract(cam);
            Vec3 c1 = c.add(side.scale(widths[i + 1])).subtract(cam);
            Vec3 c0 = c.subtract(side.scale(widths[i + 1])).subtract(cam);
            int al0 = (int) (Gfx.clamp(alpha[i]) * 255);
            int al1 = (int) (Gfx.clamp(alpha[i + 1]) * 255);
            vc.addVertex(m, (float) a0.x, (float) a0.y, (float) a0.z).setColor(r, g, b, al0);
            vc.addVertex(m, (float) a1.x, (float) a1.y, (float) a1.z).setColor(r, g, b, al0);
            vc.addVertex(m, (float) c1.x, (float) c1.y, (float) c1.z).setColor(r, g, b, al1);
            vc.addVertex(m, (float) c0.x, (float) c0.y, (float) c0.z).setColor(r, g, b, al1);
            prevSide = side;
        }
    }

    private static void beam(MultiBufferSource.BufferSource bs, Vec3 cam, Vec3 start, Vec3 end, float open, float time) {
        Vec3 axis = end.subtract(start).normalize();
        Vec3 u = axis.cross(new Vec3(0, 1, 0));
        if (u.lengthSqr() < 1e-4) u = new Vec3(1, 0, 0);
        u = u.normalize();
        Vec3 v = axis.cross(u).normalize();
        VertexConsumer dark = bs.getBuffer(RenderType.debugQuads());
        for (int s = 0; s < 3; s++) {
            Vec3[] pts = new Vec3[SEGMENTS + 1];
            float[] wd = new float[SEGMENTS + 1];
            float[] al = new float[SEGMENTS + 1];
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = i / (double) SEGMENTS;
                double ang = t * 11 - time * 7 + s * Math.PI * 2 / 3;
                double rad = 0.16 * Math.sin(Math.PI * t) * (0.6 + 0.4 * t);
                pts[i] = curve(start, end, t, 0.0).add(u.scale(Math.cos(ang) * rad)).add(v.scale(Math.sin(ang) * rad));
                wd[i] = (float) (0.025 + 0.07 * t);
                float reach = Gfx.clamp(open * 1.3f - (float) t * 0.3f);
                al[i] = reach * Gfx.clamp((float) (t - 0.05) / 0.3f) * 0.9f;
            }
            ribbon(dark, cam, pts, wd, 6, 0, 14, al);
        }
        VertexConsumer glow = bs.getBuffer(RenderType.lightning());
        for (int layer = 0; layer < 2; layer++) {
            Vec3[] pts = new Vec3[SEGMENTS + 1];
            float[] wd = new float[SEGMENTS + 1];
            float[] al = new float[SEGMENTS + 1];
            for (int i = 0; i <= SEGMENTS; i++) {
                double t = i / (double) SEGMENTS;
                double flick = 0.75 + 0.25 * Math.sin(time * 23 + t * 17 + layer * 2);
                pts[i] = curve(start, end, t, 0.0).add(v.scale(Math.sin(t * 9 - time * 5) * 0.05));
                wd[i] = (float) ((layer == 0 ? 0.22 : 0.06) * (0.3 + 0.7 * t) * flick);
                float reach = Gfx.clamp(open * 1.3f - (float) t * 0.3f);
                al[i] = reach * Gfx.clamp((float) (t - 0.15) / 0.35f) * (layer == 0 ? 0.35f : 0.8f);
            }
            if (layer == 0) ribbon(glow, cam, pts, wd, 30, 90, 255, al);
            else ribbon(glow, cam, pts, wd, 150, 220, 255, al);
        }
    }

    static void portal(MultiBufferSource.BufferSource bs, Vec3 cam, Vec3 c, Vec3 normal, float size, float time) {
        if (size <= 0.01f) return;
        Vec3 a = normal.cross(Math.abs(normal.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 b = normal.cross(a).normalize();
        Matrix4f m = view;
        VertexConsumer dark = bs.getBuffer(RenderType.debugQuads());
        VertexConsumer glow = bs.getBuffer(RenderType.lightning());
        int n = 32;
        Vec3 o = c.subtract(cam);
        for (int i = 0; i < n; i++) {
            double t0 = i * Math.PI * 2 / n;
            double t1 = (i + 1) * Math.PI * 2 / n;
            double w0 = 1 + 0.08 * Math.sin(t0 * 5 + time * 6);
            double w1 = 1 + 0.08 * Math.sin(t1 * 5 + time * 6);
            Vec3 p0 = o.add(a.scale(Math.cos(t0) * size * w0)).add(b.scale(Math.sin(t0) * size * w0));
            Vec3 p1 = o.add(a.scale(Math.cos(t1) * size * w1)).add(b.scale(Math.sin(t1) * size * w1));
            quad(dark, m, o, o, p1, p0, 0, 0, 0, 235, 235);
            Vec3 q0 = o.add(a.scale(Math.cos(t0) * size * w0 * 1.25)).add(b.scale(Math.sin(t0) * size * w0 * 1.25));
            Vec3 q1 = o.add(a.scale(Math.cos(t1) * size * w1 * 1.25)).add(b.scale(Math.sin(t1) * size * w1 * 1.25));
            int bright = (int) (150 + 100 * Math.sin(t0 * 3 - time * 8));
            quad(glow, m, p0, p1, q1, q0, 110, 30, 220, bright, 0);
            Vec3 i0 = o.add(a.scale(Math.cos(t0) * size * w0 * 0.82)).add(b.scale(Math.sin(t0) * size * w0 * 0.82));
            Vec3 i1 = o.add(a.scale(Math.cos(t1) * size * w1 * 0.82)).add(b.scale(Math.sin(t1) * size * w1 * 0.82));
            quad(glow, m, i0, i1, p1, p0, 40, 80, 255, 0, 160);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int r, int g, int bl, int alphaInner, int alphaOuter) {
        vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(r, g, bl, alphaInner);
        vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(r, g, bl, alphaInner);
        vc.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(r, g, bl, alphaOuter);
        vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(r, g, bl, alphaOuter);
    }

    private static void arm(PoseStack pose, MultiBufferSource.BufferSource bs, AbstractClientPlayer p, Vec3 cam, Vec3 root, Vec3 target,
                            float open, boolean crushing, long now, int index) {
        Minecraft mc = Minecraft.getInstance();
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(p);
        ModelPart arm = renderer.getModel().rightArm;
        Vec3 dir = target.subtract(root);
        double dist = dir.length();
        dir = dir.normalize();
        float emerge = GripClient.gripping
                ? Gfx.smooth(Gfx.clamp((now - GripClient.gripMs - 200 - index * 110) / 550f))
                : open;
        if (emerge <= 0.01f) return;
        float s = (float) Math.max(0.8, Math.min(3.2, (dist - GripClient.lastWidth * 0.3) / 0.75));
        double len = 0.75 * s;
        float squeeze = crushing ? 0.85f : 1f;
        Vec3 base = root.subtract(dir.scale(len * (1 - emerge))).subtract(cam);
        float t = now / 1000f;
        pose.pushPose();
        pose.translate(base.x, base.y, base.z);
        pose.mulPose(new Quaternionf().rotationTo(0f, -1f, 0f, (float) dir.x, (float) dir.y, (float) dir.z));
        pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(t * 13 + index) * 3f));
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.cos(t * 11 + index * 2) * 3f));
        pose.scale(-s * squeeze, -s, s * squeeze);
        float x = arm.x, y = arm.y, z = arm.z, xr = arm.xRot, yr = arm.yRot, zr = arm.zRot;
        boolean vis = arm.visible;
        arm.x = 0;
        arm.y = 0;
        arm.z = 0;
        arm.xRot = 0;
        arm.yRot = 0;
        arm.zRot = 0;
        arm.visible = true;
        int alpha = (int) (235 * Gfx.clamp(emerge * 1.5f));
        int color = (alpha << 24) | 0x0E0A16;
        arm.render(pose, bs.getBuffer(RenderType.entityTranslucent(p.getSkin().texture())), FULL_BRIGHT, OverlayTexture.NO_OVERLAY, color);
        arm.x = x;
        arm.y = y;
        arm.z = z;
        arm.xRot = xr;
        arm.yRot = yr;
        arm.zRot = zr;
        arm.visible = vis;
        pose.popPose();
    }
}
