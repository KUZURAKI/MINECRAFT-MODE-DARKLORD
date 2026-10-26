package dev.zedkodex.darklord.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.WraithPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid = DarkLord.MODID)
public final class Wraith {
    private Wraith() {}

    private static final String TAG = "modehud_wraith_prev";
    private static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.04f, 0.0f, 0.07f), 2.2f);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.55f, 0.0f, 0.04f), 1.2f);
    private static final List<SoundEvent> WHISPERS = List.of(
            SoundEvents.WARDEN_AMBIENT, SoundEvents.WARDEN_NEARBY_CLOSEST, SoundEvents.SCULK_SHRIEKER_SHRIEK,
            SoundEvents.ENDERMAN_STARE, SoundEvents.WITHER_AMBIENT, SoundEvents.SOUL_ESCAPE.value(),
            SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS.value(), SoundEvents.PHANTOM_AMBIENT);

    private static final Map<UUID, GameType> active = new HashMap<>();

    public static boolean isWraith(ServerPlayer p) {
        return active.containsKey(p.getUUID());
    }

    private static void broadcast(ServerPlayer p, int state) {
        WraithPayload msg = new WraithPayload(p.getId(), state, p.getX(), p.getY(), p.getZ(), p.getYRot());
        PacketDistributor.sendToPlayersNear(p.serverLevel(), null, p.getX(), p.getY(), p.getZ(), 128, msg);
        PacketDistributor.sendToPlayer(p, msg);
    }

    public static void toggle(ServerPlayer p, boolean on) {
        if (on == isWraith(p)) return;
        if (on) enter(p);
        else exit(p);
    }

    private static void enter(ServerPlayer p) {
        GameType prev = p.gameMode.getGameModeForPlayer();
        if (prev == GameType.SPECTATOR || !p.isAlive()) return;
        active.put(p.getUUID(), prev);
        p.getPersistentData().putString(TAG, prev.getName());
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 80, 0.6, 0.9, 0.6, 0.2);
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 50, 0.8, 1.0, 0.8, 0.12);
        sl.sendParticles(SHADOW, c.x, c.y, c.z, 60, 0.8, 1.0, 0.8, 0.0);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 20, 0.5, 0.8, 0.5, 0.08);
        sl.playSound(null, p.blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 0.6f);
        sl.playSound(null, p.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.9f, 0.4f);
        p.setGameMode(GameType.SPECTATOR);
        broadcast(p, WraithPayload.ON);
    }

    private static void exit(ServerPlayer p) {
        GameType prev = active.remove(p.getUUID());
        p.getPersistentData().remove(TAG);
        if (prev == null) return;
        restore(p, prev);
        broadcast(p, WraithPayload.OFF);
    }

    private static void restore(ServerPlayer p, GameType prev) {
        ServerLevel sl = p.serverLevel();
        AABB box = p.getDimensions(p.getPose()).makeBoundingBox(p.position());
        for (int dy = 0; dy < 48; dy++) {
            AABB moved = box.move(0, dy, 0);
            if (sl.noCollision(moved) && !sl.containsAnyLiquid(moved)) {
                if (dy > 0) p.teleportTo(p.getX(), p.getY() + dy, p.getZ());
                break;
            }
        }
        p.setGameMode(prev);
        p.resetFallDistance();
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, false, false));
        Vec3 c = p.position().add(0, 1, 0);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 60, 0.4, 0.8, 0.4, 0.05);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 60, 0.6, 1.0, 0.6, 0.1);
        sl.sendParticles(SHADOW, c.x, c.y, c.z, 40, 0.5, 0.9, 0.5, 0.0);
        sl.playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.5f);
        sl.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.8f, 0.6f);
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isWraith(p)) return;
        if (p.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            active.remove(p.getUUID());
            p.getPersistentData().remove(TAG);
            broadcast(p, WraithPayload.OFF);
            return;
        }
        broadcast(p, WraithPayload.MOVE);
        ServerLevel sl = p.serverLevel();
        RandomSource r = p.getRandom();
        Vec3 c = p.position().add(0, 1.0, 0);
        sl.sendParticles(SHADOW, c.x, c.y, c.z, 3, 0.35, 0.7, 0.35, 0.0);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y - 0.6, c.z, 1, 0.25, 0.2, 0.25, 0.01);
        if (r.nextInt(3) == 0) sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y - 0.8, c.z, 2, 0.2, 0.1, 0.2, 0.01);
        if (r.nextInt(6) == 0) sl.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 1, 0.4, 0.6, 0.4, 0.02);
        if (r.nextInt(8) == 0) sl.sendParticles(BLOOD, c.x, c.y + 0.6, c.z, 2, 0.3, 0.3, 0.3, 0.0);
        if (r.nextInt(25) == 0) randomEvent(p, sl, r, c);
        if (p.tickCount % 20 == 0) dread(p, sl);
    }

    private static void ring(ServerLevel sl, Vec3 c, ParticleOptions type, double radius, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sl.sendParticles(type, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0.02, 0, 0.0);
        }
    }

    private static void randomEvent(ServerPlayer p, ServerLevel sl, RandomSource r, Vec3 c) {
        switch (r.nextInt(7)) {
            case 0 -> ring(sl, c.add(0, -0.9, 0), ParticleTypes.SOUL_FIRE_FLAME, 1.2 + r.nextDouble(), 28);
            case 1 -> sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 40, 0.2, 0.2, 0.2, 0.25);
            case 2 -> {
                for (int i = 0; i < 12; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double y = c.y + (r.nextDouble() - 0.5) * 2;
                    sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x + Math.cos(a) * 1.5, y, c.z + Math.sin(a) * 1.5, 1, 0, 0, 0, 0.02);
                }
            }
            case 3 -> ring(sl, c.add(0, 0.8, 0), ParticleTypes.REVERSE_PORTAL, 0.8, 20);
            case 4 -> sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0.0);
            case 5 -> sl.sendParticles(BLOOD, c.x, c.y + 0.6, c.z, 25, 0.5, 0.6, 0.5, 0.0);
            default -> sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 30, 0.6, 0.9, 0.6, 0.4);
        }
        SoundEvent s = WHISPERS.get(r.nextInt(WHISPERS.size()));
        sl.playSound(null, p.blockPosition(), s, SoundSource.PLAYERS, 0.7f, 0.4f + r.nextFloat() * 0.3f);
    }

    private static void dread(ServerPlayer p, ServerLevel sl) {
        AABB area = p.getBoundingBox().inflate(9);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, area, e -> e != p && e.isAlive())) {
            if (e instanceof EnderDragon || e instanceof WitherBoss) continue;
            if (e instanceof ServerPlayer other) {
                if (!other.isSpectator() && !other.isCreative()) other.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1, false, false));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, false, false));
                if (e instanceof Mob mob) mob.setTarget(null);
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isWraith(p)) exit(p);
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.getPersistentData().contains(TAG) && p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            GameType prev = GameType.byName(p.getPersistentData().getString(TAG), GameType.SURVIVAL);
            p.getPersistentData().remove(TAG);
            restore(p, prev);
        }
    }
}
