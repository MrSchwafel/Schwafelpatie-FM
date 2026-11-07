# Schwafelpatie FM

Die GitHub-Pages-Version ist ein statischer Musikplayer. Der Build liest die MP3s in `songs/standard/`, übernimmt vorhandene Song-IDs aus `song_keys.json` und erzeugt `dist/index.html`. PHP wird nur beim Build gebraucht.

## Lokal öffnen

Mit PHP 8.1 oder neuer im Projektordner ausführen.

```sh
php scripts/build-pages.php
```

Anschließend `dist/index.html` im Browser öffnen. Für einen weiteren Build `dist` vorher umbenennen oder einen neuen Ausgabeordner angeben, etwa `php scripts/build-pages.php dist-neu`.

## Auf GitHub Pages veröffentlichen

1. Im Repository unter **Settings → Pages** bei **Source** die Option **GitHub Actions** wählen. Ein privates Repository benötigt dafür bei einem persönlichen Konto GitHub Pro. Die veröffentlichte Website ist öffentlich erreichbar.
2. Unter **Actions → GitHub Pages → Run workflow** den Branch `main` auswählen und den ersten Lauf starten. Weitere Pushes auf `main` bauen und veröffentlichen die Website automatisch.
3. Die Adresse der Website steht beim erfolgreichen Lauf unter **Deployment**.

Neue Songs als MP3 in den passenden Albumordner unter `songs/standard/` legen. Ein optionales Albumcover wird aus `songs/<Album>/cover.jpg` gelesen. Die WAV-Sammlung bleibt lokal. Die bisherigen Streamzahlen aus `counter.json` erscheinen als Archiv.
