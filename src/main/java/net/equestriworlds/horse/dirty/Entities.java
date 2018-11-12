package net.equestriworlds.horse.dirty;

import net.minecraft.server.v1_12_R1.BlockPosition;
import net.minecraft.server.v1_12_R1.EntityInsentient;
import net.minecraft.server.v1_12_R1.EntityLeash;
import org.bukkit.craftbukkit.v1_12_R1.entity.CraftEntity;

public final class Entities {
    public org.bukkit.entity.LeashHitch tieUp(org.bukkit.entity.LivingEntity bukkitEntity, int x, int y, int z) {
        EntityInsentient entity = (EntityInsentient)((CraftEntity)bukkitEntity).getHandle();
        BlockPosition blockposition = new BlockPosition(x, y, z);
        EntityLeash entityLeash = EntityLeash.b(entity.world, blockposition);
        if (entityLeash == null) entityLeash = EntityLeash.a(entity.world, blockposition);
        entity.setLeashHolder(entityLeash, true);
        return (org.bukkit.entity.LeashHitch)entityLeash.getBukkitEntity();
    }
}
