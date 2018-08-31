package net.equestriworlds.horse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Horse;
import org.junit.Assert;
import org.junit.Test;

public final class HorseTest {
    @Test
    public void testHorseList() {
        EquestriHorse horse = new EquestriHorse();
        horse.setName("Epona");
        horse.setColor(Horse.Color.BROWN);
        horse.setStyle(Horse.Style.WHITEFIELD);
        horse.setOwner(UUID.randomUUID());
        EquestriHorse horse2 = new EquestriHorse();
        horse2.setName("Spirit");
        horse2.setColor(Horse.Color.WHITE);
        horse2.setStyle(Horse.Style.WHITE);
        horse2.setOwner(UUID.randomUUID());
        List<EquestriHorse> list = Arrays.asList(horse, horse2);
        Gson gson = new Gson();
        String json = gson.toJson(list);
        Type type = new TypeToken<List<EquestriHorse>>(){}.getType();
        List<EquestriHorse> list2 = gson.fromJson(json, type);
        String json2 = gson.toJson(list2);
        System.out.println("---");
        System.out.println(list);
        System.out.println(list2);
        System.out.println("---");
        System.out.println(json);
        System.out.println(json2);
        Assert.assertEquals(list, list2);
        Assert.assertEquals(json, json2);
    }
}
