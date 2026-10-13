#!/usr/bin/env python3
"""Baut aus den gerenderten Seiten (quelle.html) das animierte GIF: python3 gif.py <Ordner mit PNGs> <hell|dunkel> <Ziel.gif>"""
import sys
from PIL import Image, ImageDraw, ImageFont

FOLGE = [
    ("start", "Start", "Verbunden mit dem PC, zuletzt gesendet"),
    ("uebersicht", "Übersicht", "Speicher, Schnellzugriff, Kategorien, Suche"),
    ("liste", "Ordner und Dateien", "Vorschaubilder, Symbole, Ordner zuerst"),
    ("sortieren", "Ansicht und Sortierung", "Liste, Raster, Kompakt, Vorschau, Sortierung"),
    ("dokumente", "Dokumente", "Reiter und Schalter Intern / SD-Karte"),
    ("auswahl", "Mehrfachauswahl", "Senden, Kopieren, Verschieben, Löschen"),
    ("einfuegen", "Kopieren und Verschieben", "Zielordner öffnen, dann „Hierher“"),
    ("zweifenster", "Zwei Fenster", "Eine Zeile je Fenster, eigene Ansicht"),
    ("entpacken", "ZIP entpacken", "Vorschau, Platzprüfung, Schutz vor unsicheren Pfaden"),
    ("umbenennen", "Mehrere umbenennen", "Vier Arten, Vorschau alt → neu"),
    ("loeschen", "Löschen", "Erst in den Papierkorb, mit Nachfrage"),
    ("papierkorb", "Papierkorb", "30 Tage, zurückholen oder endgültig löschen"),
    ("analyse", "Speicheranalyse", "Gruppen, größte Ordner und Dateien"),
    ("vompc", "Vom PC", "Dateien vom PC ansehen und laden"),
    ("vompcauswahl", "Vom PC: Auswahl", "Mehrere laden, teilen, speichern"),
]

ordner, theme, ziel = sys.argv[1], sys.argv[2], sys.argv[3]
dunkel = theme == "dunkel"
bg = (22, 24, 21) if dunkel else (244, 246, 242)
fg = (242, 245, 238) if dunkel else (28, 33, 26)
dim = (185, 196, 178) if dunkel else (86, 96, 82)
pr = (126, 214, 72) if dunkel else (63, 138, 33)
fb = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 17)
fr = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 13)
fs = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 12)
B, H, K = 360, 740, 74
frames = []
for n, (seite, titel, zeile) in enumerate(FOLGE, 1):
    shot = Image.open(f"{ordner}/{seite}-{theme}.png").convert("RGB").resize((B, H), Image.LANCZOS)
    f = Image.new("RGB", (B, H + K), bg)
    f.paste(shot, (0, 0))
    d = ImageDraw.Draw(f)
    d.rectangle((0, H, B, H + 2), fill=pr)
    d.text((16, H + 12), titel, font=fb, fill=fg)
    d.text((16, H + 40), zeile, font=fr, fill=dim)
    zahl = f"{n}/{len(FOLGE)}"
    d.text((B - 16 - d.textlength(zahl, font=fs), H + 14), zahl, font=fs, fill=pr)
    frames.append(f.quantize(colors=96, method=Image.MEDIANCUT, dither=Image.NONE))
dauer = [3000] + [2600] * (len(frames) - 1)
frames[0].save(ziel, save_all=True, append_images=frames[1:], duration=dauer, loop=0, optimize=True, disposal=1)
print(ziel, len(frames), "Bilder")
