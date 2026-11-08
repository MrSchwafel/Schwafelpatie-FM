<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#141210">
    <title>Schwafelpatie FM</title>
    <link rel="icon" href="assets/logo.svg" type="image/svg+xml">
    <script>
        if (new URLSearchParams(location.search).get('player')?.toLowerCase() === 'obs') {
            document.documentElement.dataset.player = 'obs';
        }
    </script>
    <link rel="stylesheet" href="pages/player.css?v=<?=$styleVersion?>">
</head>
<body>
<svg class="icon-library" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <symbol id="icon-play" viewBox="0 0 24 24"><path d="m9 5 12 7-12 7Z" fill="currentColor" stroke="none"/></symbol>
    <symbol id="icon-pause" viewBox="0 0 24 24"><path d="M9 5v14m6-14v14" stroke-width="3"/></symbol>
    <symbol id="icon-prev" viewBox="0 0 24 24"><path d="M5 5v14m14-14L7 12l12 7Z"/></symbol>
    <symbol id="icon-next" viewBox="0 0 24 24"><path d="M19 5v14M5 5l12 7-12 7Z"/></symbol>
    <symbol id="icon-shuffle" viewBox="0 0 24 24"><path d="M3 6h3c5 0 7 12 12 12h3m-4-4 4 4-4 4M3 18h3c5 0 7-12 12-12h3m-4-4 4 4-4 4"/></symbol>
    <symbol id="icon-loop" viewBox="0 0 24 24"><path d="M19 6H7a4 4 0 0 0-4 4m14-7 3 3-3 3M5 18h12a4 4 0 0 0 4-4M7 15l-3 3 3 3m3.5-10.5 1.5-1v5"/></symbol>
    <symbol id="icon-volume" viewBox="0 0 24 24"><path d="M4 9h4l4-4v14l-4-4H4Zm11-1a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/></symbol>
    <symbol id="icon-link" viewBox="0 0 24 24"><path d="m10 13 4-4m-6 1 3-3a4 4 0 0 1 6 6l-2 2m1-1-3 3a4 4 0 0 1-6-6l2-2"/></symbol>
    <symbol id="icon-settings" viewBox="0 0 24 24"><path d="M4 7h16M4 17h16"/><circle cx="9" cy="7" r="2.5" fill="var(--surface)"/><circle cx="15" cy="17" r="2.5" fill="var(--surface)"/></symbol>
</svg>

<section class="obs-banner" id="obsBanner" aria-label="OBS-Musikanzeige">
    <button class="obs-logo" id="obsPlay" type="button" aria-label="Abspielen" title="Abspielen oder pausieren">
        <img src="assets/logo.svg" alt="" width="72" height="72">
    </button>
    <div class="obs-track">
        <div class="obs-station">
            <span>Schwafelpatie FM</span>
            <span class="obs-status" id="obsStatus">Bereit</span>
        </div>
        <p class="obs-title" id="obsTitle"><?=htmlspecialchars($allSongs[0]['title'])?></p>
        <p class="obs-album" id="obsAlbum"><?=htmlspecialchars($allSongs[0]['album'])?></p>
    </div>
    <button class="obs-options" id="obsOptions" type="button" aria-label="Songauswahl öffnen" title="Songauswahl öffnen">
        <svg class="icon" aria-hidden="true"><use href="#icon-settings"/></svg>
    </button>
    <div class="obs-progress" id="obsProgress" aria-hidden="true"></div>
</section>

