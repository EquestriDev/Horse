package net.equestriworlds.horse;

import java.time.Instant;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;

@RequiredArgsConstructor
final class Breeding implements Listener {
    private final HorsePlugin plugin;
    private static final int ONE_DAY = 60 * 60 * 24;

    static final class BreedException extends Exception {
        final Type type;
        final SpawnedHorse spawned;

        enum Type {
            PARENT_NOT_CUSTOM,
            BREEDER_NOT_VET,
            GENDER_MISMATCH,
            NOT_ADULT,
            ALREADY_PREGNANT,
            NOT_READY,
            ;

            BreedException make() {
                return new BreedException(this, null);
            }
            BreedException make(SpawnedHorse spawnedHorse) {
                return new BreedException(this, spawnedHorse);
            }
        }

        BreedException(Type type, SpawnedHorse spawned) {
            super(type.name());
            this.type = type;
            this.spawned = spawned;
        }
    }

    @RequiredArgsConstructor
    static final class BreedInfo {
        final SpawnedHorse mother, father;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onEntityBreed(EntityBreedEvent event) {
        // Fail fast conditions
        if (!(event.getEntity() instanceof AbstractHorse)) return;
        if (!(event.getMother() instanceof AbstractHorse)) return;
        if (!(event.getFather() instanceof AbstractHorse)) return;
        SpawnedHorse spawnedParentA = this.plugin.findSpawnedHorse((AbstractHorse)event.getMother());
        SpawnedHorse spawnedParentB = this.plugin.findSpawnedHorse((AbstractHorse)event.getFather());
        if (spawnedParentA == null && spawnedParentB == null) return;
        // If the breeder went offline, we better cancel.
        event.setCancelled(true);
        spawnedParentA.getEntity().setAge(1200);
        spawnedParentB.getEntity().setAge(1200);
        if (!(event.getBreeder() instanceof Player)) return;
        Player breeder = (Player)event.getBreeder();
        BreedInfo info;
        try {
            info = onBreed(spawnedParentA, spawnedParentB, breeder);
        } catch (BreedException be) {
            switch (be.type) {
            case PARENT_NOT_CUSTOM: breeder.sendMessage(ChatColor.RED + "One horse is feral!"); break;
            case BREEDER_NOT_VET: breeder.sendMessage(ChatColor.RED + "You are not a licensed vet!"); break;
            case GENDER_MISMATCH: breeder.sendMessage(ChatColor.RED + "You can only breed a mare and a stallion!"); break;
            case NOT_ADULT: breeder.sendMessage(ChatColor.RED + be.spawned.data.getStrippedName() + " is not an adult!"); break;
            case ALREADY_PREGNANT: breeder.sendMessage(ChatColor.RED + be.spawned.data.getStrippedName() + " is already pregnant!"); break;
            case NOT_READY: breeder.sendMessage(ChatColor.RED + be.spawned.data.getStrippedName() + " is still recovering!"); break;
            default: throw new IllegalArgumentException("Unhandled breed exception type: " + be.type);
            }
            return;
        }
        // Mother is pregnant
        info.mother.data.setBreedingStage(BreedingStage.PREGNANT);
        HorseData.Pregnancy pregnancy = new HorseData.Pregnancy();
        pregnancy.conceived = Instant.now().getEpochSecond();
        info.mother.data.setBreedingCooldown(ONE_DAY * 6 + ThreadLocalRandom.current().nextInt(ONE_DAY)); // 6 - 7 days
        pregnancy.partnerId = info.father.data.getId();
        info.mother.data.setPregnancy(pregnancy);
        this.plugin.getDatabase().updateHorse(info.mother.data);
        // Father needs to recover
        info.father.data.setBreedingStage(BreedingStage.RECOVERY);
        info.father.data.setBreedingCooldown(ONE_DAY);
        this.plugin.getDatabase().updateHorse(info.father.data);
        // Message
        String msg = "" + ChatColor.GOLD + info.mother.data.getMaskedName() + ChatColor.GOLD + " is now pregnant from " + info.father.data.getMaskedName() + ChatColor.GOLD + ".";
        breeder.sendMessage(msg);
        breeder.sendActionBar(msg);
        if (info.mother.data.getOwner() != null) {
            Player owner = this.plugin.getServer().getPlayer(info.mother.data.getOwner());
            if (owner != null && !owner.equals(breeder)) {
                owner.sendMessage(msg);
                owner.sendActionBar(msg);
            }
        }
    }

