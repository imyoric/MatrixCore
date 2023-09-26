package net.minecraft.world.entity.item;

import javax.annotation.Nullable;
import net.minecraft.core.particles.Particles;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.syncher.DataWatcher;
import net.minecraft.network.syncher.DataWatcherObject;
import net.minecraft.network.syncher.DataWatcherRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.EntityPose;
import net.minecraft.world.entity.EntitySize;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EnumMoveType;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.level.World;

// CraftBukkit start;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.event.CraftEventFactory;
import org.bukkit.event.entity.ExplosionPrimeEvent;
// CraftBukkit end

public class EntityTNTPrimed extends Entity implements TraceableEntity {

    private static final DataWatcherObject<Integer> DATA_FUSE_ID = DataWatcher.defineId(EntityTNTPrimed.class, DataWatcherRegistry.INT);
    private static final int DEFAULT_FUSE_TIME = 80;
    @Nullable
    public EntityLiving owner;
    public float yield = 4; // CraftBukkit - add field
    public boolean isIncendiary = false; // CraftBukkit - add field

    public EntityTNTPrimed(EntityTypes<? extends EntityTNTPrimed> entitytypes, World world) {
        super(entitytypes, world);
        this.blocksBuilding = true;
    }

    public EntityTNTPrimed(World world, double d0, double d1, double d2, @Nullable EntityLiving entityliving) {
        this(EntityTypes.TNT, world);
        Bukkit.getScheduler().runAsyncTaskWithMatrix(new Runnable() {
            @Override
            public void run() {
                setPos(d0, d1, d2);
                double d3 = world.threadSafeRandom.nextDouble() * 6.2831854820251465D;

                setDeltaMovement(-Math.sin(d3) * 0.02D, 0.20000000298023224D, -Math.cos(d3) * 0.02D);
                setFuse(80);
                xo = d0;
                yo = d1;
                zo = d2;
                owner = entityliving;
            }
        });
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(EntityTNTPrimed.DATA_FUSE_ID, 80);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    public boolean isExploded = false;

    @Override
    public void tick() {
        if(isExploded) return;
        Bukkit.getScheduler().runAsyncTaskWithMatrix(new Runnable() {
            @Override
            public void run() {
                if (level().spigotConfig.maxTntTicksPerTick > 0 && ++level().spigotConfig.currentPrimedTnt > level().spigotConfig.maxTntTicksPerTick) { return; } // Spigot
                if (!isNoGravity()) {
                    setDeltaMovement(getDeltaMovement().add(0.0D, -0.04D, 0.0D));
                }

                Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
                    @Override
                    public void run() {
                        move(EnumMoveType.SELF, getDeltaMovement());
                    }
                });

                setDeltaMovement(getDeltaMovement().scale(0.98D));
                if (onGround()) {
                    setDeltaMovement(getDeltaMovement().multiply(0.7D, -0.5D, 0.7D));
                }

                int i = getFuse() - 1;

                setFuse(i);
                if (i <= 0) {
                    // CraftBukkit start - Need to reverse the order of the explosion and the entity death so we have a location for the event
                    // discard();
                    if (!level().isClientSide && !isExploded) {
                        explode();
                        isExploded = true;
                    }
                    // CraftBukkit end
                } else {
                    updateInWaterStateAndDoFluidPushing();
                    if (level().isClientSide) {
                        level().addParticle(Particles.SMOKE, getX(), getY() + 0.5D, getZ(), 0.0D, 0.0D, 0.0D);
                    }
                }
            }
        });
    }
    EntityTNTPrimed getThis(){return this;}

    private void explode() {
        Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
            @Override
            public void run() {
                // CraftBukkit start
                // float f = 4.0F;
                ExplosionPrimeEvent event = CraftEventFactory.callExplosionPrimeEvent((org.bukkit.entity.Explosive) getBukkitEntity());

                if (!event.isCancelled()) {
                    level().explode(getThis(), getX(), getY(0.0625D), getZ(), event.getRadius(), event.getFire(), World.a.TNT);
                }
                // CraftBukkit end
            }
        });
    }

    @Override
    protected void addAdditionalSaveData(NBTTagCompound nbttagcompound) {
        nbttagcompound.putShort("Fuse", (short) this.getFuse());
    }

    @Override
    protected void readAdditionalSaveData(NBTTagCompound nbttagcompound) {
        this.setFuse(nbttagcompound.getShort("Fuse"));
    }

    @Nullable
    @Override
    public EntityLiving getOwner() {
        return this.owner;
    }

    @Override
    protected float getEyeHeight(EntityPose entitypose, EntitySize entitysize) {
        return 0.15F;
    }

    public void setFuse(int i) {
        this.entityData.set(EntityTNTPrimed.DATA_FUSE_ID, i);
    }

    public int getFuse() {
        return (Integer) this.entityData.get(EntityTNTPrimed.DATA_FUSE_ID);
    }
}
