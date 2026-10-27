# AtlasAC – Atlas AntiCheat

Paper-Plugin (26.3) mit Grim-artiger Packet-Analyse, starken PvP-Checks, Anti-XRay-Alerts und Anti-Dupe.

## Voraussetzungen
- Paper 26.3, Java 25
- [PacketEvents](https://modrinth.com/plugin/packetevents) als Plugin auf dem Server (passend zur MC-Version)

## Features
**PvP**
- Reach (Lag-Compensation über Positions-Historie + Ping, Interaction-Range-Attribut, Speere ausgenommen)
- Hitbox (Blickstrahl muss die Hitbox treffen)
- KillAura: Multi-Target im selben Client-Tick, Treffer durch Wände, Angriff ohne Swing
- Aim: Sensitivity-GCD, Modulo-360-Snaps, Aim-Lock (zu konstanter Ziel-Offset)
- AutoClicker: CPS-Limit + Klick-Konsistenz
- Velocity (Anti-Knockback), Criticals (Packet-Crits)

**Movement (Prediction)**
- Gravity-Vorhersage (Vanilla-Physik), Luft-Speed-Vorhersage, Jump-Höhe
- GroundSpoof/NoFall (Fallschaden wird nachträglich angewendet)
- Timer (Balance über Client-Tick-End-Packets)

**Gepatchte Bypass-Techniken**
- Entity-Collision-Speed (Grim „Collide“): keine Pauschal-Ausnahme mehr neben Entitäten, nur Vanilla-Push (0.05/Entität)
- Hurt-Time-Ausnahme verkürzt auf das Knockback-Fenster (vorher 30 Ticks freie Bewegung nach Treffern)
- Velocity: Projektion auf die Knockback-Richtung – erkennt Teil-Multiplikatoren, Reversal, Strafe, verzögerten KB; Jump-Reset bleibt legit
- Packet-Criticals / Mini-Hops (Crit dicht am Boden < 5 Luft-Ticks)
- Mehrere Bewegungs-Packets pro Tick (Silent-Rotations, VanillaSpeed, Packet-Crits, Bursts)
- Nuker / PacketMine (mehrere Abbau-Starts pro Tick)
- NoRotate (Server-Rotation nach Teleport ignoriert)
- NoSlow (Item benutzen, Schild, Schleichen inkl. Swift Sneak)
- Phase / VClip / ClickTP
- Jesus / gespooftes onGround über Flüssigkeiten
- Scaffold / Grim-Place: Airplace, Platzieren an Flüssigkeit, Reichweite, Blick trifft Block nicht
- Silent-Aura-Snap (großer Yaw-Sprung aus dem Stillstand direkt vor dem Schlag)
- Ping-Spoofing: Lag-Compensation auf 300 ms gedeckelt

**Mod-Erkennung** – Client-Brand und Mod-Channels werden unscharf mit einer Liste verglichen (`autototem` erkennt auch `AuToTootem_x30975`), dazu eine Translation-Key-Probe per unsichtbarem Schild. Meteor, Wurst, Baritone, LiquidBounce und weitere Hack-Clients: 30 Tage Ban; Freecam, XRay, AutoTotem, ESP: 7 Tage. Anti-Detection-Mods (keine Antwort auf die Probe): erst Kick mit Aufforderung, den Mod zu entfernen, beim nächsten Mal 30 Tage Ban. Strafe pro Mod frei einstellbar (`mod-detection.mods`), eigene Namen einfach ergänzen, testen mit `/atlas mods test <Name>`.

**Anti-XRay** – Alert, wenn in einem Zeitfenster zu viele Erze einer Gruppe abgebaut werden (inkl. Erz/Stein-Verhältnis). Einstellbar per GUI (`/atlas xray`) oder `config.yml`.

**Anti-Dupe** – jedes nicht stapelbare Item bekommt eine versteckte ID. Gleiche ID an zwei Orten gleichzeitig → Kopie wird gelöscht, Alert ans Team, Spieler wird geflaggt (Standard: Kick).

**Strafsystem** – eigener Kick-/Ban-Screen („Kicked by Atlas AntiCheat“ / „Banned by Atlas AntiCheat“ mit Fall-ID), Ban, Tempban, IP-Ban, Temp-IP-Ban, Mute, Freeze, Historie, Alt-Account-Alerts über die IP. Atlas kickt ab Werk bei jedem Check und bannt nach 3 Kicks in 24h automatisch für 7 Tage. Wer eingefroren den Server verlässt, wird 30 Tage gebannt. Daten liegen standardmäßig lokal in `plugins/AtlasAC/data.yml`; per `storage.type` auch SQLite, MySQL, MariaDB oder PostgreSQL (Treiber werden automatisch geladen, bestehende data.yml wird beim Umstieg migriert).

**Staff-Modus** (`/staff`) – sichert Inventar, Position und Spielmodus (auch bei Absturz), Vanish, Fliegen, unverwundbar und Werkzeuge: Einfrieren, Inventar/Enderkiste ansehen, Spieler-Info, Zufalls-Teleport, Vanish an/aus, Admin-GUI. Nochmal `/staff` → alles zurück und Teleport zur Startposition.

**Admin-GUI** (`/atlas gui`) – alle Spieler mit Ping, Client-Brand, Version und Flags; pro Spieler: Teleport, Freeze, Inventar, Enderkiste, Flags + XRay-Statistik, Historie (aktive Strafen per Shift-Rechtsklick aufheben) und Schnell-Strafen (`staff-gui.presets`).

## Befehle
| Befehl | Recht |
|---|---|
| `/atlas alerts` | atlasac.command + atlasac.alerts |
| `/atlas vl <Spieler>` | atlasac.command |
| `/atlas xray` · `/atlas xray stats <Spieler>` · `/atlas xray reset <Spieler>` | atlasac.command (Ändern im GUI: atlasac.admin) |
| `/atlas dupe scan` · `/atlas dupe check` | atlasac.command |
| `/atlas reload` | atlasac.admin |
| `/atlas freeze <Spieler>` (nochmal = auftauen) | atlasac.freeze |
| `/atlas kick <Spieler> [Grund]` | atlasac.kick |
| `/atlas ban <Spieler> [Dauer] [Grund]` · `/atlas tempban <Spieler> <Dauer> [Grund]` | atlasac.ban / atlasac.tempban |
| `/atlas ipban <Spieler\|IP> [Dauer] [Grund]` · `/atlas tempipban …` | atlasac.ipban / atlasac.tempipban |
| `/atlas unban <Spieler> [-ip]` · `/atlas unbanip <Spieler\|IP>` | atlasac.unban / atlasac.unbanip |
| `/atlas mute` · `/atlas tempmute` · `/atlas unmute` | atlasac.mute / tempmute / unmute |
| `/atlas history <Spieler>` | atlasac.history |
| `/staff` · `/staff vanish` · `/staff gui` | atlasac.staff (Vanish sehen: atlasac.vanish.see, fremde Inventare bearbeiten: atlasac.staff.invedit) |
| `/atlas gui [Spieler]` | atlasac.gui |
| `/atlas mods check\|info <Spieler>` · `/atlas mods test <Name>` | atlasac.admin |
| `/atlas freezecmd add\|remove\|list <Befehl>` | atlasac.admin |

Dauer-Format: `30m`, `12h`, `7d`, `2w`, `1mo`, `1y`, kombinierbar (`1d12h`) oder `permanent`.

`atlasac.bypass` – Spieler wird nicht geprüft (Standard: niemand).

## Hinweise
- Alle Checks kicken ab Werk (`punish: true`), Movement-Checks setzen zusätzlich zurück. Eskalation zum Tempban unter `punishment.auto-ban`.
- Movement-Checks brauchen 1.21.2+-Clients (Tick-End-Packets); ältere Clients über ViaVersion werden davon ausgenommen.
- Die Prediction ist eine vereinfachte Vanilla-Physik, kein vollständiger Grim-Simulator. Grenzwerte nach ersten Tests in der `config.yml` nachjustieren.
- Anti-Dupe prüft Online-Inventare, Enderkisten, offene Container und Boden-Items – nicht unbeobachtete Kisten. Stapelbare Items bekommen keine ID, weil sie sonst nicht mehr stacken.

## Build
`gradle build` – das JAR landet in `build/libs/`. GitHub Actions baut bei jedem Push automatisch (Artefakt "AtlasAC"), Tags `v*` erzeugen ein Release.
