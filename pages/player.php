<!DOCTYPE html>
<html lang="de">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="theme-color" content="#141210">
    <title>Schwafelpatie FM</title>
    <link rel="stylesheet" href="pages/player.css">
</head>
<body>
<svg class="icon-library" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <symbol id="icon-radio" viewBox="0 0 24 24"><path d="M4 9v6m4-9v12m4-16v20m4-16v12m4-9v6"/></symbol>
    <symbol id="icon-play" viewBox="0 0 24 24"><path d="m9 5 12 7-12 7Z" fill="currentColor" stroke="none"/></symbol>
    <symbol id="icon-pause" viewBox="0 0 24 24"><path d="M9 5v14m6-14v14" stroke-width="3"/></symbol>
    <symbol id="icon-prev" viewBox="0 0 24 24"><path d="M5 5v14m14-14L7 12l12 7Z"/></symbol>
    <symbol id="icon-next" viewBox="0 0 24 24"><path d="M19 5v14M5 5l12 7-12 7Z"/></symbol>
    <symbol id="icon-shuffle" viewBox="0 0 24 24"><path d="M3 6h3c5 0 7 12 12 12h3m-4-4 4 4-4 4M3 18h3c5 0 7-12 12-12h3m-4-4 4 4-4 4"/></symbol>
    <symbol id="icon-loop" viewBox="0 0 24 24"><path d="M19 6H7a4 4 0 0 0-4 4m14-7 3 3-3 3M5 18h12a4 4 0 0 0 4-4M7 15l-3 3 3 3m3.5-10.5 1.5-1v5"/></symbol>
    <symbol id="icon-volume" viewBox="0 0 24 24"><path d="M4 9h4l4-4v14l-4-4H4Zm11-1a6 6 0 0 1 0 8m3-11a10 10 0 0 1 0 14"/></symbol>
    <symbol id="icon-link" viewBox="0 0 24 24"><path d="m10 13 4-4m-6 1 3-3a4 4 0 0 1 6 6l-2 2m1-1-3 3a4 4 0 0 1-6-6l2-2"/></symbol>
</svg>

<div class="shell">
    <header class="station">
        <span class="station-mark"><svg class="icon"><use href="#icon-radio"/></svg></span>
        <h1>Schwafelpatie<span> FM</span></h1>
    </header>

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
                    <label for="albumSelector">Album</label>
                    <select id="albumSelector">
                        <option value="all">Alle Alben</option>
                        <?php foreach ($albumKeys as $i => $albumName): ?>
                        <option value="<?=$i?>"><?=htmlspecialchars($albumName)?></option>
                        <?php endforeach; ?>
                    </select>
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
        </section>
    </main>

    <footer>&copy; <?=date('Y')?> Schwafelpatie &amp; Suno AI</footer>
</div>

<script id="catalog" type="application/json"><?=$catalogJson?></script>
<script src="pages/player.js" defer></script>
</body>
</html>
