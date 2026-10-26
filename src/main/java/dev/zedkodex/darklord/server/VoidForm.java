package dev.zedkodex.darklord.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.AbilityPayload;
import dev.zedkodex.darklord.net.FxPayload;
import dev.zedkodex.darklord.net.VoidPayload;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

@EventBusSubscriber(modid = DarkLord.MODID)
public final class VoidForm {
    private VoidForm() {}

    public static final ResourceKey<DamageType> ERASE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "erase"));
    private static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.02f, 0.0f, 0.04f), 2.0f);
    private static final DustParticleOptions ASH = new DustParticleOptions(new Vector3f(0.08f, 0.08f, 0.1f), 1.0f);

    private static final Set<UUID> active = new HashSet<>();
    private static final Map<UUID, Map<Integer, Long>> cooldowns = new HashMap<>();

    private record Dissolve(ServerLevel level, int target, UUID owner, long start) {}

    private record Hole(ServerLevel level, Vec3 pos, UUID owner, long start) {}

    private static final List<Dissolve> dissolves = new ArrayList<>();
    private static final List<Hole> holes = new ArrayList<>();

    public static boolean isVoid(ServerPlayer p) {
        return active.contains(p.getUUID());
    }

    private static void sync(ServerPlayer p, boolean on) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new VoidPayload(p.getId(), on));
    }

    private static DamageSource source(ServerLevel sl, ResourceKey<DamageType> key, ServerPlayer p) {
        return new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key), p);
    }

    public static void toggle(ServerPlayer p) {
        if (Wraith.isWraith(p) || p.isSpectator()) return;
        ServerLevel sl = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        if (active.remove(p.getUUID())) {
            if (!p.isCreative()) {
                p.getAbilities().mayfly = false;
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                p.resetFallDistance();
                p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, false, false));
            }
            sl.sendParticles(SHADOW, c.x, c.y, c.z, 50, 0.4, 0.9, 0.4, 0.0);
            sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 40, 0.4, 0.9, 0.4, 0.05);
            sl.playSound(null, p.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.0f, 0.6f);
            sync(p, false);
        } else {
            active.add(p.getUUID());
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
            sl.sendParticles(SHADOW, c.x, c.y, c.z, 80, 0.5, 1.0, 0.5, 0.0);
            sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 50, 0.4, 0.9, 0.4, 0.1);
            sl.playSound(null, p.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 0.8f, 0.7f);
            sl.playSound(null, p.blockPosition(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 0.5f);
            sync(p, true);
        }
    }

    private static boolean ready(ServerPlayer p, int ability, int ticks) {
        Map<Integer, Long> m = cooldowns.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        long now = p.serverLevel().getGameTime();
        Long until = m.get(ability);
        if (until != null && now < until) return false;
        m.put(ability, now + ticks);
        return true;
    }

    public static void ability(ServerPlayer p, int ability, int target) {
        if (!isVoid(p) || !p.isAlive() || ability < 0 || ability >= AbilityPayload.COUNT) return;
        if (!ready(p, ability, AbilityPayload.COOLDOWN[ability])) return;
        switch (ability) {
            case AbilityPayload.ERASE -> erase(p, target);
            case AbilityPayload.DASH -> dash(p);
            case AbilityPayload.HANDS -> hands(p);
            case AbilityPayload.ABYSS -> abyss(p, target);
            case AbilityPayload.CLEAVE -> cleave(p);
            case AbilityPayload.DEVOUR -> devour(p);
            case AbilityPayload.SINGULARITY -> singularity(p);
            default -> {
            }
        }
    }

    private static void fx(ServerPlayer p, int kind, int target, Vec3 pos, float yaw) {
        PacketDistributor.sendToPlayersNear(p.serverLevel(), null, pos.x, pos.y, pos.z, 96,
                new FxPayload(kind, p.getId(), target, pos.x, pos.y, pos.z, yaw));
    }

    private static Vec3 ground(ServerLevel sl, Vec3 at) {
        net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(at);
        for (int i = 0; i < 12; i++) {
            if (!sl.getBlockState(bp.below()).getCollisionShape(sl, bp.below()).isEmpty()) return new Vec3(at.x, bp.getY() + 0.02, at.z);
            bp = bp.below();
        }
        return at;
    }

    private static List<LivingEntity> victims(ServerPlayer p, double r) {
        return p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive() && !(e instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())));
    }

    private static Vec3 aim(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        Vec3 end = eye.add(p.getLookAngle().scale(range));
        HitResult hit = p.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(p.getLookAngle().scale(0.8));
    }

    private static void line(ServerLevel sl, Vec3 a, Vec3 b, ParticleOptions type, double step) {
        double len = a.distanceTo(b);
        for (double d = 0; d < len; d += step) {
            Vec3 q = a.lerp(b, d / len);
            sl.sendParticles(type, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
        }
    }

    private static void ring(ServerLevel sl, Vec3 c, ParticleOptions type, double radius, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sl.sendParticles(type, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0, 0, 0, 0.0);
        }
    }

    private static void dash(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 from = p.position();
        Vec3 dest = aim(p, 22).subtract(0, p.getEyeHeight(), 0);
        for (int dy = 0; dy < 4; dy++) {
            AABB box = p.getBoundingBox().move(dest.subtract(from)).move(0, dy, 0);
            if (sl.noCollision(box)) {
                dest = dest.add(0, dy, 0);
                break;
            }
        }
        line(sl, from.add(0, 1, 0), dest.add(0, 1, 0), SHADOW, 0.4);
        line(sl, from.add(0, 1, 0), dest.add(0, 1, 0), ParticleTypes.SQUID_INK, 1.2);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1, from.z, 30, 0.3, 0.8, 0.3, 0.1);
        p.teleportTo(dest.x, dest.y, dest.z);
        p.resetFallDistance();
        sl.sendParticles(SHADOW, dest.x, dest.y + 1, dest.z, 40, 0.4, 0.8, 0.4, 0.0);
        sl.playSound(null, p.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 0.4f);
    }

    private record Delayed(ServerLevel level, UUID owner, int target, long due, int kind) {}

    private static final List<Delayed> delayed = new ArrayList<>();

    private static void hands(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        List<LivingEntity> list = new ArrayList<>(victims(p, 11));
        list.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        int n = 0;
        for (LivingEntity e : list) {
            if (n++ >= 8) break;
            Vec3 g = ground(sl, e.position());
            fx(p, FxPayload.HANDS, e.getId(), g, 0f);
            sl.sendParticles(SHADOW, g.x, g.y + 0.1, g.z, 20, 0.5, 0.05, 0.5, 0.0);
            delayed.add(new Delayed(sl, p.getUUID(), e.getId(), sl.getGameTime() + 7, 0));
        }
        sl.playSound(null, p.blockPosition(), SoundEvents.WARDEN_DIG, SoundSource.PLAYERS, 1.2f, 0.6f);
        sl.playSound(null, p.blockPosition(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    private static LivingEntity pickTarget(ServerPlayer p, int id, double range) {
        Entity e = p.serverLevel().getEntity(id);
        if (e instanceof LivingEntity le && le.isAlive() && le != p && le.distanceTo(p) <= range) return le;
        LivingEntity best = null;
        for (LivingEntity v : victims(p, range)) if (best == null || v.distanceToSqr(p) < best.distanceToSqr(p)) best = v;
        return best;
    }

    private static void abyss(ServerPlayer p, int id) {
        ServerLevel sl = p.serverLevel();
        LivingEntity t = pickTarget(p, id, 24);
        if (t == null) return;
        Vec3 g = ground(sl, t.position());
        fx(p, FxPayload.ABYSS, t.getId(), g, 0f);
        for (int i = 0; i < 36; i++) delayed.add(new Delayed(sl, p.getUUID(), t.getId(), sl.getGameTime() + 6 + i, i == 35 ? 2 : 1));
        sl.playSound(null, t.blockPosition(), SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, SoundSource.PLAYERS, 1.2f, 0.4f);
        sl.playSound(null, t.blockPosition(), SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 1.0f, 0.6f);
    }

    private static void cleave(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        fx(p, FxPayload.CLEAVE, -1, eye.add(look.scale(1.2)).add(0, -0.3, 0), p.getYRot());
        DamageSource src = sl.damageSources().indirectMagic(p, p);
        for (LivingEntity e : victims(p, 7)) {
            Vec3 to = e.position().add(0, e.getBbHeight() / 2, 0).subtract(eye);
            if (to.length() > 7 || to.normalize().dot(look) < 0.35) continue;
            e.hurt(src, 14f);
            e.setDeltaMovement(to.normalize().scale(1.2).add(0, 0.35, 0));
            e.hurtMarked = true;
            Vec3 c = e.position().add(0, e.getBbHeight() / 2, 0);
            sl.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.0f, 0.04f), 1.6f), c.x, c.y, c.z, 30, 0.3, 0.4, 0.3, 0.0);
            sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0.0);
        }
        sl.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.5f, 0.5f);
        sl.playSound(null, p.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.7f, 0.4f);
    }

    private static boolean stepDelayed(Delayed d) {
        ServerLevel sl = d.level();
        if (sl.getGameTime() < d.due()) return false;
        Entity e = sl.getEntity(d.target());
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return true;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(d.owner());
        if (d.kind() == 0) {
            le.setDeltaMovement(new Vec3(0, 1.1, 0));
            le.hurtMarked = true;
            le.hurt(owner != null ? sl.damageSources().indirectMagic(owner, owner) : sl.damageSources().magic(), 9f);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3, false, true));
            sl.sendParticles(ParticleTypes.SQUID_INK, le.getX(), le.getY() + 0.5, le.getZ(), 25, 0.4, 0.4, 0.4, 0.1);
            sl.playSound(null, le.blockPosition(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0f, 0.6f);
            return true;
        }
        le.setDeltaMovement(Vec3.ZERO);
        le.hurtMarked = true;
        if (le instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        le.setNoGravity(true);
        le.teleportTo(le.getX(), le.getY() - 0.075, le.getZ());
        sl.sendParticles(SHADOW, le.getX(), le.getY() + 0.1, le.getZ(), 6, le.getBbWidth() * 0.7, 0.05, le.getBbWidth() * 0.7, 0.0);
        if (sl.random.nextInt(3) == 0) sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, le.getX(), le.getY() + 0.1, le.getZ(), 3, 0.6, 0.1, 0.6, 0.02);
        if (d.kind() == 2) {
            le.setNoGravity(false);
            le.invulnerableTime = 0;
            DamageSource src = owner != null ? source(sl, ERASE, owner)
                    : new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ERASE));
            le.teleportTo(le.getX(), le.getY() + 2.7, le.getZ());
            le.hurt(src, Math.max(1000f, le.getMaxHealth() * 10f));
            sl.playSound(null, le.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 1.2f, 0.4f);
        }
        return true;
    }

    private static void devour(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        DamageSource src = sl.damageSources().indirectMagic(p, p);
        float healed = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), e -> e != p && e.isAlive())) {
            Vec3 from = e.position().add(0, e.getBbHeight() / 2, 0);
            line(sl, from, p.position().add(0, 1, 0), new DustParticleOptions(new Vector3f(0.5f, 0.0f, 0.05f), 1.2f), 0.3);
            sl.sendParticles(ParticleTypes.SOUL, from.x, from.y, from.z, 4, 0.2, 0.3, 0.2, 0.02);
            if (e.hurt(src, 5f)) healed += 2f;
        }
        if (healed > 0) p.heal(Math.min(healed, 12f));
        sl.playSound(null, p.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8f, 0.5f);
        sl.playSound(null, p.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.2f, 0.6f);
    }

    private static void singularity(ServerPlayer p) {
        ServerLevel sl = p.serverLevel();
        Vec3 c = aim(p, 26);
        holes.add(new Hole(sl, c, p.getUUID(), sl.getGameTime()));
        sl.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.5f, 0.4f);
    }

    public static void erase(ServerPlayer p, int id) {
        if (!isVoid(p)) return;
        LivingEntity picked = pickTarget(p, id, 28);
        if (picked == null) return;
        id = picked.getId();
        ServerLevel sl = p.serverLevel();
        Entity e = sl.getEntity(id);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le == p || le.distanceTo(p) > 28) return;
        if (le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator())) return;
        for (Dissolve d : dissolves) if (d.target() == id) return;
        dissolves.add(new Dissolve(sl, id, p.getUUID(), sl.getGameTime()));
        sl.playSound(null, le.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.5f, 0.4f);
        sl.playSound(null, le.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0f, 0.5f);
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        dissolves.removeIf(VoidForm::stepDissolve);
        delayed.removeIf(VoidForm::stepDelayed);
        holes.removeIf(VoidForm::stepHole);
    }

    private static boolean stepDissolve(Dissolve d) {
        Entity e = d.level().getEntity(d.target());
        if (!(e instanceof LivingEntity le) || !le.isAlive()) return true;
        long age = d.level().getGameTime() - d.start();
        float h = le.getBbHeight();
        float w = le.getBbWidth();
        double y = le.getY() + h * (1 - age / 12.0);
        d.level().sendParticles(SHADOW, le.getX(), y, le.getZ(), 18, w * 0.5, 0.1, w * 0.5, 0.0);
        d.level().sendParticles(ASH, le.getX(), y, le.getZ(), 8, w * 0.5, 0.1, w * 0.5, 0.03);
        d.level().sendParticles(ParticleTypes.SQUID_INK, le.getX(), y, le.getZ(), 3, w * 0.4, 0.1, w * 0.4, 0.02);
        le.setDeltaMovement(Vec3.ZERO);
        le.hurtMarked = true;
        if (age < 12) return false;
        ServerPlayer owner = d.level().getServer().getPlayerList().getPlayer(d.owner());
        le.invulnerableTime = 0;
        DamageSource src = owner != null ? source(d.level(), ERASE, owner)
                : new DamageSource(d.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ERASE));
        le.hurt(src, Math.max(1000f, le.getMaxHealth() * 10f));
        d.level().sendParticles(ParticleTypes.SCULK_SOUL, le.getX(), le.getY() + h / 2, le.getZ(), 10, w * 0.4, h * 0.4, w * 0.4, 0.05);
        return true;
    }

    private static boolean stepHole(Hole hole) {
        ServerLevel sl = hole.level();
        long age = sl.getGameTime() - hole.start();
        Vec3 c = hole.pos();
        double t = age / 20.0;
        for (int i = 0; i < 6; i++) {
            double a = t * 6 + i * Math.PI / 3;
            double r = 2.5 - (age % 20) / 10.0;
            sl.sendParticles(SHADOW, c.x + Math.cos(a) * r, c.y + Math.sin(t * 3 + i) * 0.5, c.z + Math.sin(a) * r, 1, 0, 0, 0, 0.0);
        }
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 6, 1.5, 1.5, 1.5, 0.05);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 2, 0.1, 0.1, 0.1, 0.0);
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(hole.owner());
        for (Entity e : sl.getEntities((Entity) null, new AABB(c, c).inflate(9), e -> (e instanceof LivingEntity || e instanceof ItemEntity) && e != owner)) {
            Vec3 pull = c.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
            double dist = Math.max(0.5, pull.length());
            e.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(pull.normalize().scale(Math.min(0.9, 2.5 / dist))));
            e.hurtMarked = true;
        }
        if (age % 20 == 0) sl.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.4f);
        if (age < 80) return false;
        DamageSource src = owner != null ? sl.damageSources().indirectMagic(owner, owner) : sl.damageSources().magic();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(4.5), e -> e != owner && e.isAlive())) {
            e.hurt(src, 14f);
            e.setDeltaMovement(e.position().subtract(c).normalize().scale(1.5).add(0, 0.5, 0));
            e.hurtMarked = true;
        }
        sl.sendParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0.0);
        sl.sendParticles(SHADOW, c.x, c.y, c.z, 120, 2, 2, 2, 0.0);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 80, 0.5, 0.5, 0.5, 0.4);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5f, 0.4f);
        sl.playSound(null, net.minecraft.core.BlockPos.containing(c), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.2f, 0.5f);
        return true;
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !isVoid(p)) return;
        if (!p.getAbilities().mayfly) {
            p.getAbilities().mayfly = true;
            p.onUpdateAbilities();
        }
        if (p.tickCount % 3 == 0) {
            ServerLevel sl = p.serverLevel();
            sl.sendParticles(SHADOW, p.getX(), p.getY() + 0.9, p.getZ(), 1, 0.3, 0.6, 0.3, 0.0);
            if (p.getRandom().nextInt(4) == 0) sl.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.1, p.getZ(), 1, 0.3, 0.0, 0.3, 0.01);
        }
    }

    @SubscribeEvent
    static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer t && event.getEntity() instanceof ServerPlayer viewer && isVoid(t)) {
            PacketDistributor.sendToPlayer(viewer, new VoidPayload(t.getId(), true));
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isVoid(p)) toggle(p);
    }

    @SubscribeEvent
    static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && isVoid(p)) sync(p, true);
    }
}
