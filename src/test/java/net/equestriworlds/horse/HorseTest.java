package net.equestriworlds.horse;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.FileInputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.bukkit.Sound;
import org.junit.Assert;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public final class HorseTest {
    public void testHorseList() {
        HorseData horse = new HorseData();
        horse.setName("Epona");
        horse.setColor(HorseColor.random());
        horse.setMarkings(HorseMarkings.random());
        //horse.setOwner(UUID.randomUUID());
        HorseData horse2 = new HorseData();
        horse2.setName("Spirit");
        horse2.setColor(HorseColor.random());
        horse2.setMarkings(HorseMarkings.random());
        //horse2.setOwner(UUID.randomUUID());
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
    public void soundTest() throws java.io.IOException {
        java.io.PrintStream out = new java.io.PrintStream("target/sounds.txt");
        for (Sound sound: Sound.values()) {
            out.println(sound.name());
        }
        out.close();
    }

    @Test
    public void horseTestBreeds() throws java.io.IOException {
        java.io.PrintStream out = new java.io.PrintStream("target/breeds.html");
        out.print("<table border=1 style='text-align:right;'>");
        out.print("<tr><th>Breed</th><th>Colors</th><th>Markings</th></tr>");
        for (HorseBreed breed: HorseBreed.values()) {
            out.print("<tr>");
            out.print("<td style='padding:8px;'>");
            out.print(breed.humanName);
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            for (HorseColor color: HorseColor.values()) {
                if (!breed.colors.contains(color)) {
                    out.print("<span style='color:#aaaaaa;text-decoration:line-through;'>");
                    out.print(color.humanName);
                    out.print("</span>");
                } else {
                    out.print(color.humanName);
                }
                out.print("<br/>");
            }
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            for (HorseMarkings markings: HorseMarkings.values()) {
                if (!breed.markings.contains(markings)) {
                    out.print("<span style='color:#aaaaaa;text-decoration:line-through;'>");
                    out.print(markings.humanName);
                    out.print("</span>");
                } else {
                    out.print(markings.humanName);
                }
                out.print("<br/>");
            }
            out.print("</td>");
            out.print("</tr>");
        }
        out.println("</table>");
        out.close();
    }

    @Test
    public void tools() throws java.io.IOException {
        java.io.PrintStream out = new java.io.PrintStream("target/tools.html");
        out.print("<table border=1 style='text-align:right;'>");
        out.print("<tr><th>#</th><th>Tool</th><th>Activity</th><th>Appearance</th><th>Uses</th><th>Item</th><th>Lore</th></tr>");
        Yaml yaml = new Yaml();
        Map<String, Object> cfg = (Map<String, Object>)yaml.load(new FileInputStream("src/main/resources/items.yml"));
        int index = 1;
        for (Grooming.Tool tool: Grooming.Tool.values()) {
            Map<String, Object> sec = (Map<String, Object>)cfg.get(tool.key);
            List<String> lore;
            if (sec == null) {
                lore = Arrays.asList("N/A");
            } else {
                lore = (List<String>)sec.get("lore");
            }
            out.print("<tr>");
            out.print("<td style='padding:8px;'>");
            out.print("" + (index++));
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            out.print(tool.humanName);
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            out.print("" + tool.activity.name().toLowerCase());
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            out.print("" + tool.activity.maximum + "x" + tool.activity.appearance);
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            out.print("" + tool.uses);
            out.print("</td>");
            out.print("<td style='padding:8px;'>");
            out.print("" + tool.item.getData());
            out.print("</td>");
            out.print("<td style='padding:8px;text-align:left;color:purple;font-style:italic;font-family:monospace;'>");
            for (String l: lore) {
                out.print(l);
                out.print("<br/>");
            }
            out.print("</td>");
            out.print("</tr>");
        }
        out.println("</table>");
        out.close();
    }
}
