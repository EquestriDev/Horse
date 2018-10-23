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
        HorseData horse = new HorseData();
        horse.setName("Epona");
        horse.setColor(HorseColor.random());
        horse.setMarkings(HorseMarkings.random());
        horse.setOwner(UUID.randomUUID());
        HorseData horse2 = new HorseData();
        horse2.setName("Spirit");
        horse2.setColor(HorseColor.random());
        horse2.setMarkings(HorseMarkings.random());
        horse2.setOwner(UUID.randomUUID());
        List<HorseData> list = Arrays.asList(horse, horse2);
        Gson gson = new Gson();
        String json = gson.toJson(list);
        Type type = new TypeToken<List<HorseData>>() { }.getType();
        List<HorseData> list2 = gson.fromJson(json, type);
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

    @Test
    public void horseTestBreeds() throws java.io.IOException {
        java.io.PrintStream out = new java.io.PrintStream("target/breeds.html");
        out.print("<table border=1 style='text-align:right;'>");
        out.print("<tr><th>Breed</th><th>Colors</th><th>Markings</th></tr>");
        for (HorseBreed breed: HorseBreed.values()) {
            out.print("<tr>");
            out.print("<td>");
            out.print(breed.humanName);
            out.print("</td>");
            out.print("<td>");
            if (breed.colors.size() == HorseColor.values().length) {
                out.print("<span style='background-color:aqua;'>All</span>");
            } else {
                for (HorseColor color: breed.colors) {
                    out.print(color.humanName);
                    out.print("<br/>");
                }
            }
            out.print("</td><td>");
            if (breed.markings.size() == HorseMarkings.values().length) {
                out.print("<span style='background-color:lime;'>All</span>");
            } else {
                for (HorseMarkings markings: breed.markings) {
                    out.print(markings.humanName);
                    out.print("<br/>");
                }
            }
            out.print("</td>");
            out.print("</tr>");
        }
        out.println("</table>");
        out.close();
    }
}
