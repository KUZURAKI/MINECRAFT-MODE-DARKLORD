package dev.zedkodex.darklord.client;

import com.mojang.math.Axis;
import dev.zedkodex.darklord.DarkLord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class Gfx {
    private Gfx() {}

    public static final ResourceLocation FONT = ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "pixel");
    private static final Style STYLE = Style.EMPTY.withFont(FONT);

    public static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "textures/gui/" + name + ".png");
    }

    public static int vw;
    public static int vh;

    public static int gray(int rgb) {
        int l = (int) (((rgb >> 16) & 255) * 0.3f + ((rgb >> 8) & 255) * 0.59f + (rgb & 255) * 0.11f);
        return (l << 16) | (l << 8) | l;
    }

    public static float clamp(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    public static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public static int rgba(int rgb, float alpha) {
        return ((int) (clamp(alpha) * 255f) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = clamp(t);
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    public static int hash(int x) {
        x *= 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return x & 0x7FFFFFFF;
    }

    public static int width(String s) {
        return Minecraft.getInstance().font.width(Component.literal(s).withStyle(STYLE));
    }

    public static void text(GuiGraphics g, String s, float x, float y, int argb, float scale, boolean shadow) {
        if (((argb >>> 24) & 255) < 12) return;
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(Minecraft.getInstance().font, Component.literal(s).withStyle(STYLE), 0, 0, argb, shadow);
        g.pose().popPose();
    }

    public static void textCentered(GuiGraphics g, String s, float cx, float y, int argb, float scale, boolean shadow) {
        text(g, s, cx - width(s) * scale / 2f, y, argb, scale, shadow);
    }

    public static void icon(GuiGraphics g, String name, float cx, float cy, float size, float rotDeg, float scale, float alpha) {
        icon(g, name, cx, cy, size, rotDeg, scale, alpha, 1f);
    }

    public static void icon(GuiGraphics g, String name, float cx, float cy, float size, float rotDeg, float scale, float alpha, float tint) {
        if (alpha <= 0.01f) return;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(rotDeg));
        g.pose().scale(scale, scale, 1f);
        g.setColor(tint, tint, tint, clamp(alpha));
        g.blit(tex(name), (int) (-size / 2), (int) (-size / 2), (int) size, (int) size, 0, 0, 64, 64, 64, 64);
        g.setColor(1f, 1f, 1f, 1f);
        g.pose().popPose();
    }

    public static float easeOutBack(float t) {
        t = clamp(t);
        float c1 = 1.70158f, c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    public static float smooth(float t) {
        t = clamp(t);
        return t * t * (3f - 2f * t);
    }
}
