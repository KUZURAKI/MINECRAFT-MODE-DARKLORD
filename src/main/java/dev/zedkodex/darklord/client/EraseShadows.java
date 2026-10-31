package dev.zedkodex.darklord.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.zedkodex.darklord.DarkLord;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class EraseShadows {
    private EraseShadows() {}

    private static final float TURN = 0.6f;
    private static final float HOLD = 10f;
    private static final float FADE = 2.5f;

    private static final class Shadow {
        final LivingEntity entity;
        final long start;
        Vec3 pos;
        float yaw;

        Shadow(LivingEntity entity, long start) {
            this.entity = entity;
            this.start = start;
        }
    }

    private static final List<Shadow> shadows = new ArrayList<>();
    private static boolean drawing;

    public static void add(Entity e) {
        if (e instanceof LivingEntity le) shadows.add(new Shadow(le, Util.getMillis()));
    }

    private static float age(Shadow s) {
        return (Util.getMillis() - s.start) / 1000f;
    }

    private static float darkness(Shadow s) {
        float t = age(s);
        if (t < TURN) return Gfx.smooth(t / TURN) * 0.95f;
        return 0.95f * (1f - Gfx.smooth(Gfx.clamp((t - TURN - HOLD) / FADE)));
    }

    @SubscribeEvent
    static void onLiving(RenderLivingEvent.Pre<?, ?> event) {
        if (drawing) return;
        for (Shadow s : shadows) {
            if (s.entity == event.getEntity() && age(s) >= TURN) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        ClientLevel lv = Minecraft.getInstance().level;
        if (lv == null) {
            shadows.clear();
            return;
        }
        shadows.removeIf(s -> age(s) > TURN + HOLD + FADE || s.entity.level() != lv);
        for (Shadow s : shadows) {
            float t = age(s);
            if (s.pos == null && t >= TURN) {
                s.pos = s.entity.position();
                s.yaw = s.entity.getYRot();
            }
        }
    }

    @SubscribeEvent
    static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || shadows.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        List<ResourceLocation> used = new ArrayList<>();
        for (Shadow s : shadows) {
            float a = darkness(s);
            if (a <= 0.01f) continue;
            LivingEntity e = s.entity;
            @SuppressWarnings("unchecked")
            EntityRenderer<LivingEntity> r = (EntityRenderer<LivingEntity>) mc.getEntityRenderDispatcher().getRenderer(e);
            ResourceLocation tex = r.getTextureLocation(e);
            used.add(tex);
            Vec3 p = s.pos != null ? s.pos : e.getPosition(pt);
            float yaw = s.pos != null ? s.yaw : e.getYRot();
            int death = e.deathTime;
            int hurt = e.hurtTime;
            e.deathTime = 0;
            e.hurtTime = 0;
            drawing = true;
            pose.pushPose();
            pose.translate(p.x - cam.x, p.y - cam.y, p.z - cam.z);
            try {
                r.render(e, yaw, s.pos != null ? 0f : pt, pose, type -> tinted(bs, type, tex, a), 0xF000F0);
            } finally {
                pose.popPose();
                drawing = false;
                e.deathTime = death;
                e.hurtTime = hurt;
            }
            if (s.pos != null) {
                FxKit.setup(event.getCamera());
                float w = e.getBbWidth() * 1.4f + 0.4f;
                FxKit.ring(bs.getBuffer(RenderType.debugQuads()), p.add(0, 0.03, 0), new Vec3(0, 1, 0), 0f, w, 0, 0, 0, (int) (200 * a), 0);
            }
        }
        for (ResourceLocation tex : used) bs.endBatch(RenderType.entityTranslucent(tex));
        bs.endBatch(RenderType.debugQuads());
    }

    private static VertexConsumer tinted(MultiBufferSource.BufferSource bs, RenderType type, ResourceLocation tex, float a) {
        if (type.format() != DefaultVertexFormat.NEW_ENTITY) return Sink.INSTANCE;
        return new Black(bs.getBuffer(RenderType.entityTranslucent(tex)), (int) (255 * a));
    }

    private record Black(VertexConsumer inner, int alpha) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            inner.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            inner.setColor(6, 4, 10, alpha * a / 255);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            inner.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            inner.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            inner.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            inner.setNormal(x, y, z);
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
}
