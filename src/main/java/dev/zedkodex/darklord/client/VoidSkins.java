package dev.zedkodex.darklord.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.zedkodex.darklord.DarkLord;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

public final class VoidSkins {
    private VoidSkins() {}

    private static final class Entry {
        ResourceLocation source;
        NativeImage original;
        DynamicTexture texture;
        ResourceLocation id;
        float level = -1f;
    }

    private static final Map<Integer, Entry> entries = new HashMap<>();

    public static ResourceLocation get(int entityId, ResourceLocation source, float f) {
        if (!RenderSystem.isOnRenderThread()) return null;
        Entry e = entries.get(entityId);
        if (e == null || !e.source.equals(source)) {
            if (e != null) release(entityId);
            NativeImage img = download(source);
            if (img == null) return null;
            e = new Entry();
            e.source = source;
            e.original = img;
            e.texture = new DynamicTexture(new NativeImage(img.getWidth(), img.getHeight(), true));
            e.id = ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "void_skin/" + entityId);
            Minecraft.getInstance().getTextureManager().register(e.id, e.texture);
            entries.put(entityId, e);
        }
        if (Math.abs(f - e.level) > 0.01f) {
            paint(e, f);
            e.level = f;
        }
        return e.id;
    }

    private static NativeImage download(ResourceLocation source) {
        AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(source);
        if (tex == null) return null;
        tex.bind();
        NativeImage img = new NativeImage(64, 64, false);
        img.downloadTexture(0, false);
        boolean any = false;
        for (int y = 0; y < img.getHeight() && !any; y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                if ((img.getPixelRGBA(x, y) >>> 24) != 0) {
                    any = true;
                    break;
                }
            }
        }
        if (!any) {
            img.close();
            return null;
        }
        return img;
    }

    private static void paint(Entry e, float f) {
        NativeImage src = e.original;
        NativeImage dst = e.texture.getPixels();
        if (dst == null) return;
        float keep = 1f - 0.93f * f;
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getPixelRGBA(x, y);
                int a = c >>> 24;
                int b = (int) (((c >> 16) & 255) * keep);
                int g = (int) (((c >> 8) & 255) * keep);
                int r = (int) ((c & 255) * keep);
                dst.setPixelRGBA(x, y, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        e.texture.upload();
    }

    public static void release(int entityId) {
        Entry e = entries.remove(entityId);
        if (e == null) return;
        e.original.close();
        Minecraft.getInstance().getTextureManager().release(e.id);
    }

    public static void retain(java.util.Set<Integer> ids) {
        for (Integer id : new java.util.ArrayList<>(entries.keySet())) {
            if (!ids.contains(id)) release(id);
        }
    }
}
