<!DOCTYPE html>
<html lang="de">
<head>
<meta charset="UTF-8">
<title>Schwafelpatie FM</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
*{box-sizing:border-box;margin:0;padding:0;}
body{font-family:'Segoe UI',sans-serif;background:#111;color:#eee;display:flex;align-items:center;justify-content:center;min-height:100vh;padding:2rem;}
.player-container{background:#1e1e1e;padding:2rem;border-radius:16px;max-width:520px;width:100%;box-shadow:0 0 20px rgba(0,0,0,0.6);text-align:center;}
h1{margin-bottom:1.5rem;font-size:1.8rem;color:#ffda6c;}
.cover{width:100%;height:300px;background:#333 center/cover no-repeat;border-radius:12px;margin-bottom:1rem;}
.meta{margin-bottom:1rem;font-size:1rem;color:#ccc;}
.stats-box{background:#262626;padding:.8rem;border-radius:10px;color:#ddd;text-align:left;margin-bottom:1rem;}
.stats-box div{margin:.25rem 0;}
select,button,input[type=range]{appearance:none;border:none;padding:.6rem 1rem;border-radius:8px;margin:.3rem;font-size:1rem;}
select{width:100%;background:#2b2b2b;color:#fff;}
button{cursor:pointer;background:#444;color:#fff;transition:background .3s;}
button:hover{background:#666;}
.controls{display:flex;justify-content:center;margin:1rem 0;}
.controls button{margin:0 .4rem;font-size:1.4rem;}
button.active{background:#ff9800;color:#000;}
.volume-container{display:flex;align-items:center;justify-content:center;gap:12px;margin:1.2rem auto;}
.volume-icon{font-size:1.4rem;color:#ffda6c;}
input[type=range]{-webkit-appearance:none;appearance:none;width:100%;max-width:200px;height:8px;background:#333;border-radius:4px;outline:none;cursor:pointer;transition:background .3s;}
input[type=range]::-webkit-slider-thumb{-webkit-appearance:none;appearance:none;width:16px;height:16px;border-radius:50%;background:#ffda6c;cursor:pointer;box-shadow:0 0 4px rgba(255,255,255,.4);transition:background .3s;}
input[type=range]::-moz-range-thumb{width:16px;height:16px;border:none;border-radius:50%;background:#ffda6c;cursor:pointer;box-shadow:0 0 4px rgba(255,255,255,.4);}
#timeDisplay{color:#ccc;font-size:.9rem;margin-top:.3rem;}
#shareButton{display:flex;align-items:center;justify-content:center;margin:1rem auto;background:#333;color:#eee;gap:.5rem;transition:.3s;}
footer{margin-top:2rem;font-size:.85rem;color:#888;}
</style>
</head>
<body>
<div class="player-container">
<h1>Schwafelpatie FM</h1>
<div class="cover" id="cover"></div>
<div class="meta"><div id="meta-title">Titel: –</div></div>

<?php if($newestSong): ?>
<button id="newestSongBtn" style="margin-bottom:1rem;background:#ff9800;color:#000;padding:.6rem 1rem;border-radius:8px;width:100%;font-weight:bold;">
🎉 Neuster Song: <?=htmlspecialchars($newestSong['title'])?>
</button>
<?php endif; ?>

<div class="stats-box" id="statsBox">
<strong>Stream-Archiv</strong>
<div>Gesamt: <span id="globalStreams">0</span> Streams, <span id="globalMinutes">0 Min</span></div>
<div>Aktueller Song: <span id="songStreams">0</span> Streams, <span id="songMinutes">0 Min</span></div>
</div>

<select id="albumSelector" aria-label="Album auswählen">
<option value="all">🎵 Alle Songs</option>
<?php foreach($albumKeys as $i=>$albumName): ?>
<option value="<?=$i?>"><?=htmlspecialchars($albumName)?></option>
<?php endforeach; ?>
</select>

<select id="songSelector" aria-label="Song auswählen" disabled>
<option disabled selected>🎧 Song auswählen...</option>
</select>

<audio id="player" preload="metadata"></audio>
<div id="timeDisplay" aria-live="polite">0:00 / 0:00</div>

<div class="controls">
<button id="prevBtn" title="Vorheriger Song">⏮</button>
<button id="playBtn" title="Play/Pause">⏯</button>
<button id="nextBtn" title="Nächster Song">⏭</button>
<button id="loopBtn" title="Loop an/aus" aria-pressed="false">🔁</button>
<button id="shuffleBtn" title="Shuffle an/aus" aria-pressed="false">🔀</button>
</div>

<div class="volume-container">
<span class="volume-icon">🔊</span>
<input type="range" id="volumeSlider" min="0" max="1" step="0.01" value="1" aria-label="Lautstärke">
</div>

<button id="shareButton" title="Link zum Teilen kopieren">🔗 Link kopieren</button>
<footer>&copy; <?=date("Y")?> by Schwafelpatie and Suno AI</footer>
</div>

<script id="catalog" type="application/json"><?=json_encode(
    compact('albums', 'albumKeys', 'allSongs', 'initialStats', 'newestSong'),
    JSON_THROW_ON_ERROR | JSON_HEX_TAG | JSON_INVALID_UTF8_SUBSTITUTE
)?></script>
<script src="pages/player.js" defer></script>
</body>
</html>
