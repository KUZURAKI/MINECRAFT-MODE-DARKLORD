package dev.zedkodex.darklord.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import dev.zedkodex.darklord.DarkLord;
import dev.zedkodex.darklord.net.GripPayload;
import net.minecraft.core.particles.DustParticleOptions;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = DarkLord.MODID)
public final class Grip {
    private Grip() {}

    public static final ResourceKey<DamageType> CRUSH = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(DarkLord.MODID, "crush"));
    private static final double RANGE = 24.0;
    private static final DustParticleOptions SHADOW = new DustParticleOptions(new Vector3f(0.05f, 0.0f, 0.08f), 1.6f);

    private static final class Hold {
        int target;
        double dist;
    }

    private static final Map<UUID, Hold> holds = new HashMap<>();

    private record Bolt(ServerLevel level, Vec3 pos, long due) {}

    private static final java.util.List<Bolt> bolts = new java.util.ArrayList<>();

    public static void handle(ServerPlayer p, GripPayload msg) {
        switch (msg.action()) {
            case GripPayload.START -> start(p, msg.entityId());
            case GripPayload.STOP -> holds.remove(p.getUUID());
            case GripPayload.DISTANCE -> {
                Hold h = holds.get(p.getUUID());
                if (h != null) h.dist = Math.max(2.0, Math.min(RANGE, h.dist + msg.value()));
            }
            case GripPayload.CRUSH -> crush(p);
            case GripPayload.ERASE -> VoidForm.ability(p, dev.zedkodex.darklord.net.AbilityPayload.ERASE, msg.entityId());
            default -> {
            }
        }
    }

    private static boolean valid(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity le && le != p && le.isAlive() && le.level() == p.level()
                && le.distanceTo(p) <= RANGE + 4 && !(le instanceof ServerPlayer sp && (sp.isCreative() || sp.isSpectator()));
    }

    private static void start(ServerPlayer p, int id) {
        if (p.isSpectator() || !VoidForm.isVoid(p)) return;
        Entity e = p.level().getEntity(id);
        if (!valid(p, e)) return;
        Hold h = new Hold();
        h.target = id;
        h.dist = Math.max(2.0, Math.min(RANGE, p.getEyePosition().distanceTo(e.position().add(0, e.getBbHeight() / 2, 0))));
        holds.put(p.getUUID(), h);
        p.level().playSound(null, e.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 1.4f, 0.6f);
        p.level().playSound(null, p.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.0f, 0.7f);
    }

    private static void crush(ServerPlayer p) {
        Hold h = holds.remove(p.getUUID());
        if (h == null) return;
        Entity e = p.level().getEntity(h.target);
        if (!valid(p, e)) return;
        LivingEntity le = (LivingEntity) e;
        ServerLevel sl = (ServerLevel) p.level();
        Vec3 c = le.position().add(0, le.getBbHeight() / 2, 0);
        sl.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 40, 0.4, 0.5, 0.4, 0.15);
        sl.sendParticles(ParticleTypes.SCULK_SOUL, c.x, c.y, c.z, 12, 0.3, 0.4, 0.3, 0.05);
        sl.sendParticles(SHADOW, c.x, c.y, c.z, 30, 0.5, 0.6, 0.5, 0.0);
        sl.playSound(null, le.blockPosition(), SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 1.0f, 0.55f);
        sl.playSound(null, le.blockPosition(), SoundEvents.SKELETON_HURT, SoundSource.PLAYERS, 1.0f, 0.5f);
        long t = sl.getGameTime();
        for (int i = 0; i < 4; i++) {
            Vec3 off = new Vec3((sl.random.nextDouble() - 0.5) * 0.8, 0, (sl.random.nextDouble() - 0.5) * 0.8);
            bolts.add(new Bolt(sl, le.position().add(off), t + i * 4L));
        }
        le.invulnerableTime = 0;
        DamageSource src = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CRUSH), p);
        le.hurt(src, Math.max(1000f, le.getMaxHealth() * 10f));
    }

    @SubscribeEvent
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (bolts.isEmpty()) return;
        bolts.removeIf(b -> {
            if (b.level().getGameTime() < b.due()) return false;
            net.minecraft.world.entity.LightningBolt bolt = net.minecraft.world.entity.EntityType.LIGHTNING_BOLT.create(b.level());
            if (bolt != null) {
                bolt.moveTo(b.pos());
                bolt.setVisualOnly(true);
                b.level().addFreshEntity(bolt);
            }
            b.level().sendParticles(ParticleTypes.SOUL_FIRE_FLAME, b.pos().x, b.pos().y + 0.8, b.pos().z, 25, 0.3, 0.6, 0.3, 0.08);
            return true;
        });
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        Hold h = holds.get(p.getUUID());
        if (h == null) return;
        Entity e = p.level().getEntity(h.target);
        if (!p.isAlive() || p.isSpectator() || !VoidForm.isVoid(p) || !valid(p, e)) {
            holds.remove(p.getUUID());
            return;
        }
        LivingEntity le = (LivingEntity) e;
        Vec3 aim = p.getEyePosition().add(p.getLookAngle().scale(h.dist)).add(0, 0.6, 0);
        Vec3 center = le.position().add(0, le.getBbHeight() / 2, 0);
        Vec3 pull = aim.subtract(center);
        le.setDeltaMovement(pull.scale(0.3).add(0, 0.04, 0));
        le.hurtMarked = true;
        le.resetFallDistance();
        if (le instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        ServerLevel sl = (ServerLevel) p.level();
        if (p.tickCount % 2 == 0) {
            sl.sendParticles(SHADOW, center.x, center.y, center.z, 4, le.getBbWidth() * 0.6, le.getBbHeight() * 0.5, le.getBbWidth() * 0.6, 0.0);
            sl.sendParticles(ParticleTypes.SMOKE, center.x, center.y - le.getBbHeight() / 2, center.z, 2, 0.3, 0.05, 0.3, 0.01);
        }
        if (p.tickCount % 5 == 0) sl.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z, 1, 0.3, 0.3, 0.3, 0.02);
        if (p.tickCount % 40 == 0) sl.playSound(null, le.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.8f, 0.6f);
    }
}
