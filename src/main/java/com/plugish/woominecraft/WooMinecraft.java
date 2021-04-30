/*
 * Woo Minecraft Donation plugin
 * Author:	   Jerry Wood
 * Author URI: http://plugish.com
 * License:	   GPLv2
 * 
 * Copyright 2014 All rights Reserved
 * 
 */
package com.plugish.woominecraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.plugish.woominecraft.pojo.Order;
import com.plugish.woominecraft.pojo.WMCPojo;
import com.plugish.woominecraft.pojo.WMCProcessedOrders;
import okhttp3.*;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class WooMinecraft extends JavaPlugin {

	private static WooMinecraft instance;
	public static WooMinecraft getInstance() {
		return instance;
	}

	private YamlConfiguration l10n;
	public YamlConfiguration config;

	@Override
	public void onEnable() {
		instance = this;
		config = (YamlConfiguration) instance.getConfig();

		// Save the default config.yml
		try{
			saveDefaultConfig();
		} catch ( IllegalArgumentException e ) {
			getLogger().warning( e.getMessage() );
		}

		String lang = getConfig().getString("lang");
		if ( lang == null ) {
			getLogger().warning( "No default l10n set, setting to english." );
		}

		// Load the commands.
		getCommand( "woo" ).setExecutor(new WooCommand());

		// Log when plugin is initialized.
		getLogger().info( this.getLang( "log.com_init" ));

		// Setup the scheduler
		BukkitRunner scheduler = new BukkitRunner(instance);
		int interval = config.getInt("update_interval");
		scheduler.runTaskTimerAsynchronously( instance, (long)interval * 20, (long)interval * 20 );

		// Log when plugin is fully enabled ( setup complete ).
		getLogger().info( this.getLang("log.enabled"));
	}

	@Override
	public void onDisable() {
		// Log when the plugin is fully shut down.
		getLogger().info( this.getLang("log.com_init"));
	}

	/**
	 * Helper method to get localized strings
	 *
	 * Much better than typing this.l10n.getString...
	 * @param path Path to the config var
	 * @return String
	 */
	String getLang(String path) {
		if ( null == this.l10n ) {
			LangSetup lang = new LangSetup( instance );
			l10n = lang.loadConfig();
		}
		return this.l10n.getString( path );
	}

	/**
	 * Validates the basics needed in the config.yml file.
	 *
	 * Multiple reports of user configs not having keys etc... so this will ensure they know of this
	 * and will not allow checks to continue if the required data isn't set in the config.
	 *
	 * @throws Exception Reason for failing to validate the config.
	 */
	private void validateConfig() throws Exception {

		if (config.getString( "url" ).length() < 1) {
			throw new Exception( "Server URL is empty, check config." );
		} else if (config.getString( "url" ).equals( "https://playground.dev" ) ) {
			throw new Exception( "URL is still the default URL, check config." );
		} else if (config.getString( "key" ).length() < 1) {
			throw new Exception( "Server Key is empty, this is insecure, check config." );
		}
	}

	/**
	 * Gets the site URL
	 *
	 * @return URL
	 * @throws Exception Why the URL failed.
	 */
	private URL getSiteURL() throws Exception {
		return new URL( config.getString( "url" ) + "/wp-json/wmc/v1/server/" + config.getString( "key" ) );
	}

	/**
	 * Checks all online players against the
	 * website's database looking for pending donation deliveries
	 *
	 * @return boolean
	 * @throws Exception Why the operation failed.
	 */
	boolean check() throws Exception {

		// Make 100% sure the config has at least a key and url
		this.validateConfig();

		// Contact the server.
		String pendingOrders = getPendingOrders();

		// Server returned an empty response, bail here.
		if ( pendingOrders.isEmpty() ) {
			return false;
		}

		// Create new object from JSON response.
		Gson gson = new GsonBuilder().create();
		// Log if debugging is enabled.
		wmc_log( pendingOrders );
		WMCPojo wmcPojo = gson.fromJson( pendingOrders, WMCPojo.class );
		List<Order> orderList = wmcPojo.getOrders();


		// Validate we can indeed process what we need to.
		if ( wmcPojo.getData() != null ) {
			// We have an error, so we need to bail.
			wmc_log( "Code:" + wmcPojo.getCode(), 3 );
			throw new Exception( wmcPojo.getMessage() );
		}

		if ( orderList == null || orderList.isEmpty() ) {
			wmc_log( "No orders to process.", 2 );
			return false;
		}

		// foreach ORDERS in JSON feed
		List<Integer> processedOrders = new ArrayList<>();
		if (isDebug()) {
			wmc_log("foreach with orders");
		}
		for ( Order order : orderList ) {
			Player player = Bukkit.getServer().getPlayerExact(order.getPlayer());
			if ( player == null ) {
				// Offline player support
				wmc_log("Executing order for CRACKED player: " + order.getPlayer());
				wmc_log("This can cause some problems so please be aware of that", 2);
				for (String command: order.getCommands()) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
					wmc_log("Dispatched command: " + command);
				}
			} else {
				// World whitelisting.
				if (getConfig().isSet("whitelist-worlds")) {
					List<String> whitelistWorlds = config.getStringList("whitelist-worlds");
					String playerWorld = player.getWorld().getName();
					if (!whitelistWorlds.contains(playerWorld)) {
						wmc_log("Player " + player.getDisplayName() + " was in world " + playerWorld + " which is not in the white-list, no commands were ran.");
						continue;
					}
				}

				// Walk over all commands and run them at the next available tick.
				for (String command : order.getCommands()) {
					BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
					wmc_log("initiating command execution...");
					scheduler.scheduleSyncDelayedTask(instance, () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), command), 20L);
					wmc_log("executed command: " + command);
				}
			}
				wmc_log( "Adding item to list - " + order.getOrderId() );
				processedOrders.add(order.getOrderId());
				wmc_log( "Processed length is " + processedOrders.size() );

		}


		// If it's empty, we skip it.
		if ( processedOrders.isEmpty() ) {
			return false;
		}

		// Send/update processed orders.
		return sendProcessedOrders( processedOrders );
	}

	/**
	 * Sends the processed orders to the site.
	 *
	 * @param processedOrders A list of order IDs which were processed.
	 * @return boolean
	 */
	private boolean sendProcessedOrders( List<Integer> processedOrders ) throws Exception {
		// Build the GSON data to send.
		Gson gson = new Gson();
		WMCProcessedOrders wmcProcessedOrders = new WMCProcessedOrders();
		wmcProcessedOrders.setProcessedOrders( processedOrders );
		String orders = gson.toJson( wmcProcessedOrders );

		// Setup the client.
		OkHttpClient client = new OkHttpClient();

		// Process stuffs now.
		RequestBody body = RequestBody.create(orders, MediaType.parse("application/json; charset=utf-8"));
		Request request = new Request.Builder().url(getSiteURL()).post(body).build();
		Response response = client.newCall(request).execute();

		// If the body is empty we can do nothing.
		if (response.body() == null) {
			throw new Exception("Received empty response from your server, check connections.");
		}

		// Get the JSON reply from the endpoint.
		WMCPojo wmcPojo = gson.fromJson( response.body().string(), WMCPojo.class );
		if (null != wmcPojo.getCode()) {
			wmc_log( "Received error when trying to send post data:" + wmcPojo.getCode(), 3 );
			throw new Exception( wmcPojo.getMessage() );
		}

		return true;
	}

	/**
	 * If debugging is enabled.
	 *
	 * @return boolean
	 */
	private boolean isDebug() {
		return getConfig().getBoolean("debug");
	}

	/**
	 * Gets pending orders from the WordPress JSON endpoint.
	 *
	 * @return String
	 * @throws Exception On failure.
	 */
	private String getPendingOrders() throws Exception {
		InputStream is = this.getSiteURL().openStream();
		BufferedReader in;
		try {
			// Grab JSON data from web server.
			in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		} catch( Exception e ) {
			String key = config.getString("key");
			String msg = e.getMessage();
			assert key != null;
			if (msg.contains(key)) {
				msg = msg.replaceAll(key,"privateKey" );
			}
			throw new Exception(msg);
		}

		StringBuilder buffer = new StringBuilder();

		// Walk over each line of the response.
		String line;
		while ((line = in.readLine()) != null) {
			buffer.append(line);
		}

		// Close connection to webpage.
		in.close();

		return buffer.toString();
	}

	/**
	 * Function for logging stuff.
	 *
	 * @param message The message to log.
	 */
	private void wmc_log(String message) {
		this.wmc_log( message, 1 );
	}

	/**
	 * Function for logging stuff with log level options.
	 *
	 * @param message The message to log.
	 * @param level The level to log it at.
	 */
	private void wmc_log(String message, Integer level) {

		if (!isDebug()) {
			return;
		}

		switch (level) {
			case 1:
				this.getLogger().info( message );
				break;
			case 2:
				this.getLogger().warning( message );
				break;
			case 3:
				this.getLogger().severe( message );
				break;
		}
	}
}
