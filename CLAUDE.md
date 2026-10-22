# Projekt-Notizen für Claude

## Worum es geht
Kompatibilitäts-Patch für das Minecraft-Plugin **LootChest** (Black_Eyes / Guarmanda), damit es auf neuen Paper-Versionen läuft.
Original-Plugin (immer von hier das aktuelle JAR holen): https://github.com/Guarmanda/LootChest/releases
Es gibt keinen Quellcode im Repo. Das fertige Original-JAR wird per ASM-Bytecode-Patch angepasst (`src/patcher/Patcher.java`).

## Stand
- Getestet und bestätigt vom Nutzer: Paper 26.2 (Build 121) läuft mit `dist/Lootchest-2.5.9-compat.jar`, inkl. bestehender Kisten.
- Ursachen, die gepatcht wurden: unbekannte Version → `Version.CURRENT == null` (Plugin schaltet sich ab); Paper-Versionsstring
  `26.2.build.121-stable` → `NumberFormatException` in `Main.getCompleteVersion`; exakter `Fallv_<Version>`-Klassenname.
- Fallback nutzt den NMS-Adapter `v26_1` des Originals. Bricht ein künftiges Minecraft-Update den Adapter, braucht es ein Upstream-Update.

## Update-Ablauf
1. Neues Original-JAR nach `input/` (genau eine Datei, JARs sind per .gitignore ausgeschlossen).
2. `./build.sh` → `dist/<name>-compat.jar`. Bricht ein Patch ab, hat sich das Upstream-JAR geändert → `Patcher.java` anpassen.
3. Hat der Upstream bereits Support für die neue Version, kein Patch nötig: Original-JAR direkt verwenden.
4. Neues `dist/*.jar` committen und auf den Entwicklungsbranch pushen.

## Hinweise
- Server-Logs des Nutzers zuerst nach `[LootChest]` und `ERROR` durchsuchen; Versionsformat im Log beachten (Paper 26.x: `26.2.build.121-stable`).
- Original-Klassen sind teils Java 8, teils Java 25 (Klassenversion 69): Helfer mit `--release 8` kompilieren, ASM ≥ 9.9 verwenden.
- Paper-Repo und GitHub sind in der Build-Umgebung teils gesperrt; Maven Central funktioniert (bei 429 erneut versuchen).
- Antworten an den Nutzer auf Deutsch, technisch und knapp, Quellen nennen.
