package net.equestriworlds.horse;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

final class HorseEffects {
    private HorseEffects() { }

    static void claim(HorsePlugin plugin, final Player player, final AbstractHorse entity) {
        new BukkitRunnable() {
            int ticks;
            @Override
            public void run() {
                if (!player.isValid() || !entity.isValid()) return;
                switch (ticks) {
                case 0: case 20: case 40: case 60: case 80: case 100:
                    player.spawnParticle(Particle.HEART, entity.getEyeLocation().add(0, 0.5, 0), 4, 0.4, 0.4, 0.4, 0.0);
                    break;
                case 1:
                    player.playSound(entity.getEyeLocation(), Sound.ENTITY_HORSE_ARMOR, SoundCategory.NEUTRAL, 0.45f, 1.25f);
                    break;
                case 21:
                    cancel();
                    return;
                default: break;
                }
                ticks += 1;
            }
        }.runTaskTimer(plugin, 1, 1);
    }

    static void teleport(HorsePlugin plugin, final Player player, Location loc) {
        new BukkitRunnable() {
            int ticks;
            @Override
            public void run() {
                if (!player.isValid()) return;
                switch (ticks) {
                case 0:
                    player.playSound(loc, Sound.ENTITY_HORSE_GALLOP, SoundCategory.NEUTRAL, 0.5f, 1.0f);
                    break;
                case 10:
                    player.playSound(loc, Sound.ENTITY_HORSE_GALLOP, SoundCategory.NEUTRAL, 0.75f, 1.0f);
                    cancel();
                    return;
                default: break;
                }
                ticks += 1;
            }
        }.runTaskTimer(plugin, 1, 1);
    }
}
