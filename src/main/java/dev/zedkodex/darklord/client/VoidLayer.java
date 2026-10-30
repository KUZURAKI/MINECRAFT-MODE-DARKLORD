package dev.zedkodex.darklord.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.util.Mth;

public class VoidLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int COLS = 12;
    private static final int ROWS = 16;
    private static final float LENGTH = 1.7f;

    public VoidLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        float f = VoidClient.form(player);
        if (f <= 0.01f || player.isInvisible()) return;
        cape(pose, buffer, player, f, partialTick, ageInTicks);
    }

    private void cape(PoseStack pose, MultiBufferSource buffer, AbstractClientPlayer p, float f, float pt, float age) {
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        double dy = p.getY() - p.yo;
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        double fwd = dx * -Mth.sin(yaw) + dz * Mth.cos(yaw);
        double side = dx * Mth.cos(yaw) + dz * Mth.sin(yaw);
        float speed = (float) Math.min(1.0, Math.max(0.0, fwd) * 4.0);
        float fall = (float) Math.max(0.0, -dy) * 3f;
        float swing = p.getAttackAnim(pt);
        boolean air = !p.onGround();
        float lift = Mth.clamp(0.08f + speed * 1.1f + fall + swing * 0.5f + (air ? 0.25f : 0f), 0f, 1.35f);
        float flap = 0.04f + speed * 0.12f + (air ? 0.06f : 0f) + swing * 0.1f;
        float sway = (float) side * 3f;

        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        Matrix4f m = pose.last().pose();
        float[][] px = new float[ROWS + 1][COLS + 1];
        float[][] py = new float[ROWS + 1][COLS + 1];
        float[][] pz = new float[ROWS + 1][COLS + 1];
        for (int c = 0; c <= COLS; c++) {
            float u = c / (float) COLS * 2f - 1f;
            float y = -0.02f;
            float z = 0.15f;
            float dl = LENGTH / ROWS;
            for (int r = 0; r <= ROWS; r++) {
                float v = r / (float) ROWS;
                float half = 0.34f + 0.62f * (float) Math.pow(v, 1.15);
                float wave = Mth.sin(age * 0.25f - v * 6f + u * 2.5f) * flap * v + Mth.sin(age * 0.11f + u * 4f) * 0.02f * v;
                px[r][c] = u * half + sway * v * v * 0.4f;
                py[r][c] = y;
                pz[r][c] = z + wave - u * u * 0.14f * v;
                float a = lift * v * 1.2f + 0.05f;
                y += Mth.cos(a) * dl;
                z += Mth.sin(a) * dl;
            }
        }
        VertexConsumer vc = buffer.getBuffer(RenderType.debugQuads());
        int alpha = (int) (250 * f);
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                float ax = px[r + 1][c] - px[r][c], ay = py[r + 1][c] - py[r][c], az = pz[r + 1][c] - pz[r][c];
                float bx = px[r][c + 1] - px[r][c], by = py[r][c + 1] - py[r][c], bz = pz[r][c + 1] - pz[r][c];
                float nx = ay * bz - az * by, ny = az * bx - ax * bz, nz = ax * by - ay * bx;
                float len = Mth.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6f;
                float shade = Math.abs((nx * 0.3f - ny * 0.6f + nz * 0.74f) / len);
                float edge = (c == 0 || c == COLS - 1 || r == ROWS - 1) ? 1f : 0f;
                int col = (int) (6 + 22 * shade + 10 * edge);
                int red = col + (int) (6 * edge);
                vc.addVertex(m, px[r][c], py[r][c], pz[r][c]).setColor(red, col, col + 3, alpha);
                vc.addVertex(m, px[r + 1][c], py[r + 1][c], pz[r + 1][c]).setColor(red, col, col + 3, alpha);
                vc.addVertex(m, px[r + 1][c + 1], py[r + 1][c + 1], pz[r + 1][c + 1]).setColor(red, col, col + 3, alpha);
                vc.addVertex(m, px[r][c + 1], py[r][c + 1], pz[r][c + 1]).setColor(red, col, col + 3, alpha);
            }
        }
        pose.popPose();
    }
}
