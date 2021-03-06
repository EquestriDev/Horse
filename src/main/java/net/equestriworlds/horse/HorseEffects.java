package net.equestriworlds.horse;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
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

    static void friendJingle(HorsePlugin plugin, final Player player) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!player.isValid()) {
                    cancel();
                    return;
                }
                switch (tick) {
                case 0: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_GUITAR, SoundCategory.MASTER, 0.5f, 0.9f); break;
                case 1: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_GUITAR, SoundCategory.MASTER, 0.5f, 0.95f); break;
                case 2: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_GUITAR, SoundCategory.MASTER, 0.5f, 1.05f); break;
                default:
                    cancel();
                    return;
                }
                tick += 1;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    static void unfriendJingle(HorsePlugin plugin, final Player player) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!player.isValid()) {
                    cancel();
                    return;
                }
                switch (tick) {
                case 0: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_GUITAR, SoundCategory.MASTER, 0.5f, 1.0f); break;
                case 1: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_GUITAR, SoundCategory.MASTER, 0.5f, 0.9f); break;
                case 2: player.playSound(player.getEyeLocation(), Sound.BLOCK_NOTE_GUITAR, SoundCategory.MASTER, 0.5f, 0.5f); break;
                default:
                    cancel();
                    return;
                }
                tick += 1;
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    static void feed(HorsePlugin plugin, Player player, SpawnedHorse spawned, Feeding.Feed feed) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!player.isValid() || !spawned.isPresent()) {
                    cancel();
                    return;
                }
                Location pl = player.getEyeLocation();
                Location hl = spawned.getEntity().getEyeLocation();
                if (!pl.getWorld().equals(hl.getWorld())) {
                    cancel();
                    return;
                }
                Location l = pl.toVector().multiply(0.65).add(hl.toVector().multiply(0.35)).toLocation(pl.getWorld());
                switch (tick++) {
                case 0:
                    l.getWorld().playSound(l, Sound.ENTITY_HORSE_EAT, 1.0f, 1.0f);
                    l.getWorld().spawnParticle(Particle.ITEM_CRACK, l, 4, 0, 0, 0, 0.05, feed.item);
                    break;
                case 1:
                    l.getWorld().playSound(l, Sound.ENTITY_HORSE_EAT, 1.0f, 1.1f);
                    l.getWorld().spawnParticle(Particle.ITEM_CRACK, l, 8, 0, 0, 0, 0.05, feed.item);
                    break;
                case 2:
                    l.getWorld().playSound(l, Sound.ENTITY_HORSE_EAT, 1.0f, 1.2f);
                    l.getWorld().spawnParticle(Particle.ITEM_CRACK, l, 16, 0, 0, 0, 0.05, feed.item);
                    break;
                default:
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 6L);
    }

    static void grazeEffect(HorsePlugin plugin, Block block) {
        Location l = block.getLocation().add(0.5, 1.5, 0.0);
        l.getWorld().playSound(l, Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 1.0f, 1.0f);
        l.getWorld().spawnParticle(Particle.BLOCK_DUST, l, 12, 0.25, 0.25, 0.25, 0.1, Material.GRASS.getNewData((byte)0));
    }

    static void suckleEffect(HorsePlugin plugin, SpawnedHorse spawned) {
        Location loc = spawned.getEntity().getEyeLocation().add(0.0, 0.5, 0.0);
        loc.getWorld().playSound(loc, Sound.ENTITY_COW_MILK, SoundCategory.NEUTRAL, 0.5f, 1.35f);
        loc.getWorld().spawnParticle(Particle.BLOCK_DUST, loc, 32, 0.0, 0.0, 0.0, 0.1, Material.QUARTZ_BLOCK.getNewData((byte)0));
    }

    static void drinkEffect(HorsePlugin plugin, SpawnedHorse spawned, Block waterBlock) {
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                if (!spawned.isPresent()) {
                    cancel();
                    return;
                }
                Location loc = spawned.getEntity().getEyeLocation().add(0.0, 0.25, 0.0);
                Location loc2 = waterBlock.getLocation().add(0.5, 1.0, 0.5);
                switch (tick++) {
                case 0:
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, SoundCategory.NEUTRAL, 0.2f, 1.200f);
                    loc2.getWorld().spawnParticle(Particle.WATER_SPLASH, loc2, 8, 0.15, 0.15, 0.15, 1.0);
                    break;
                case 1:
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, SoundCategory.NEUTRAL, 0.3f, 1.225f);
                    loc2.getWorld().spawnParticle(Particle.WATER_SPLASH, loc2, 16, 0.20, 0.20, 0.20, 1.0);
                    break;
                case 2:
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, SoundCategory.NEUTRAL, 0.4f, 1.250f);
                    loc2.getWorld().spawnParticle(Particle.WATER_SPLASH, loc2, 32, 0.25, 0.25, 0.25, 1.0);
                    break;
                case 3:
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_DRINK, SoundCategory.NEUTRAL, 0.5f, 1.3f);
                    loc2.getWorld().spawnParticle(Particle.WATER_SPLASH, loc2, 64, 0.25, 0.25, 0.25, 1.0);
                    break;
                default:
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }
}
