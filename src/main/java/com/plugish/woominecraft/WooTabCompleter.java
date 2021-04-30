package com.plugish.woominecraft;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class WooTabCompleter implements TabCompleter {

    /**
     * Class for tab completion for user friendly interface.
     * @param sender Command sender.
     * @param command Command the Sender send
     * @param label Command's label
     * @param args Each word that player wrote after label
     * @return List of strings that is given to player ofter clicking TAB.
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        ArrayList<String> list = new ArrayList<>();

        if (args.length == 1) {
            /*
            if you will add any subcommand enter it right here, as follows:
            list.add("subcommand")
             */
            list.add("check");
        }

        return list;
    }
}