    private BreedInfo onBreed(SpawnedHorse parentEntityA, SpawnedHorse parentEntityB, Player breeder) throws BreedException {
        if (parentEntityA == null || parentEntityB == null) throw BreedException.Type.PARENT_NOT_CUSTOM.make();
        if (!Profession.VET.has(breeder)) throw BreedException.Type.BREEDER_NOT_VET.make();
        final SpawnedHorse mother, father;
        if (parentEntityA.data.getGender() == HorseGender.MARE && parentEntityB.data.getGender() == HorseGender.STALLION) {
            mother = parentEntityA;
            father = parentEntityB;
        } else if  (parentEntityA.data.getGender() == HorseGender.STALLION && parentEntityB.data.getGender() == HorseGender.MARE) {
            mother = parentEntityB;
            father = parentEntityA;
        } else {
            throw BreedException.Type.GENDER_MISMATCH.make();
        }
        if (mother.data.getAge() != HorseAge.ADULT) throw BreedException.Type.NOT_ADULT.make(mother);
        if (father.data.getAge() != HorseAge.ADULT) throw BreedException.Type.NOT_ADULT.make(father);
        if (mother.data.getBreedingStage() == BreedingStage.PREGNANT) throw BreedException.Type.ALREADY_PREGNANT.make(mother);
        if (mother.data.getBreedingStage() != BreedingStage.READY) throw BreedException.Type.NOT_READY.make(mother);
        if (father.data.getBreedingStage() != BreedingStage.READY) throw BreedException.Type.NOT_READY.make(father);
        return new BreedInfo(mother, father);
    }

