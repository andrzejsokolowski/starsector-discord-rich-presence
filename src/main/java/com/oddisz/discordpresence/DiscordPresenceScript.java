package com.oddisz.discordpresence;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;

/**
 * Runs every game frame, throttled by UPDATE_INTERVAL.
 *
 * Pushes rich presence to Discord with:
 *   details — commander name + flagship
 *   state   — star system, fleet size, credits
 *
 * runWhilePaused() returns true so the presence stays fresh in menus.
 */
public class DiscordPresenceScript implements EveryFrameScript {

    private static final Logger log = Logger.getLogger(DiscordPresenceScript.class);

    /** Seconds between Discord presence updates. */
    private static final float UPDATE_INTERVAL = 5f;

    private float timer = UPDATE_INTERVAL; // fire immediately on first advance()
    private boolean done = false;

    // ── EveryFrameScript ─────────────────────────────────────────────────────

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    @Override
    public void advance(float amount) {
        timer += amount;
        if (timer < UPDATE_INTERVAL) return;
        timer = 0f;

        try {
            pushPresence();
        } catch (Exception e) {
            // Never let an RPC failure affect gameplay
            log.warn("[DiscordPresence] Error updating presence: " + e.getMessage());
        }
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private void pushPresence() {
        if (Global.getSector() == null) return;
        if (Global.getSector().getPlayerPerson() == null) return;

        CampaignFleetAPI fleet = Global.getSector().getPlayerFleet();
        if (fleet == null) return;

        // ── Commander ────────────────────────────────────────────────────────
        String name  = Global.getSector().getPlayerPerson().getName().getFullName();
        int    level = Global.getSector().getPlayerPerson().getStats().getLevel();

        // ── Flagship ─────────────────────────────────────────────────────────
        String flagshipInfo = "";
        FleetMemberAPI flagship = fleet.getFlagship();
        if (flagship != null) {
            String shipName = flagship.getShipName();
            String hullName = flagship.getHullSpec().getHullName();
            flagshipInfo = shipName + " (" + hullName + ")";
        }

        // ── Location ─────────────────────────────────────────────────────────
        LocationAPI location = fleet.getContainingLocation();
        String systemName = (location != null && location.getName() != null)
                ? location.getName()
                : "Hyperspace";

        // ── Fleet size ───────────────────────────────────────────────────────
        int fleetSize = fleet.getFleetData().getMembersListCopy().size();

        // ── Credits ──────────────────────────────────────────────────────────
        long credits = (long) fleet.getCargo().getCredits().get();

        // ── Compose lines ────────────────────────────────────────────────────
        // details (bold): "Cmdr Alex Thornton  Lvl 8 | Onslaught (XIV Battleship)"
        // state:          "Corvus · 12 ships · 1,234,567 ¢"

        String details = "Cmdr " + name + "  Lvl " + level
                + (flagshipInfo.isEmpty() ? "" : " | " + flagshipInfo);

        String state = systemName
                + " · " + fleetSize + " ship" + (fleetSize == 1 ? "" : "s")
                + " · " + formatCredits(credits) + " ¢";

        DiscordPresenceManager.getInstance().updatePresence(details, state);
    }

    /** Formats a credit value with comma separators, e.g. "1,234,567". */
    private static String formatCredits(long amount) {
        String raw = Long.toString(amount);
        StringBuilder sb = new StringBuilder();
        int start = raw.length() % 3;
        if (start > 0) sb.append(raw, 0, start);
        for (int i = start; i < raw.length(); i += 3) {
            if (sb.length() > 0) sb.append(',');
            sb.append(raw, i, i + 3);
        }
        return sb.toString();
    }
}
