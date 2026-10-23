# Boffo Addon

Meteor-Client-Addon für Minecraft 1.21.11 (Fabric). Enthält nur eine Sache: eine HUD-Anzeige, die zählt, wie oft du relogged hast.

## Benutzung
- Meteor-HUD-Editor öffnen (Standard: Rechtsshift → HUD) und das Element **Relog Counter** (Gruppe "Boffo Addon") hinzufügen/aktivieren. Ausschalten = Element deaktivieren oder entfernen.
- Verschieben: im HUD-Editor per Drag & Drop.
- Größe: Einstellung `scale` im Element.
- Zähler zurücksetzen: `.relogs reset` (Prefix kann bei dir anders sein). `.relogs` zeigt den Stand im Chat.
- Beim Schließen des Spiels wird der Zähler automatisch auf 0 gesetzt.

## Bauen
Java 21 nötig. Gradle-Wrapper aus dem Meteor-Addon-Template übernehmen (gradlew, gradlew.bat, gradle/wrapper/), dann:

    ./gradlew build

Die JAR liegt in build/libs/ und kommt zusammen mit Fabric Loader, Meteor Client (1.21.11) in den mods-Ordner.
