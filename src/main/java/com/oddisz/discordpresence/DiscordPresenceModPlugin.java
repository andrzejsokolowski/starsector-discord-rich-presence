package com.oddisz.discordpresence;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.apache.log4j.Logger;

/**
 * Entry point for the Discord Rich Presence mod.
 *
 * Hooks:
 *   onApplicationLoad  – connects to Discord IPC once the app starts
 *   onGameLoad         – registers the per-frame update script into the sector
 */
public class DiscordPresenceModPlugin extends BaseModPlugin {

    private static final Logger log = Logger.getLogger(DiscordPresenceModPlugin.class);

    @Override
    public void onApplicationLoad() throws Exception {
        log.info("[DiscordPresence] Application loaded, connecting to Discord...");
        DiscordPresenceManager.getInstance().connect();
    }

    @Override
    public void onGameLoad(boolean newGame) {
        log.info("[DiscordPresence] Game loaded, registering update script.");
        // Reset the Discord timer so it counts from when this session was loaded
        DiscordPresenceManager.getInstance().startTimer();
        // addTransientScript so it's removed automatically when the sector is gone
        Global.getSector().addTransientScript(new DiscordPresenceScript());
    }
}
