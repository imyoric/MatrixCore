package net.minecraft.world.entity.ai.goal.target;

import java.util.EnumSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.entity.EntityInsentient;
import net.minecraft.world.entity.EntityLiving;
import net.minecraft.world.entity.ai.goal.PathfinderGoal;
import net.minecraft.world.entity.ai.targeting.PathfinderTargetCondition;
import net.minecraft.world.entity.player.EntityHuman;
import net.minecraft.world.phys.AxisAlignedBB;
import org.bukkit.Bukkit;

public class PathfinderGoalNearestAttackableTarget<T extends EntityLiving> extends PathfinderGoalTarget {

    private static final int DEFAULT_RANDOM_INTERVAL = 10;
    protected final Class<T> targetType;
    protected final int randomInterval;
    @Nullable
    protected EntityLiving target;
    protected PathfinderTargetCondition targetConditions;

    public PathfinderGoalNearestAttackableTarget(EntityInsentient entityinsentient, Class<T> oclass, boolean flag) {
        this(entityinsentient, oclass, 10, flag, false, (Predicate) null);
    }

    public PathfinderGoalNearestAttackableTarget(EntityInsentient entityinsentient, Class<T> oclass, boolean flag, Predicate<EntityLiving> predicate) {
        this(entityinsentient, oclass, 10, flag, false, predicate);
    }

    public PathfinderGoalNearestAttackableTarget(EntityInsentient entityinsentient, Class<T> oclass, boolean flag, boolean flag1) {
        this(entityinsentient, oclass, 10, flag, flag1, (Predicate) null);
    }

    public PathfinderGoalNearestAttackableTarget(EntityInsentient entityinsentient, Class<T> oclass, int i, boolean flag, boolean flag1, @Nullable Predicate<EntityLiving> predicate) {
        super(entityinsentient, flag, flag1);
        this.targetType = oclass;
        this.randomInterval = reducedTickDelay(i);
        this.setFlags(EnumSet.of(PathfinderGoal.Type.TARGET));
        this.targetConditions = PathfinderTargetCondition.forCombat().range(this.getFollowDistance()).selector(predicate);
    }

    @Override
    public boolean canUse() {
        if (this.randomInterval > 0 && this.mob.getRandom().nextInt(this.randomInterval) != 0) {
            return false;
        } else {
            this.findTarget();
            return this.target != null;
        }
    }

    protected AxisAlignedBB getTargetSearchArea(double d0) {
        return this.mob.getBoundingBox().inflate(d0, 4.0D, d0);
    }

    protected void findTarget() {
        Bukkit.getScheduler().runTaskWithMatrix(new Runnable() {
            @Override
            public void run() {
                if (targetType != EntityHuman.class && targetType != EntityPlayer.class) {
                    target = mob.level().getNearestEntity(mob.level().getEntitiesOfClass(targetType, getTargetSearchArea(getFollowDistance()), (entityliving) -> {
                        return true;
                    }), targetConditions, mob, mob.getX(), mob.getEyeY(), mob.getZ());
                } else {
                    target = mob.level().getNearestPlayer(targetConditions, mob, mob.getX(), mob.getEyeY(), mob.getZ());
                }
            }
        });
    }

    @Override
    public void start() {
        mob.setTarget(this.target, target instanceof EntityPlayer ? org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_PLAYER : org.bukkit.event.entity.EntityTargetEvent.TargetReason.CLOSEST_ENTITY, true); // CraftBukkit - reason
        super.start();
    }

    public void setTarget(@Nullable EntityLiving entityliving) {
        this.target = entityliving;
    }
}
