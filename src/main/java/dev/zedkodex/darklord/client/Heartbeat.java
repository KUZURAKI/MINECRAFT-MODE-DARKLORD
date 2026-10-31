package dev.zedkodex.darklord.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.zedkodex.darklord.DarkLord;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class Heartbeat {
    private Heartbeat() {}

    private static final float DURATION = 15f;
    private static final float PERIOD = 0.85f;
    private static final double RADIUS = 64;

    private static long start = -1;
    private static int lastBeat = -1;
    private static boolean drawing;

    public static void start() {
        start = Util.getMillis();
        lastBeat = -1;
    }

    private static float time() {
        return start < 0 ? -1f : (Util.getMillis() - start) / 1000f;
    }

    static float strength() {
        float t = time();
        if (t < 0 || t > DURATION) return 0f;
        return Gfx.smooth(Gfx.clamp(t / 0.5f)) * (1f - Gfx.smooth(Gfx.clamp((t - (DURATION - 2.5f)) / 2.5f)));
    }

    static float pulse() {
        float t = time();
        if (t < 0) return 0f;
        float ph = (t % PERIOD) / PERIOD;
        double a = Math.exp(-Math.pow((ph - 0.05) / 0.05, 2));
        double b = 0.6 * Math.exp(-Math.pow((ph - 0.27) / 0.05, 2));
        return (float) (a + b);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        float t = time();
        if (p == null || mc.level == null || t < 0) return;
        if (t > DURATION) {
            start = -1;
            return;
        }
        int beat = (int) (t / PERIOD);
        if (beat != lastBeat && t < DURATION - 1.5f) {
            lastBeat = beat;
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.9f, 0.85f, false);
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || drawing) return;
        float k = strength();
        if (k <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        LocalPlayer me = mc.player;
        if (lv == null || me == null) return;
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        Vector3f right = new Vector3f(event.getCamera().getLeftVector()).mul(-1f);
        Vector3f up = new Vector3f(event.getCamera().getUpVector());
        PoseStack pose = event.getPoseStack();
        float beat = pulse();
        int alpha = (int) (110 * k * (0.35f + 0.65f * beat));
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Red red = new Red(b, alpha);
        MultiBufferSource src = type -> type.format() == DefaultVertexFormat.NEW_ENTITY && type.mode() == VertexFormat.Mode.QUADS ? red : Sink.INSTANCE;
        int count = 0;
        drawing = true;
        try {
            for (LivingEntity e : lv.getEntitiesOfClass(LivingEntity.class, me.getBoundingBox().inflate(RADIUS), e -> e != me && e.isAlive())) {
                Vec3 p = e.getPosition(pt);
                if (p.distanceTo(cam) > RADIUS) continue;
                @SuppressWarnings("unchecked")
                EntityRenderer<LivingEntity> r = (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(e);
                pose.pushPose();
                pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
                try {
                    r.render(e, e.getYRot(), pt, pose, src, 0xF000F0);
                } finally {
                    pose.popPose();
                }
                Vec3 chest = p.add(0, e.getBbHeight() * 0.62, 0).subtract(cam);
                glow(b, new Matrix4f(pose.last().pose()), chest, right, up, e.getBbHeight() * (0.45f + 0.25f * beat), (int) (170 * k * (0.3f + 0.7f * beat)));
                count++;
            }
        } finally {
            drawing = false;
        }
        MeshData mesh = b.build();
        if (mesh == null) return;
        if (count == 0) {
            mesh.close();
            return;
        }
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
    }

    private static void glow(BufferBuilder b, Matrix4f m, Vec3 o, Vector3f right, Vector3f up, float size, int a) {
        int seg = 16;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg;
            double a1 = (i + 1) * Math.PI * 2 / seg;
            v(b, m, o, right, up, 0, 0, a);
            v(b, m, o, right, up, 0, 0, a);
            v(b, m, o, right, up, (float) Math.cos(a1) * size, (float) Math.sin(a1) * size, 0);
            v(b, m, o, right, up, (float) Math.cos(a0) * size, (float) Math.sin(a0) * size, 0);
        }
    }

    private static void v(BufferBuilder b, Matrix4f m, Vec3 o, Vector3f right, Vector3f up, float x, float y, int a) {
        b.addVertex(m, (float) o.x + right.x * x + up.x * y, (float) o.y + right.y * x + up.y * y, (float) o.z + right.z * x + up.z * y)
                .setColor(230, 12, 30, a);
    }

    private record Red(BufferBuilder inner, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(230, 12, 30, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private enum Sink implements VertexConsumer {
        INSTANCE;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    public static void overlay(GuiGraphics g, DeltaTracker delta) {
        float k = strength();
        if (k <= 0.01f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float beat = pulse();
        g.fill(0, 0, w, h, Gfx.rgba(0x000000, 0.32f * k));
        int n = 18;
        int wb = (int) (w * 0.22f);
        for (int i = 0; i < n; i++) {
            float f = 1f - i / (float) n;
            int col = Gfx.rgba(0x5A0008, (0.25f + 0.35f * beat) * k * f * f);
            g.fill(i * wb / n, 0, (i + 1) * wb / n, h, col);
            g.fill(w - (i + 1) * wb / n, 0, w - i * wb / n, h, col);
        }
    }
}
