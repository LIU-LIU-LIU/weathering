package cc.ahaly.weathering;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class WeatheringTabCompleter implements TabCompleter {

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("weathering")) {
            if (args.length == 1) {
                return Arrays.asList("query", "list", "draw", "reset", "remind");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("draw")) {
                return Arrays.asList("events", "weathers", "clear");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("query")) {
                return Arrays.asList("mca");
            } else if (args.length >= 2 && args[0].equalsIgnoreCase("query") && args[1].equalsIgnoreCase("mca")) {
                if (args.length == 3) {
                    return Arrays.asList("<x>");
                } else if (args.length == 4) {
                    return Arrays.asList("<z>");
                }
            }
        }
        return new ArrayList<>();
    }
}
