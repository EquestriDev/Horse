package net.equestriworlds.horse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * Extra horse data which are only applicable to spawned horses and
 * subject to sudden change, thus stored separately.
 */
@RequiredArgsConstructor @Getter @Setter
public final class HorseExtra {
    private final int horseId;
    public static final String EXTRA_COOLDOWNS = "cooldowns";
    private boolean needsSaving = false;
    // Extra Data
    private Map<String, Integer> cooldowns = null;
    private Breeding.Persistence pregnancy = null;
    private Crosstie.Persistence crosstie = null;
    private Grooming.Persistence grooming = null;
    private Health.Persistence health = null;

    // --- Loading

    void load(Map<String, String> data) {
        String cd = data.get(EXTRA_COOLDOWNS);
        if (cd != null) {
            Gson gson = new Gson();
            try {
                this.cooldowns = gson.fromJson(cd, new TypeToken<Map<String, Integer>>(){}.getType());
            } catch (Exception e) {
                e.printStackTrace();
                this.cooldowns = new HashMap<>();
            }
        } else {
            this.cooldowns = new HashMap<>();
        }
        this.pregnancy = parse(data.get(Breeding.Persistence.EXTRA_KEY), Breeding.Persistence.class);
        this.crosstie = parse(data.get(Crosstie.Persistence.EXTRA_KEY), Crosstie.Persistence.class);
        this.grooming = parse(data.get(Grooming.Persistence.EXTRA_KEY), Grooming.Persistence.class);
        this.health = parse(data.get(Health.Persistence.EXTRA_KEY), Health.Persistence.class);
    }

    private <T> T parse(String inp, Class<T> clazz, Supplier<T> dfl) {
        if (inp == null) return dfl.get();
        Gson gson = new Gson();
        try {
            return gson.fromJson(inp, clazz);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dfl.get();
    }

    private <T> T parse(String inp, Class<T> clazz) {
        if (inp == null) return null;
        Gson gson = new Gson();
        try {
            return gson.fromJson(inp, clazz);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // --- Cooldowns

    void passSecond(long now) {
        // Reduce all cooldowns
        for (Iterator<Map.Entry<String, Integer>> iter = this.cooldowns.entrySet().iterator(); iter.hasNext();) {
            Map.Entry<String, Integer> entry = iter.next();
            int value = entry.getValue();
            if (value > 1) {
                entry.setValue(value - 1);
            } else {
                iter.remove();
            }
        }
    }

    public void setCooldown(String name, int seconds) {
        if (name == null) throw new IllegalArgumentException("name cannot be null");
        if (seconds < 0) throw new IllegalArgumentException("seconds cannot be negative");
        if (seconds == 0) {
            this.cooldowns.remove(name);
        } else {
            this.cooldowns.put(name, seconds);
        }
    }

    public int getCooldown(String name) {
        Integer result = this.cooldowns.get(name);
        return result == null ? 0 : result;
    }

    void saveNow(HorsePlugin plugin) {
        this.needsSaving = false;
        Gson gson = new Gson();
        plugin.getDatabase().saveExtraData(this.horseId, EXTRA_COOLDOWNS, gson.toJson(this.cooldowns));
        plugin.getDatabase().saveExtraData(this.horseId, Breeding.Persistence.EXTRA_KEY, gson.toJson(this.cooldowns));
        plugin.getDatabase().saveExtraData(this.horseId, Crosstie.Persistence.EXTRA_KEY, gson.toJson(this.crosstie));
        plugin.getDatabase().saveExtraData(this.horseId, Grooming.Persistence.EXTRA_KEY, gson.toJson(this.grooming));
        plugin.getDatabase().saveExtraData(this.horseId, Health.Persistence.EXTRA_KEY, gson.toJson(this.health));
    }

    void save() {
        this.needsSaving = true;
    }
}
