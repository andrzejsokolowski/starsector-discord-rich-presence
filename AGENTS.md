# Discord Rich Presence

Shows the commander name, level, credits and flagship as a Discord Rich Presence status. These
instructions apply to this repository, including when it is opened without the parent mod
workspace. The workspace `AGENTS.md` one folder up governs releases, packaging, Git identity and
communication.

## Notion

Tasks and history: the **Discord Rich Presence board** in Andrzej's Notion, under
**Starsector mods** (`collection://8379aff0-c822-4a4e-8b2d-e1f0f4ba0e3c`). Working rules and game
API notes: **Claude Information** (`collection://7bf9b1d6-b9e5-4b62-87f1-757bbaa51919`), starting
with "Start here: working on Starsector mods through Notion". Read the board before working, give
every task a ticket (ticket ids start with DRP) and keep it current. The Notion tools are
deferred: load them with ToolSearch first.

Update the board during the work, not only at release time. Before changing files, put the
message on its ticket: a new request gets a new ticket (In progress), and a problem Andrzej
reports on Needs Human Test work moves that ticket to Changes Requested with the notes quoted.
Before ending the turn, update the ticket's body (What was done, How, Commits, Test list) and
its Status, Version and End date. Read the board with `notion-query-data-sources` mode `view`
on `view://3fd20ca7-5d20-814f-bec1-000ccdf16353` or `notion-fetch` a ticket. SQL and rows mode share a
small quota that runs out. The workspace `AGENTS.md` has the full routine.

## Essentials

- id `discord_presence`, plugin `com.oddisz.discordpresence.DiscordPresenceModPlugin`, jar
  `jars/discord_presence.jar`. Uses the vendored jagrosh `discordipc` library.
- Build: `build.bat` or Gradle `jar` (see `SETUP.txt`). `libs/starsector/` holds the game's API
  jars and is ignored by Git: never commit them.
- The player sets their own Discord application id in `data/config/discord_presence_settings.json`.
- Forum topic 35392 in `discord_presence.version`.
