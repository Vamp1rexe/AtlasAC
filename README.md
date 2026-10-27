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

**Anti-XRay** – Alert, wenn in einem Zeitfenster zu viele Erze einer Gruppe abgebaut werden (inkl. Erz/Stein-Verhältnis). Einstellbar per GUI (`/atlas xray`) oder `config.yml`.

**Anti-Dupe** – jedes nicht stapelbare Item bekommt eine versteckte ID. Gleiche ID an zwei Orten gleichzeitig → Kopie wird gelöscht, Alert ans Team, Spieler wird geflaggt (Standard: Kick).

## Befehle
| Befehl | Recht |
|---|---|
| `/atlas alerts` | atlasac.command + atlasac.alerts |
| `/atlas vl <Spieler>` | atlasac.command |
| `/atlas xray` · `/atlas xray stats <Spieler>` · `/atlas xray reset <Spieler>` | atlasac.command (Ändern im GUI: atlasac.admin) |
| `/atlas dupe scan` · `/atlas dupe check` | atlasac.command |
| `/atlas reload` | atlasac.admin |

`atlasac.bypass` – Spieler wird nicht geprüft (Standard: niemand).

## Hinweise
- Strafe ist standardmäßig ein Kick (`punishment.commands`), Bans gibt es nicht automatisch.
- Movement-Checks brauchen 1.21.2+-Clients (Tick-End-Packets); ältere Clients über ViaVersion werden davon ausgenommen.
- Die Prediction ist eine vereinfachte Vanilla-Physik, kein vollständiger Grim-Simulator. Grenzwerte nach ersten Tests in der `config.yml` nachjustieren.
- Anti-Dupe prüft Online-Inventare, Enderkisten, offene Container und Boden-Items – nicht unbeobachtete Kisten. Stapelbare Items bekommen keine ID, weil sie sonst nicht mehr stacken.

## Build
`gradle build` – das JAR landet in `build/libs/`. GitHub Actions baut bei jedem Push automatisch (Artefakt "AtlasAC"), Tags `v*` erzeugen ein Release.
