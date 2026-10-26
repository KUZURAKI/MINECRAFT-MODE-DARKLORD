package dev.zedkodex.darklord.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.GripPayload;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = DarkLord.MODID, value = Dist.CLIENT)
public final class GripClient {
    private GripClient() {}

    private static final double RANGE = 24.0;

    private static int hovered = -1;
    private static int glowing = -1;
    static boolean gripping;
    static int gripId = -1;
    static long gripMs;
    static long crushMs;
    static long releaseMs;
    static int variant;
    static int seed;
    static Vec3 lastTarget = Vec3.ZERO;
    static float lastHeight = 1f;
    static float lastWidth = 0.6f;
    private static float curse;
    private static long lastMs;

    private static void send(int action, int id, float value) {
        PacketDistributor.sendToServer(new GripPayload(action, id, value));
    }

    private static boolean able(Minecraft mc, LocalPlayer p) {
        return mc.screen == null && p.getMainHandItem().isEmpty() && !p.isSpectator();
    }

    static int pick(ClientLevel lv, LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        double wall = RANGE * RANGE;
        var block = lv.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        if (block.getType() != net.minecraft.world.phys.HitResult.Type.MISS) wall = eye.distanceToSqr(block.getLocation());
        AABB box = p.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, eye, end, box,
                e -> e instanceof LivingEntity && e != p && !e.isSpectator() && e.isPickable() && e.isAlive(), RANGE * RANGE);
        if (hit == null || eye.distanceToSqr(hit.getLocation()) > wall) return -1;
        return hit.getEntity().getId();
    }

    private static void glow(ClientLevel lv, int id) {
        if (glowing != id && glowing >= 0) {
            Entity old = lv.getEntity(glowing);
            if (old != null) old.setGlowingTag(false);
        }
        glowing = id;
        if (id >= 0) {
            Entity e = lv.getEntity(id);
            if (e != null) e.setGlowingTag(true);
        }
    }

    private static void stop() {
        if (gripping) {
            send(GripPayload.STOP, gripId, 0f);
            releaseMs = Util.getMillis();
        }
        gripping = false;
        gripId = -1;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel lv = mc.level;
        if (p == null || lv == null) {
            gripping = false;
            hovered = -1;
            glowing = -1;
            return;
        }
        boolean able = able(mc, p);
        if (gripping) {
            Entity e = lv.getEntity(gripId);
            if (!able || e == null || !e.isAlive() || !mc.options.keyUse.isDown()) stop();
        }
        hovered = gripping ? gripId : able ? pick(lv, p) : -1;
        glow(lv, hovered);

        if (gripping) {
            Entity e = lv.getEntity(gripId);
            if (e != null) {
                lastTarget = e.position().add(0, e.getBbHeight() / 2, 0);
                lastHeight = e.getBbHeight();
                lastWidth = e.getBbWidth();
                GripFx.particles(lv, p, e);
            }
        }
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        if (gripping) {
            if (event.isAttack()) {
                send(GripPayload.CRUSH, gripId, 0f);
                gripping = false;
                gripId = -1;
                crushMs = Util.getMillis();
            }
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && hovered >= 0 && able(mc, p)) {
            send(GripPayload.START, hovered, 0f);
            gripping = true;
            gripId = hovered;
            gripMs = Util.getMillis();
            variant = p.getRandom().nextInt(3);
            seed = p.getRandom().nextInt(100000);
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!gripping) return;
        double d = event.getScrollDeltaY();
        if (d != 0) send(GripPayload.DISTANCE, gripId, d > 0 ? 1f : -1f);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onArm(RenderLivingEvent.Pre<?, ?> event) {
        if (gripping && event.getEntity() == Minecraft.getInstance().player
                && event.getRenderer().getModel() instanceof PlayerModel<?> model) {
            model.rightArmPose = HumanoidModel.ArmPose.SPYGLASS;
        }
    }

    @SubscribeEvent
    static void onHand(RenderHandEvent event) {
        long now = Util.getMillis();
        float crush = 1f - Gfx.clamp((now - crushMs) / 450f);
        if (event.getHand() != InteractionHand.MAIN_HAND || (!gripping && crush <= 0f)) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        event.setCanceled(true);
        PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(p);
        float reach = gripping ? Gfx.smooth(Gfx.clamp((now - gripMs) / 280f)) : crush;
        float t = now / 1000f;
        float shake = gripping ? 0.6f : 3f * crush;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-0.12f * reach, 0.12f * reach, -0.18f * reach + (gripping ? 0f : 0.06f * crush));
        pose.mulPose(Axis.XP.rotationDegrees(-22f * reach + (float) Math.sin(t * 37f) * shake));
        pose.mulPose(Axis.YP.rotationDegrees(10f * reach + (float) Math.cos(t * 29f) * shake));
        pose.translate(0.64000005F, -0.6F, -0.71999997F);
        pose.mulPose(Axis.YP.rotationDegrees(45.0F));
        pose.translate(-1.0F, 3.6F, 3.5F);
        pose.mulPose(Axis.ZP.rotationDegrees(120.0F));
        pose.mulPose(Axis.XP.rotationDegrees(200.0F));
        pose.mulPose(Axis.YP.rotationDegrees(-135.0F));
        pose.translate(5.6F, 0.0F, 0.0F);
        renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), p);
        pose.popPose();
    }

    public static void overlay(GuiGraphics g, DeltaTracker delta) {
        long now = Util.getMillis();
        float dt = lastMs == 0 ? 16 : Math.min(100, now - lastMs);
        lastMs = now;
        float target = gripping ? 1f : 0f;
        curse += (target - curse) * (1f - (float) Math.exp(-dt / (gripping ? 250f : 600f)));
        float flash = 1f - Gfx.clamp((now - crushMs) / 500f);
        if (curse < 0.01f && flash <= 0f) return;
        int w = g.guiWidth();
        int h = g.guiHeight();
        float pulse = 0.8f + 0.2f * (float) Math.sin(now / 160.0);
        float a = curse * pulse;
        int n = 22;
        int wb = (int) (w * 0.24f);
        int hb = (int) (h * 0.3f);
        for (int i = 0; i < n; i++) {
            float f = 1f - i / (float) n;
            int col = Gfx.rgba(0x05000A, a * 0.75f * f * f);
            int x0 = i * wb / n;
            int x1 = (i + 1) * wb / n;
            g.fill(x0, 0, x1, h, col);
            g.fill(w - x1, 0, w - x0, h, col);
        }
        g.fillGradient(0, 0, w, hb, Gfx.rgba(0x05000A, a * 0.7f), Gfx.rgba(0x05000A, 0f));
        g.fillGradient(0, h - hb, w, h, Gfx.rgba(0x05000A, 0f), Gfx.rgba(0x05000A, a * 0.7f));
        for (int i = 0; i < 10; i++) {
            int seed = Gfx.hash(i * 97 + (int) (now / 900));
            boolean left = (seed & 1) == 0;
            int y = seed % Math.max(1, h);
            int len = 20 + seed % 60;
            int x = left ? 0 : w;
            int dir = left ? 1 : -1;
            for (int k = 0; k < len; k += 2) {
                int jx = x + dir * k;
                int jy = y + (int) (Math.sin((k + seed) * 0.3) * 4);
                g.fill(jx, jy, jx + 2, jy + 1, Gfx.rgba(0x4A0E5C, a * 0.5f * (1f - k / (float) len)));
            }
        }
        if (flash > 0f) g.fill(0, 0, w, h, Gfx.rgba(0x000000, flash * 0.55f));
    }
}
