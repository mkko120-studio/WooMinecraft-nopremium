package com.plugish.woominecraft;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WooCommand implements CommandExecutor {

	public WooMinecraft plugin = WooMinecraft.getInstance();
	private static final String chatPrefix = ChatColor.translateAlternateColorCodes('&',"&5[&fWooMinecraft&5] ");

	/**
	 * Command for manually refreshing order info.
	 * @param sender Command sender
	 * @param command Executed command
	 * @param label Command's label
	 * @param args Array of each argument typed with command
	 * @return True if command executed, otherwise false.
	 */

	@Override
	public boolean onCommand( CommandSender sender, Command command, String label, String[] args) {
		if (args.length == 1) {
			//Check if sender has op or permission to execute command
			if (sender.hasPermission("woo.admin") || sender.isOp()) {
				// Write here every command for permission woo.admin
				if (args[0].equalsIgnoreCase("check")) {
					try {
						String msg;
						boolean checkResults = plugin.check();

						if ( !checkResults ) {
							msg = chatPrefix + " " + plugin.getLang( "general.none_avail" );
						} else {
							msg = chatPrefix + " " + plugin.getLang( "general.processed" );
						}
						sender.sendMessage( msg );
					} catch ( Exception e ) {
						plugin.getLogger().warning(e.getMessage());
						e.printStackTrace();
					}
				} else {
					//If command is not /woo check
					sender.sendMessage("Usage: /woo check");
					return true;
				}
			} else {
			sender.sendMessage(chatPrefix + " " + plugin.getLang("general.not_authorized").replaceAll("&", String.valueOf(ChatColor.COLOR_CHAR)));
			}
		} else {
			//If you want to add multiple subcommands please replace this with help page.
			sender.sendMessage(chatPrefix + " " + plugin.getLang("general.avail_commands") + ": /woo check");
			return true;
		}
		//Successfully executed command
		return true;
	}
}
