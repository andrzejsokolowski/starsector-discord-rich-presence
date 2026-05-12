package com.oddisz.discordpresence;

import com.fs.starfarer.api.Global;
import com.jagrosh.discordipc.IPCClient;
import com.jagrosh.discordipc.IPCListener;
import com.jagrosh.discordipc.entities.RichPresence;
import com.jagrosh.discordipc.entities.User;
import org.apache.log4j.Logger;

/**
 * Singleton that owns the Discord IPC connection.
 *
 * All calls are guarded so that if Discord is not running (or the app ID is
 * wrong) the game continues normally — we never let an RPC error propagate.
 */
public class DiscordPresenceManager {

    private static final Logger log = Logger.getLogger(DiscordPresenceManager.class);

    /** Settings key in data/config/discord_presence_settings.json */
    private static final String SETTINGS_FILE = "data/config/discord_presence_settings.json";

    private static DiscordPresenceManager instance;

    private IPCClient client;
    private boolean connected = false;

    /** Fixed timestamp for the current session — set once on connect/game-load so
     *  the elapsed timer in Discord counts up steadily instead of resetting on every
     *  presence update. */
    private java.time.OffsetDateTime sessionStart;

    // Sends presence updates on a daemon thread so the game's main thread is
    // never blocked by IPC I/O, even if the pipe is momentarily busy.
    private final java.util.concurrent.ExecutorService sender =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DiscordRPC-Sender");
                t.setDaemon(true);
                return t;
            });

    // ── Singleton ────────────────────────────────────────────────────────────

    public static DiscordPresenceManager getInstance() {
        if (instance == null) {
            instance = new DiscordPresenceManager();
        }
        return instance;
    }

    private DiscordPresenceManager() {}

    // ── Connect ──────────────────────────────────────────────────────────────

    /**
     * Connects to Discord IPC on a daemon thread so the game boot is never
     * blocked if Discord is closed or slow to respond.
     */
    public void connect() {
        final long appId;
        try {
            String raw = Global.getSettings()
                    .loadJSON(SETTINGS_FILE)
                    .getString("applicationId");

            if (raw == null || raw.isEmpty() || raw.contains("YOUR_DISCORD")) {
                log.warn("[DiscordPresence] No valid applicationId in "
                        + SETTINGS_FILE + " — rich presence disabled.");
                return;
            }
            appId = Long.parseLong(raw.trim());
        } catch (Exception e) {
            log.warn("[DiscordPresence] Could not read settings: " + e.getMessage());
            return;
        }

        client = new IPCClient(appId);
        client.setListener(new IPCListener() {
            @Override
            public void onReady(IPCClient client) {
                connected = true;
                log.info("[DiscordPresence] Connected to Discord.");
                // Start the timer for the main-menu session
                startTimer();
                updatePresence("In the Main Menu", "Starsector");
            }

            @Override
            public void onDisconnect(IPCClient client, Throwable t) {
                connected = false;
                log.warn("[DiscordPresence] Lost connection to Discord: " + t.getMessage());
            }

            // ── Unused activity hooks ────────────────────────────────────────
            @Override
            public void onActivityJoin(IPCClient client, String secret) {}
            @Override
            public void onActivitySpectate(IPCClient client, String secret) {}
            @Override
            public void onActivityJoinRequest(IPCClient client, String secret, User user) {}
        });

        Thread t = new Thread(() -> {
            try {
                client.connect();
            } catch (Exception e) {
                log.warn("[DiscordPresence] Could not connect to Discord "
                        + "(is Discord running?): "
                        + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }, "DiscordRPC-Connect");
        t.setDaemon(true);
        t.start();
    }

    // ── Update presence ──────────────────────────────────────────────────────

    // Art asset keys — must match exactly what you named them in the Discord
    // Developer Portal under Rich Presence → Art Assets.
    private static final String IMG_LARGE       = "orbitalbombardment";
    private static final String IMG_LARGE_TEXT  = "Starsector";
    private static final String IMG_SMALL       = "s_icon64";
    private static final String IMG_SMALL_TEXT  = "Starsector";

    /**
     * Resets the elapsed timer shown in Discord.
     * Call once when a new session begins (e.g. game loaded).
     */
    public void startTimer() {
        this.sessionStart = java.time.OffsetDateTime.now();
    }

    /**
     * @param details  Bold line shown in the presence card
     * @param state    Smaller line below details
     */
    public void updatePresence(final String details, final String state) {
        if (!connected || client == null) return;
        // Capture the timestamp so the lambda always uses the value current at
        // submission time, even if startTimer() is called again concurrently.
        final java.time.OffsetDateTime ts = sessionStart;
        sender.submit(() -> {
            try {
                RichPresence.Builder builder = new RichPresence.Builder()
                        .setDetails(details)
                        .setState(state)
                        .setLargeImage(IMG_LARGE, IMG_LARGE_TEXT)
                        .setSmallImage(IMG_SMALL, IMG_SMALL_TEXT);
                if (ts != null) {
                    builder.setStartTimestamp(ts);
                }
                client.sendRichPresence(builder.build());
            } catch (Exception e) {
                log.warn("[DiscordPresence] Failed to update presence: " + e.getMessage());
            }
        });
    }

    // ── Disconnect ───────────────────────────────────────────────────────────

    public void disconnect() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {}
            connected = false;
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
