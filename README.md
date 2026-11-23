# Anki Audio

Audio-Modus für AnkiDroid: liest Karten eines Decks vor, Rückseite per Taste aufdecken, per Taste bewerten. Grafische Karten (Flaggen, Weltkarte, Image Occlusion) werden automatisch übersprungen und bleiben in AnkiDroid fällig.

Die App greift über die offizielle AnkiDroid API (ContentProvider) auf die Sammlung zu. Bewertungen landen direkt in AnkiDroid und synchronisieren normal mit AnkiWeb.

## Installieren

1. Jeder Push auf `main` baut per GitHub Actions eine APK und hängt sie an das Release **latest**.
2. Auf dem Handy: GitHub-Release `latest` öffnen, `anki-audio.apk` laden, installieren (Installation aus unbekannten Quellen erlauben).
3. Beim ersten Start "Zugriff erlauben" tippen (AnkiDroid-Berechtigung).

Die APKs sind mit dem festen `debug.keystore` im Repo signiert, deshalb lässt sich jede neue Version über die alte installieren.

## Bedienung (Stand v0.1)

| Wann | Taste | Aktion |
|---|---|---|
| Vorderseite | Leertaste, Enter, Play/Pause, Lautstärke | Aufdecken |
| Rückseite | Lautstärke hoch / Leertaste | Good |
| Rückseite | Lautstärke runter | Again |
| immer | 1-4 oder A/H/G/E | Again / Hard / Good / Easy |
| immer | R / S | Wiederholen / Überspringen |

In v0.1 muss der Bildschirm an sein. Steuerung bei ausgeschaltetem Bildschirm, Cloud-Stimmen und Sprachbefehle folgen.

## Entwicklung

Lokal wird nichts gebaut (kein Android SDK nötig). Unit-Tests und APK laufen in `.github/workflows/build.yml`.

Struktur:
- `anki/` AnkiDroid ContentProvider (Decks, fällige Karten, Inhalt, Bewertung)
- `content/` HTML zu Sprechtext, Erkennung grafischer Karten, Sprache de/en
- `session/` Ablauf einer Session, Tastenbelegung
- `tts/` Systemstimme, Signaltöne
