package dev.zedkodex.darklord.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.FxPayload;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class FxKit {
    private FxKit() {}

    private static float shakeAmp;
    private static long shakeStart;
    private static long shakeMs = 1;
    private static float flash;
    private static int flashRgb;
    private static long lastFrame;
    private static float night;
    private static long baseTime = -1;
    private static long baseTick;
    private static long cataclysmStart = -1;

    static Vec3 cam = Vec3.ZERO;
    static Vector3f right = new Vector3f();
    static Vector3f up = new Vector3f();

    static void setup(Camera camera) {
        cam = camera.getPosition();
        right = new Vector3f(camera.getLeftVector()).mul(-1f);
        up = new Vector3f(camera.getUpVector());
    }

    static float falloff(Vec3 at, double range) {
        var p = Minecraft.getInstance().player;
        return p == null ? 0f : (float) Math.max(0, 1 - p.getEyePosition().distanceTo(at) / range);
    }

    static void shake(float amp, long ms) {
        long now = Util.getMillis();
        float left = shakeAmp * (1f - Gfx.clamp((now - shakeStart) / (float) shakeMs));
        if (amp < left) return;
        shakeAmp = amp;
        shakeStart = now;
        shakeMs = ms;
    }

    static void flash(int rgb, float a) {
        if (a > flash) {
            flash = a;
            flashRgb = rgb;
        }
    }

    static void cataclysm() {
        cataclysmStart = Util.getMillis();
    }

    static float nightLevel(long now) {
        if (cataclysmStart < 0) return 0f;
        float t = (now - cataclysmStart) / 1000f;
        if (t > 8f) {
            cataclysmStart = -1;
            return 0f;
        }
        float in = Gfx.smooth(Gfx.clamp(t / 1.0f));
        float out = 1f - Gfx.smooth(Gfx.clamp((t - 4.8f) / 3.0f));
        return in * out;
    }

    @SubscribeEvent
    static void onAngles(ViewportEvent.ComputeCameraAngles event) {
        long now = Util.getMillis();
        float k = shakeAmp * (1f - Gfx.clamp((now - shakeStart) / (float) shakeMs));
        if (k <= 0.001f) return;
        float t = now / 1000f;
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 61) + Math.sin(t * 37)) * k);
        event.setPitch(event.getPitch() + (float) (Math.cos(t * 53) + Math.sin(t * 29)) * k);
        event.setRoll(event.getRoll() + (float) Math.sin(t * 47) * k * 1.5f);
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.ComputeFogColor event) {
        float n = night;
        if (n > 0.01f) {
            float d = 1f - 0.75f * n;
            event.setRed(event.getRed() * d);
            event.setGreen(event.getGreen() * d);
            event.setBlue(event.getBlue() * d);
        }
        if (flash > 0.01f && flashRgb == 0xFFFFFF) {
            event.setRed(Mth.lerp(flash, event.getRed(), 1f));
            event.setGreen(Mth.lerp(flash, event.getGreen(), 1f));
            event.setBlue(Mth.lerp(flash, event.getBlue(), 1f));
        }
    }

    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Pre event) {
        applyNight();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        applyNight();
    }

    private static void applyNight() {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) {
            baseTime = -1;
            cataclysmStart = -1;
            return;
        }
        night = nightLevel(Util.getMillis());
        if (night <= 0f) {
            if (baseTime >= 0) {
                lv.setDayTime(real(lv));
                baseTime = -1;
            }
            return;
        }
        if (baseTime < 0) {
            baseTime = lv.getDayTime();
            baseTick = lv.getGameTime();
        }
        long real = real(lv);
        long into = Math.floorMod(real, 24000L);
        long target = real + Math.floorMod(18000L - into, 24000L);
        lv.setDayTime(real + (long) ((target - real) * night));
    }

    private static long real(ClientLevel lv) {
        boolean cycle = lv.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        return baseTime + (cycle ? lv.getGameTime() - baseTick : 0);
    }

    public static void overlay(GuiGraphics g, DeltaTracker delta) {
        long now = Util.getMillis();
        float dt = lastFrame == 0 ? 16 : Math.min(100, now - lastFrame);
        lastFrame = now;
        flash *= (float) Math.exp(-dt / (flashRgb == 0xFFFFFF ? 420f : 160f));
        int w = g.guiWidth();
        int h = g.guiHeight();
        if (night > 0.01f) {
            int n = 20;
            int wb = (int) (w * 0.25f);
            for (int i = 0; i < n; i++) {
                float f = 1f - i / (float) n;
                int col = Gfx.rgba(0x000000, 0.55f * night * f * f);
                g.fill(i * wb / n, 0, (i + 1) * wb / n, h, col);
                g.fill(w - (i + 1) * wb / n, 0, w - i * wb / n, h, col);
            }
        }
        if (flash > 0.01f) g.fill(0, 0, w, h, Gfx.rgba(flashRgb, Math.min(0.95f, flash)));
    }

    static void blob(VertexConsumer vc, Vec3 c, float size, int r, int g, int b, int a) {
        if (a <= 0 || size <= 0.001f) return;
        Matrix4f m = GripFx.view;
        Vec3 o = c.subtract(cam);
        float cx = (float) o.x, cy = (float) o.y, cz = (float) o.z;
        int seg = 12;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg;
            double a1 = (i + 1) * Math.PI * 2 / seg;
            float x0 = (float) Math.cos(a0) * size, y0 = (float) Math.sin(a0) * size;
            float x1 = (float) Math.cos(a1) * size, y1 = (float) Math.sin(a1) * size;
            vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
            vc.addVertex(m, cx, cy, cz).setColor(r, g, b, a);
            vc.addVertex(m, cx + right.x * x1 + up.x * y1, cy + right.y * x1 + up.y * y1, cz + right.z * x1 + up.z * y1).setColor(r, g, b, 0);
            vc.addVertex(m, cx + right.x * x0 + up.x * y0, cy + right.y * x0 + up.y * y0, cz + right.z * x0 + up.z * y0).setColor(r, g, b, 0);
        }
    }

    static void sphere(VertexConsumer vc, Vec3 c, float radius, int r, int g, int b, int a) {
        if (a <= 0 || radius <= 0.01f) return;
        Matrix4f m = GripFx.view;
        Vec3 o = c.subtract(cam);
        int lat = 14;
        int lon = 24;
        for (int i = 0; i < lat; i++) {
            double t0 = Math.PI * i / lat - Math.PI / 2;
            double t1 = Math.PI * (i + 1) / lat - Math.PI / 2;
            for (int j = 0; j < lon; j++) {
                double p0 = Math.PI * 2 * j / lon;
                double p1 = Math.PI * 2 * (j + 1) / lon;
                put(vc, m, o, radius, t0, p0, r, g, b, a);
                put(vc, m, o, radius, t1, p0, r, g, b, a);
                put(vc, m, o, radius, t1, p1, r, g, b, a);
                put(vc, m, o, radius, t0, p1, r, g, b, a);
            }
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, Vec3 o, float radius, double lat, double lon, int r, int g, int b, int a) {
        double cl = Math.cos(lat);
        vc.addVertex(m, (float) (o.x + Math.cos(lon) * cl * radius), (float) (o.y + Math.sin(lat) * radius), (float) (o.z + Math.sin(lon) * cl * radius))
                .setColor(r, g, b, a);
    }

    static void ring(VertexConsumer vc, Vec3 c, Vec3 normal, float r0, float r1, int r, int g, int b, int aIn, int aOut) {
        if (r1 <= 0.01f || (aIn <= 0 && aOut <= 0)) return;
        Vec3 n = normal.normalize();
        Vec3 u = n.cross(Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
        Vec3 v = n.cross(u).normalize();
        Matrix4f m = GripFx.view;
        Vec3 o = c.subtract(cam);
        int seg = 48;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg;
            double a1 = Math.PI * 2 * (i + 1) / seg;
            Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0)));
            Vec3 d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
            vtx(vc, m, o.add(d0.scale(r0)), r, g, b, aIn);
            vtx(vc, m, o.add(d1.scale(r0)), r, g, b, aIn);
            vtx(vc, m, o.add(d1.scale(r1)), r, g, b, aOut);
            vtx(vc, m, o.add(d0.scale(r1)), r, g, b, aOut);
        }
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, Math.max(0, Math.min(255, a)));
    }

    static void segment(VertexConsumer vc, Vec3 a, Vec3 b, float w0, float w1, int r, int g, int bl, int al0, int al1) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(cam.subtract(a));
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize();
        Matrix4f m = GripFx.view;
        Vec3 oa = a.subtract(cam);
        Vec3 ob = b.subtract(cam);
        vtx(vc, m, oa.subtract(side.scale(w0)), r, g, bl, al0);
        vtx(vc, m, oa.add(side.scale(w0)), r, g, bl, al0);
        vtx(vc, m, ob.add(side.scale(w1)), r, g, bl, al1);
        vtx(vc, m, ob.subtract(side.scale(w1)), r, g, bl, al1);
    }

    static void bolt(VertexConsumer vc, Vec3 a, Vec3 b, long seed, float width, int r, int g, int bl, int al, float jitter) {
        RandomSource rnd = RandomSource.create(seed);
        Vec3[] pts = new Vec3[17];
        pts[0] = a;
        pts[16] = b;
        double len = a.distanceTo(b);
        for (int step = 8; step >= 1; step /= 2) {
            for (int i = step; i < 16; i += step * 2) {
                Vec3 mid = pts[i - step].lerp(pts[i + step], 0.5);
                double off = len * jitter * step / 16.0;
                pts[i] = mid.add((rnd.nextDouble() - 0.5) * off, (rnd.nextDouble() - 0.5) * off, (rnd.nextDouble() - 0.5) * off);
            }
        }
        for (int i = 0; i < 16; i++) {
            float w = width * (1f - 0.5f * i / 16f);
            segment(vc, pts[i], pts[i + 1], w, w, r, g, bl, al, al);
            if (rnd.nextInt(5) == 0 && i > 1) {
                Vec3 fork = pts[i].add((rnd.nextDouble() - 0.5) * len * 0.2, (rnd.nextDouble() - 0.5) * len * 0.2, (rnd.nextDouble() - 0.5) * len * 0.2);
                segment(vc, pts[i], fork, w * 0.6f, 0.001f, r, g, bl, al, 0);
            }
        }
    }

    static void debris(ClientLevel lv, Vec3 at, int count, double speed) {
        BlockPos bp = BlockPos.containing(at.x, at.y - 0.2, at.z);
        BlockState st = lv.getBlockState(bp);
        if (st.isAir()) st = lv.getBlockState(bp.below());
        if (st.isAir()) return;
        RandomSource r = lv.random;
        BlockParticleOption opt = new BlockParticleOption(ParticleTypes.BLOCK, st);
        for (int i = 0; i < count; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double s = speed * (0.4 + r.nextDouble());
            lv.addParticle(opt, at.x + Math.cos(a) * 0.5, at.y + 0.1, at.z + Math.sin(a) * 0.5, Math.cos(a) * s, 0.25 + r.nextDouble() * 0.4, Math.sin(a) * s);
        }
    }

    static void burst(ClientLevel lv, Vec3 at, int kind) {
        RandomSource r = lv.random;
        switch (kind) {
            case FxPayload.HANDS, FxPayload.ABYSS -> {
                debris(lv, at, kind == FxPayload.ABYSS ? 50 : 24, 0.25);
                for (int i = 0; i < 20; i++) {
                    lv.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x + (r.nextDouble() - 0.5) * 2, at.y + 0.1, at.z + (r.nextDouble() - 0.5) * 2, 0, 0.05 + r.nextDouble() * 0.1, 0);
                }
            }
            case FxPayload.CLEAVE -> {
                for (int i = 0; i < 30; i++) {
                    lv.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, (r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.2) * 0.6, (r.nextDouble() - 0.5) * 1.2);
                }
            }
            case FxPayload.ERASE -> {
                for (int i = 0; i < 30; i++) {
                    lv.addParticle(ParticleTypes.SCULK_SOUL, at.x + (r.nextDouble() - 0.5), at.y + r.nextDouble() * 2, at.z + (r.nextDouble() - 0.5), 0, 0.06, 0);
                }
            }
            case FxPayload.LIGHTNING -> {
                for (int i = 0; i < 40; i++) {
                    lv.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 1, at.z, (r.nextDouble() - 0.5) * 1.5, r.nextDouble() * 1.2, (r.nextDouble() - 0.5) * 1.5);
                }
                debris(lv, at, 30, 0.3);
            }
            case FxPayload.CATACLYSM -> {
                lv.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
                for (int i = 0; i < 160; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double e = (r.nextDouble() - 0.3) * Math.PI;
                    double s = 1.5 + r.nextDouble() * 3;
                    lv.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, Math.cos(a) * Math.cos(e) * s, Math.sin(e) * s, Math.sin(a) * Math.cos(e) * s);
                }
                for (int i = 0; i < 60; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    lv.addParticle(ParticleTypes.CLOUD, at.x + Math.cos(a) * 3, at.y, at.z + Math.sin(a) * 3, Math.cos(a) * 1.8, 0.2, Math.sin(a) * 1.8);
                }
                debris(lv, at, 120, 1.2);
            }
            default -> {
            }
        }
    }
}
