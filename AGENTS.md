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

## Essentials

- id `discord_presence`, plugin `com.oddisz.discordpresence.DiscordPresenceModPlugin`, jar
  `jars/discord_presence.jar`. Uses the vendored jagrosh `discordipc` library.
- Build: `build.bat` or Gradle `jar` (see `SETUP.txt`). `libs/starsector/` holds the game's API
  jars and is ignored by Git: never commit them.
- The player sets their own Discord application id in `data/config/discord_presence_settings.json`.
- Forum topic 35392 in `discord_presence.version`.