    /**
     * Called by HorsePlugin once per second for every SpawnedHorse
     * unless the BreedingStage is READY.
     */
    void passSecond(SpawnedHorse spawned, long now) {
        final int cooldown = spawned.data.getBreedingCooldown();
        switch (spawned.data.getBreedingStage()) {
        case PREGNANT: {
            if (cooldown == 0) {
                spawned.data.setBreedingStage(BreedingStage.LABOR);
                Random random = ThreadLocalRandom.current();
                // 2 hours
                spawned.data.setBreedingCooldown(random.nextInt(3600) + random.nextInt(3600));
                this.plugin.getDatabase().updateHorse(spawned.data);
                Player owner = spawned.data.getOwningPlayer();
                if (owner != null) {
                    owner.sendMessage(ChatColor.GOLD + "Your mare " + spawned.data.getMaskedName() + ChatColor.GOLD + " looks like it's going into labor within the next two hours. Better keep a close eye on her.");
                    HorseEffects.friendJingle(this.plugin, owner);
                }
                return;
            }
            // Complications
            HorseData.Pregnancy pregnancy = spawned.data.getPregnancy();
            if (!pregnancy.flags.contains(HorseData.Pregnancy.Flag.OVERWEIGHT) && spawned.data.getBodyCondition().score >= 7) {
                pregnancy.flags.add(HorseData.Pregnancy.Flag.OVERWEIGHT);
                double dice = ThreadLocalRandom.current().nextDouble();
                boolean badLuck = dice < 0.25;
                this.plugin.getLogger().info("Horse `" + spawned.data.getStrippedName() + "` owned by " + spawned.data.getOwnerName(this.plugin) + " is overweight. Dice=" + (int)(dice * 100.0) + ", BadLuck=" + badLuck + ".");
                if (badLuck) {
                    pregnancy.flags.add(HorseData.Pregnancy.Flag.MISCARRIAGE);
                    // TODO: Laminitis
                }
                // Save
                this.plugin.getDatabase().updateHorse(spawned.data);
            }
            if (!pregnancy.flags.contains(HorseData.Pregnancy.Flag.UNDERWEIGHT) && spawned.data.getBodyCondition().score <= 4) {
                pregnancy.flags.add(HorseData.Pregnancy.Flag.UNDERWEIGHT);
                this.plugin.getDatabase().updateHorse(spawned.data);
                double dice = ThreadLocalRandom.current().nextDouble();
                boolean badLuck = dice < 0.25;
                this.plugin.getLogger().info("Horse `" + spawned.data.getStrippedName() + "` owned by " + spawned.data.getOwnerName(this.plugin) + " is underweight. Dice=" + (int)(dice * 100.0) + ", BadLuck=" + badLuck + ".");
                if (badLuck) {
                    pregnancy.flags.add(HorseData.Pregnancy.Flag.PREMATURE);
                }
                // Save
                this.plugin.getDatabase().updateHorse(spawned.data);
            }
            return;
        }
        case LABOR: {
            if (cooldown == 0) {
                HorseData.Pregnancy pregnancy = spawned.data.getPregnancy();
                spawned.data.setPregnancy(null);
                HorseData father = this.plugin.findHorse(pregnancy.partnerId);
                Player owner = spawned.data.getOwningPlayer();
                // Miscarriage
                if (father == null || pregnancy.flags.contains(HorseData.Pregnancy.Flag.MISCARRIAGE)) {
                    spawned.data.setBreedingStage(BreedingStage.RECOVERY);
                    spawned.data.setBreedingCooldown(ONE_DAY * 7);
                    this.plugin.getDatabase().updateHorse(spawned.data);
                    if (owner != null) {
                        owner.sendMessage(ChatColor.RED + "Your mare " + spawned.data.getMaskedName() + ChatColor.RED + " just had a miscarriage.");
                        HorseEffects.unfriendJingle(this.plugin, owner);
                    }
                    return;
                }
                // Finalize birth
                spawned.data.setBreedingStage(BreedingStage.NURTURE);
                spawned.data.setBreedingCooldown(ONE_DAY * 7);
                this.plugin.getDatabase().updateHorse(spawned.data);
                SpawnedHorse child = giveBirth(spawned, father);
                if (owner != null) {
                    HorseGender gender = child.data.getGender();
                    owner.sendMessage(ChatColor.GOLD + "Your mare " + spawned.data.getMaskedName() + ChatColor.GOLD + " just gave birth to a " + gender.humanName + ". Claim " + gender.pronoun() + " and give " + gender.pronoun() + " a name.");
                    owner.playSound(owner.getEyeLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.MASTER, 0.5f, 2.0f);
                }
            }
            return;
        }
        default:
            // Handle all other cases: RECOVERY, NURTURE.
            if (spawned.data.getBreedingCooldown() == 0) {
                spawned.data.setBreedingStage(BreedingStage.READY);
                this.plugin.getDatabase().updateHorse(spawned.data);
            }
        }
    }

    /**
     * Always succeeds.  Never returns null.
     */
    SpawnedHorse giveBirth(SpawnedHorse mother, HorseData father) {
        if (!mother.isPresent()) throw new IllegalStateException("mother has to be in memory to give birth");
        Player owner = mother.data.getOwningPlayer();
        HorseData child = new HorseData();
        child.randomize(this.plugin);
        child.setMotherId(mother.data.getId());
        child.setFatherId(father.getId());
        child.setAge(HorseAge.FOAL);
        Random random = ThreadLocalRandom.current();
        HorseData inherit = random.nextBoolean() ? mother.data : father;
        child.setBreed(inherit.getBreed());
        child.setColor(inherit.getColor());
        child.setMarkings(inherit.getMarkings());
        child.setJump(mother.data.getJump() * 0.5 + father.getJump() * 0.5);
        child.setSpeed(mother.data.getSpeed() * 0.5 + father.getSpeed() * 0.5);
        this.plugin.addHorse(child);
        return this.plugin.spawnHorse(child, mother.getEntity().getLocation());
    }
}