<div class="shell">
    <header class="station">
        <img class="station-mark" src="assets/logo.svg" alt="" width="48" height="48">
        <h1>Schwafelpatie<span> FM</span></h1>
        <?php if ($apkVersion !== null): ?>
        <a class="share app-download" href="<?=htmlspecialchars($androidApk)?>?v=<?=$apkVersion?>" download="Schwafelpatie-FM-<?=htmlspecialchars($androidRelease)?>.apk">Android-App <span>APK <?=htmlspecialchars($androidRelease)?> herunterladen</span></a>
        <?php endif; ?>
    </header>
    <div class="obs-toolbar">
        <strong>OBS-Steuerung</strong>
        <button class="share" type="button" id="obsClose">Overlay anzeigen</button>
    </div>

    <main>
        <section class="deck" aria-label="Musikplayer">
            <div class="artwork">
                <img id="cover" src="<?=htmlspecialchars($allSongs[0]['cover'])?>" alt="" width="598" height="598">
            </div>

            <div class="playback">
                <p class="status" id="playbackStatus"><span class="status-dot"></span><span id="playbackLabel">Bereit zur Wiedergabe</span></p>
                <div class="track">
                    <h2 id="meta-title"><?=htmlspecialchars($allSongs[0]['title'])?></h2>
                    <p id="meta-album"><?=htmlspecialchars($allSongs[0]['album'])?></p>
                </div>

                <audio id="player" preload="metadata"></audio>
                <div class="timeline">
                    <input type="range" id="progress" min="0" max="1" step="0.01" value="0" aria-label="Wiedergabeposition" disabled>
                    <div class="times" aria-hidden="true"><span id="elapsed">0:00</span><span id="duration">0:00</span></div>
                </div>

                <div class="transport">
                    <button type="button" class="control" id="shuffleBtn" aria-label="Zufallswiedergabe" title="Zufallswiedergabe" aria-pressed="false"><svg class="icon"><use href="#icon-shuffle"/></svg></button>
                    <button type="button" class="control" id="prevBtn" aria-label="Vorheriger Song" title="Vorheriger Song"><svg class="icon"><use href="#icon-prev"/></svg></button>
                    <button type="button" class="control play" id="playBtn" aria-label="Abspielen" title="Abspielen"><svg class="icon"><use id="playIcon" href="#icon-play"/></svg></button>
                    <button type="button" class="control" id="nextBtn" aria-label="Nächster Song" title="Nächster Song"><svg class="icon"><use href="#icon-next"/></svg></button>
                    <button type="button" class="control" id="loopBtn" aria-label="Aktuellen Song in Endlosschleife abspielen" title="Aktuellen Song wiederholen" aria-pressed="false"><svg class="icon"><use href="#icon-loop"/></svg></button>
                </div>

                <div class="utilities">
                    <div class="volume">
                        <svg class="icon"><use href="#icon-volume"/></svg>
                        <input type="range" id="volumeSlider" min="0" max="1" step="0.01" value="1" aria-label="Lautstärke">
                    </div>
                    <button type="button" id="shareButton" class="share">
                        <svg class="icon"><use href="#icon-link"/></svg>
                        <span id="shareLabel" aria-live="polite">Link kopieren</span>
                    </button>
                </div>
            </div>
        </section>

        <section class="library" aria-labelledby="libraryTitle">
            <div class="library-header">
                <h2 id="libraryTitle">Mediathek</h2>
                <p id="trackCount"><?=count($allSongs)?> Songs</p>
            </div>
            <div class="library-fields">
                <div class="field">
                    <label id="albumLabel">Alben</label>
                    <button type="button" class="album-picker" id="albumSelector" aria-labelledby="albumLabel albumSummary"><span id="albumSummary">Alle Alben</span></button>
                </div>
                <div class="field">
                    <label for="songSelector">Song</label>
                    <select id="songSelector" disabled>
                        <option>Song auswählen …</option>
                    </select>
                </div>
            </div>

            <?php if ($newestSong): ?>
            <button type="button" class="latest" id="newestSongBtn">
                <span class="latest-icon"><svg class="icon"><use href="#icon-play"/></svg></span>
                <span class="latest-copy">
                    <span class="latest-label">Neu in der Mediathek</span>
                    <strong><?=htmlspecialchars($newestSong['title'])?></strong>
                </span>
            </button>
            <?php endif; ?>
            <button type="button" class="latest" id="sebiSoundtrack" hidden>
                <span class="latest-icon"><svg class="icon" aria-hidden="true"><use href="#icon-play"/></svg></span>
                <span class="latest-copy">
                    <span class="latest-label">Sebi ist schuld · Hier ist der passende Soundtrack</span>
                    <strong>Die Hymne starten</strong>
                    <span>Öffnet das Sebi-Album mit Rock, Austropop, Pop-Jazz und der Katastrophensuite.</span>
                </span>
            </button>
        </section>
    </main>

    <footer>&copy; <?=date('Y')?> Schwafelpatie &amp; Suno AI <span aria-hidden="true">·</span> <a href="?player=OBS">OBS-Overlay</a></footer>
</div>

<dialog id="albumDialog" aria-labelledby="albumDialogTitle">
    <form method="dialog">
        <h2 id="albumDialogTitle">Alben zum Hören auswählen</h2>
        <div class="album-list">
            <?php foreach ($albumKeys as $albumName): ?>
            <label><input type="checkbox" name="album" value="<?=htmlspecialchars($albumName)?>"> <?=htmlspecialchars($albumName)?></label>
            <?php endforeach; ?>
        </div>
        <div class="album-actions">
            <button type="submit" class="share" value="cancel">Abbrechen</button>
            <button type="submit" class="share" value="all">Alle Alben</button>
            <button type="submit" class="share" value="apply">Übernehmen</button>
        </div>
    </form>
</dialog>

<script id="catalog" type="application/json"><?=$catalogJson?></script>
<script src="pages/player.js?v=<?=$scriptVersion?>" defer></script>
</body>
</html>
