# Atlas ModCheck

Eigenständiges Paper-Plugin (26.3) – **nur** die Client- und Mod-Erkennung aus AtlasAC, ohne Anticheat. Keine Abhängigkeiten (kein PacketEvents nötig).

- Client-Brand und Mod-Channels, unscharf verglichen (`autototem` erkennt auch `AuToTootem_x30975`)
- Translation-Key-Probe per unsichtbarem Schild (z.B. Meteor)
- Anti-Detection-Mods: erst Kick mit Hinweis, beim nächsten Mal Ban
- Strafen pro Mod: `ban`, `ipban`, `kick` (3. Kick in 24h = Ban), `alert`
- Eigene Bans mit Kick-/Ban-Screen und Fall-ID (`bans.yml`)

## Befehle (atlasmodcheck.admin)
`/modcheck info <Spieler>` · `/modcheck check <Spieler>` · `/modcheck test <Name>` · `/modcheck unban <Spieler>` · `/modcheck reload`

## Rechte
- `atlasmodcheck.alerts` – Meldungen sehen (OP)
- `atlasmodcheck.bypass.mods` – darf mit verbotenen Mods joinen, wird aber gemeldet (niemand)
- `atlasmodcheck.bypass` – wird gar nicht geprüft (niemand)

Nicht zusammen mit AtlasAC nutzen – AtlasAC enthält die Mod-Erkennung bereits.
