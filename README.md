# LootChest – Kompatibilitäts-Patch für neue Minecraft-/Paper-Versionen

Dieses Repo erzeugt aus dem **originalen LootChest-JAR** (Black_Eyes, hier 2.5.9) eine Variante, die auch auf neueren
Minecraft-/Paper-Versionen startet (z. B. 26.2). Ein Quellcode des Plugins liegt nicht vor, deshalb wird das fertige JAR
per Bytecode-Patch (ASM) angepasst. Der Patch ist so gebaut, dass er bei künftigen Versionen ohne Änderung weiterläuft.

**Fertiges JAR:** `dist/Lootchest-2.5.9-compat.jar` → in den `plugins/`-Ordner des Servers legen.

## Ursache des Fehlers

Die im Plugin eingebettete Hologramm-Bibliothek (DecentHolograms) kennt nur eine feste Liste von Minecraft-Versionen
(`Version`-Enum). Für `26.2` (und für `26.1` auf Paper, der Eintrag gilt nur für Spigot) findet sie keinen Eintrag,
`Version.CURRENT` ist `null` und das Plugin deaktiviert sich selbst. Zusätzlich wählt der Fall-Effekt seine Klasse über
den exakten Namen `Fallv_<Version>` (es gibt nur `Fallv_26_1`).

## Was der Patch ändert

| Patch | Wirkung |
|---|---|
| `version-fallback` | Ist die Serverversion neuer als alles Bekannte (oder gleich, aber andere Plattform), wird der neueste vorhandene NMS-Adapter verwendet (aktuell `v26_1`), mit einer Warnung im Log. Bekannte Versionen verhalten sich unverändert. |
| `fall-class-resolver` | Sucht zur Serverversion die nächstältere vorhandene `Fallv_*`-Klasse (26.2 → 26.1). |
| `safe-summon` | Schlägt der NMS-Fall-Effekt fehl, wird ein normaler fallender Block verwendet statt eines Fehlers im Server-Thread. |
| `legacy-marker` | `Bukkit.getVersion().contains("1.7")` trifft nicht mehr zufällig auf Build-Nummern (z. B. 26.11.7) zu. |

Jeder Patch prüft, dass er seine Zielstelle gefunden hat; passt etwas nicht mehr, bricht der Build mit einer Meldung ab.

## Neu bauen / auf neue LootChest-Version aktualisieren

Voraussetzungen: JDK 21+, `curl`, Zugriff auf Maven Central (lädt ASM 9.9 in `.cache/`).

```bash
cp ~/Downloads/Lootchest-<neue-version>.jar input/   # genau ein JAR in input/
./build.sh                                             # -> dist/Lootchest-<neue-version>-compat.jar
```

Bei einer neuen Minecraft-Version ist normalerweise **nichts** zu tun. Erst wenn der Upstream selbst NMS-Anpassungen
liefert (neues `Fallv_*`/`nms.v*`), nutzt der Patch diese automatisch. Bricht `build.sh` wegen eines Patches ab, hat sich
das Upstream-JAR strukturell geändert und `src/patcher/Patcher.java` muss angepasst werden.

## Grenzen (bitte beachten)

- **Nicht auf einem echten 26.2-Server getestet.** Paper-Repository und Downloads sind in der Build-Umgebung gesperrt.
  Getestet wurde die Versionserkennung mit simulierten Bukkit-Versionen (`src/test/CompatTest.java`).
- Der Fallback nutzt den NMS-Adapter von 26.1 (Hologramme, Fall-Effekt). Hat Minecraft 26.2 interne Klassen geändert,
  fällt der Fall-Effekt auf einen normalen fallenden Block zurück. Beim Hologramm-Adapter deaktiviert die Bibliothek
  das Plugin mit einer klaren Fehlermeldung im Log; dann braucht es einen echten neuen Adapter vom Upstream.
- Kein Ersatz für ein offizielles Update des Autors; Lizenz und Rechte am Plugin liegen bei Black_Eyes.

## Struktur

- `src/patcher/Patcher.java` – Bytecode-Patcher
- `src/helpers/` – Hilfsklassen, die ins JAR eingebaut werden (`VersionFallback`, `FallClassResolver`, `SafeSummon`)
- `src/test/` – Test der Versionserkennung gegen simulierte Server (`stubs/` ersetzt die Bukkit-API)
- `build.sh` – Build
