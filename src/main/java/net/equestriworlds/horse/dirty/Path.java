package net.equestriworlds.horse.dirty;

import java.lang.reflect.Field;
import net.minecraft.server.v1_12_R1.Entity;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.Navigation;
import net.minecraft.server.v1_12_R1.PathfinderGoalSelector;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;

public final class Path {
    private final Field fieldEntityInsentientGoalSelector;

    public Path() {
        try {
            this.fieldEntityInsentientGoalSelector = EntityInsentient.class.getDeclaredField("goalSelector");
            this.fieldEntityInsentientGoalSelector.setAccessible(true);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // UNUSED
    public void removeGoalSelectors(org.bukkit.entity.Entity bukkitEntity) {
        Entity entity = ((CraftEntity)bukkitEntity).getHandle();
        if (!(entity instanceof EntityInsentient)) return;
        try {
            EntityInsentient insentient = (EntityInsentient)entity;
            PathfinderGoalSelector goalSelector = new PathfinderGoalSelector(insentient.getWorld().methodProfiler);
            this.fieldEntityInsentientGoalSelector.set(insentient, goalSelector);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void navigate(org.bukkit.entity.Entity bukkitEntity, org.bukkit.Location bukkitLocation, double speed) {
        Entity entity = ((CraftEntity)bukkitEntity).getHandle();
        if (!(entity instanceof EntityInsentient)) throw new IllegalArgumentException("Insentient entity expected");
        EntityInsentient insentient = (EntityInsentient)entity;
        Navigation navigation = (Navigation)insentient.getNavigation();
        navigation.a(bukkitLocation.getX(), bukkitLocation.getY(), bukkitLocation.getZ(), speed);
    }

    // UNUSED
    public void moveTowards(org.bukkit.entity.Entity bukkitEntity, org.bukkit.Location bukkitLocation, double speed) {
        Entity entity = ((CraftEntity)bukkitEntity).getHandle();
        if (!(entity instanceof EntityInsentient)) throw new IllegalArgumentException("Insentient entity expected");
        EntityInsentient insentient = (EntityInsentient)entity;
        insentient.getControllerMove().a(bukkitLocation.getX(), bukkitLocation.getY(), bukkitLocation.getZ(), speed);
    }
}
