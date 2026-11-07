<?php
require_once("getid3/getid3.php");

// ==========================================
// KONFIGURATION
// ==========================================
$enableWavSupport = false; // Setze auf 'false', um den WAV-Button auszublenden
// ==========================================

$getID3 = new getID3;

// --- FUNKTIONEN ---

// 1. PHP-Version der Zeit-Formatierung (Damit es beim Laden sofort sichtbar ist)
function formatStreamingTimePHP($totalSeconds) {
    $totalSeconds = (int)$totalSeconds;
    if ($totalSeconds <= 0) return "0 Min";

    $s = floor($totalSeconds);
    $m = floor($s / 60);
    $h = floor($m / 60);
    $d = floor($h / 24);
    $y = floor($d / 365);

    $s = $s % 60;
    $m = $m % 60;
    $h = $h % 24;
    $d = $d % 365;

    $parts = [];
    if ($y > 0) $parts[] = $y . " Jahr" . ($y > 1 ? "e" : "");
    if ($d > 0) $parts[] = $d . " Tag" . ($d > 1 ? "e" : "");
    if ($h > 0) $parts[] = $h . " Std";
    if ($m > 0) $parts[] = $m . " Min";

    // KORREKTUR: Sekunden hinzufügen, wenn bereits größere Einheiten vorhanden sind
    if ($s > 0 && !empty($parts)) {
        $parts[] = $s . " Sek";
    }

    if (empty($parts)) {
        if ($s > 0) return $s . " Sek";
        return "weniger als 1 Min";
    }
    return implode(" ", $parts);
}

// 2. Generiert stabile IDs aus Dateinamen
function generateSongKey($filename) {
    $name = pathinfo($filename, PATHINFO_FILENAME);
    $name = str_replace(['ä', 'ö', 'ü', 'ß', 'Ä', 'Ö', 'Ü'], ['ae', 'oe', 'ue', 'ss', 'Ae', 'Oe', 'Ue'], $name);
    $name = preg_replace('/[^a-zA-Z0-9]+/', '-', strtolower($name));
    return trim($name, '-');
}

// --- AUTOMATISCHER SCANNER ---

$baseDir = __DIR__ . '/songs/standard';
$songKeys = [];
$scannedSongsFound = false;

if (is_dir($baseDir)) {
    $iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator($baseDir));
    foreach ($iterator as $file) {
        if ($file->isFile() && strtolower($file->getExtension()) === 'mp3') {
            $fullPath = $file->getPathname();
            $relativePath = str_replace('\\', '/', str_replace(__DIR__ . '/', '', $fullPath));
            
            $key = generateSongKey($file->getFilename());
            $songKeys[$key] = $relativePath;
            $scannedSongsFound = true;
        }
    }
}

// song_keys.json aktualisieren
$songKeysFile = __DIR__ . '/song_keys.json';
if ($scannedSongsFound) {
    file_put_contents($songKeysFile, json_encode($songKeys, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
} else {
    $songKeys = file_exists($songKeysFile) ? json_decode(file_get_contents($songKeysFile), true) : [];
}

// --- COUNTER LOGIK ---

$counterFile = __DIR__ . '/counter.json';
if (!file_exists($counterFile)) {
    file_put_contents($counterFile, json_encode(['songs' => [], 'global' => ['streams' => 0, 'minutes' => 0]], JSON_PRETTY_PRINT));
}
$counterData = json_decode(file_get_contents($counterFile), true) ?: ['songs' => [], 'global' => ['streams' => 0, 'minutes' => 0]];

// Neue Songs registrieren
$counterUpdated = false;
foreach ($songKeys as $key => $path) {
    if (!isset($counterData['songs'][$key])) {
        $counterData['songs'][$key] = ['streams' => 0, 'minutes' => 0];
        $counterUpdated = true;
    }
}

if ($counterUpdated) {
    file_put_contents($counterFile, json_encode($counterData, JSON_PRETTY_PRINT));
}

$initialStats = $counterData;

// --- ALBUM & METADATA ---

$albums = [];
foreach ($songKeys as $key => $path) {
    if (!file_exists(__DIR__ . '/' . $path)) continue;

    $album = basename(dirname(__DIR__ . '/' . $path));
    $info = @$getID3->analyze(__DIR__ . '/' . $path);
    $title = $info['tags']['id3v2']['title'][0] ?? $info['tags']['id3']['title'][0] ?? pathinfo($path, PATHINFO_FILENAME);
    
    $albums[$album][] = [
        'key' => $key,
        'file' => $path,
        'title' => $title,
        'album' => $album
    ];
}

$albumKeys = array_keys($albums);
sort($albumKeys);

$allSongs = [];
foreach ($albums as $album) {
    foreach ($album as $song) $allSongs[] = $song;
}

// Neuster Song
$newestSong = null;
$newestTime = 0;
foreach ($allSongs as $song) {
    $filePath = __DIR__ . '/' . $song['file'];
    $mtime = file_exists($filePath) ? filemtime($filePath) : 0;
    if ($mtime > $newestTime) {
        $newestTime = $mtime;
        $newestSong = $song;
    }
}
?>
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
<div><strong>Gesamt Streams:</strong> <span id="globalStreams"><?=htmlspecialchars($initialStats['global']['streams']??0)?></span></div>
<div><strong>Gesamt Streaming-Zeit:</strong> <span id="globalMinutes"><?=formatStreamingTimePHP(($initialStats['global']['minutes']??0) * 60)?></span></div>
<hr style="border:none;border-top:1px solid #333;margin:.6rem 0;">
<div><strong>Aktueller Song - Streams:</strong> <span id="songStreams">0</span></div>
<div><strong>Aktueller Song - Streaming-Zeit:</strong> <span id="songMinutes">0</span></div>
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

<?php if($enableWavSupport): ?>
<button id="qualityButton">🔊 Qualität: MP3</button>
<?php endif; ?>

<audio id="player"></audio>
<div id="timeDisplay" aria-live="polite">0:00 / 0:00</div>

<div class="controls">
<button id="prevBtn" title="Vorheriger Song">⏮</button>
<button id="playBtn" title="Play/Pause">⏯</button>
<button id="nextBtn" title="Nächster Song">⏭</button>
<button id="loopBtn" title="Loop an/aus">🔁</button>
<button id="shuffleBtn" title="Shuffle an/aus">🔀</button>
</div>

<div class="volume-container">
<span class="volume-icon">🔊</span>
<input type="range" id="volumeSlider" min="0" max="1" step="0.01" value="1" aria-label="Lautstärke">
</div>

<button id="shareButton" title="Link zum Teilen kopieren">🔗 Link kopieren</button>
<footer>&copy; <?=date("Y")?> by Schwafelpatie and Suno AI</footer>
</div>

<script>
const songKeys = <?=json_encode($songKeys,JSON_UNESCAPED_SLASHES)?>;
const albums = <?=json_encode($albums,JSON_UNESCAPED_SLASHES)?>;
const albumKeys = <?=json_encode($albumKeys)?>;
const allSongs = <?=json_encode($allSongs,JSON_UNESCAPED_SLASHES)?>;
const initialStats = <?=json_encode($initialStats,JSON_UNESCAPED_SLASHES)?>;
const newestSongFile = <?=json_encode($newestSong['file']??'')?>;

const albumSelector = document.getElementById('albumSelector');
const songSelector = document.getElementById('songSelector');
const player = document.getElementById('player');
const cover = document.getElementById('cover');
const metaTitle = document.getElementById('meta-title');
const timeDisplay = document.getElementById('timeDisplay');
const qualityButton = document.getElementById('qualityButton'); 
const prevBtn = document.getElementById('prevBtn');
const playBtn = document.getElementById('playBtn');
const nextBtn = document.getElementById('nextBtn');
const loopBtn = document.getElementById('loopBtn');
const shuffleBtn = document.getElementById('shuffleBtn');
const volumeSlider = document.getElementById('volumeSlider');
const shareButton = document.getElementById('shareButton');

const globalStreamsEl = document.getElementById('globalStreams');
const globalMinutesEl = document.getElementById('globalMinutes');
const songStreamsEl = document.getElementById('songStreams');
const songMinutesEl = document.getElementById('songMinutes');

let currentAlbumIndex = null;
let currentSongIndex = null;
let losslessEnabled = false;
let loopEnabled = false;
let shuffleEnabled = false;
let lastTime = 0;
let playedSeconds = 0;
let streamCounted = false;

// History Array für den Zurück-Button bei Shuffle
let songHistory = [];

// JS Version für Updates während des Abspielens (KORREKTUR: Zeigt Sekunden an, wenn Min/Std/Tag/Jahr > 0)
function formatStreamingTime(totalSeconds){
  if(!isFinite(totalSeconds)) totalSeconds=0;
  let s = Math.floor(totalSeconds);
  let m = Math.floor(s / 60);
  let h = Math.floor(m / 60);
  let d = Math.floor(h / 24);
  let y = Math.floor(d / 365);
  
  s = s % 60;
  m = m % 60;
  h = h % 24;
  d = d % 365;
  
  const parts = [];
  if(y > 0) parts.push(y + " Jahr" + (y > 1 ? "e" : ""));
  if(d > 0) parts.push(d + " Tag" + (d > 1 ? "e" : ""));
  if(h > 0) parts.push(h + " Std");
  if(m > 0) parts.push(m + " Min");

  // KORREKTUR: Sekunden hinzufügen, wenn bereits größere Einheiten vorhanden sind
  if(s > 0 && parts.length > 0) {
      parts.push(s + " Sek");
  }
  
  if(parts.length === 0) {
      if (s > 0) return s + " Sek";
      return "0 Min";
  }
  return parts.join(" ");
}

function formatTime(s){if(!isFinite(s))s=0;const m=Math.floor(s/60),sec=Math.floor(s%60);return m+":"+(sec<10?"0":"")+sec;}

function populateSongs(albumIndex){
  const albumName=albumKeys[albumIndex];
  const songs=albums[albumName]||[];
  songSelector.innerHTML='';
  songs.forEach((s,idx)=>{
    const opt=document.createElement('option');
    opt.value=idx;
    opt.textContent=s.title;
    songSelector.appendChild(opt);
  });
  songSelector.disabled=false;
}

function populateAllSongs(){
  songSelector.innerHTML='';
  allSongs.forEach((s,idx)=>{
    const opt=document.createElement('option');
    opt.value=idx;
    opt.textContent=s.album+" - "+s.title;
    songSelector.appendChild(opt);
  });
  songSelector.disabled=false;
}

function loadStatsForSong(key){
  fetch('counter.php?action=get&song='+encodeURIComponent(key))
    .then(r=>r.json())
    .then(data=>{
      songStreamsEl.textContent=data.song.streams??0;
      // KORREKTUR: Minuten in Sekunden umrechnen (* 60)
      songMinutesEl.textContent=formatStreamingTime((data.song.minutes??0) * 60);
      globalStreamsEl.textContent=data.global.streams??(initialStats.global.streams||0);
      // KORREKTUR: Minuten in Sekunden umrechnen (* 60)
      globalMinutesEl.textContent=formatStreamingTime((data.global.minutes??(initialStats.global.minutes||0)) * 60);
    }).catch(()=>{});
}

// LOGIK FIX: addToHistory Parameter
function changeSong(songIndex, play=true, addToHistory=true){
  let song=currentAlbumIndex==='all'?allSongs[songIndex]:albums[albumKeys[currentAlbumIndex]][songIndex];
  if(!song)return;

  // Nur zur History hinzufügen, wenn wir normal navigieren (Next, Klick), nicht bei "Zurück"
  if(addToHistory && currentSongIndex !== null) {
    songHistory.push(currentSongIndex);
  }

  currentSongIndex=songIndex;
  
  const path = losslessEnabled 
    ? song.file.replace('/standard/', '/lossless/').replace('.mp3', '.wav') 
    : song.file;

  player.src=path;
  metaTitle.textContent="Titel: "+song.title;
  cover.style.backgroundImage=`url('songs/${song.album}/cover.jpg'), url('default-cover.jpg')`;
  document.title=song.title+" – Schwafelpatie FM";
  lastTime=0;
  playedSeconds=0;
  streamCounted=false;
  loadStatsForSong(song.key);
  albumSelector.value=currentAlbumIndex==='all'?'all':currentAlbumIndex;
  songSelector.value=currentSongIndex;
  if(play)player.play().catch(()=>{});
  updateMediaSessionMetadata();
}

function previousSong(){
  // LOGIK FIX: Erst History checken
  if (songHistory.length > 0) {
    const lastIndex = songHistory.pop();
    // false = nicht erneut zur History hinzufügen, da wir rückwärts gehen
    changeSong(lastIndex, true, false); 
  } else {
    // Fallback: Rechnerisch vorheriger Song
    const maxIdx=currentAlbumIndex==='all'?allSongs.length:albums[albumKeys[currentAlbumIndex]].length;
    const newIndex=(currentSongIndex - 1 + maxIdx) % maxIdx;
    changeSong(newIndex, true, false);
  }
}

function nextSong(){
  const maxIdx=currentAlbumIndex==='all'?allSongs.length:albums[albumKeys[currentAlbumIndex]].length;
  // Wenn Shuffle an: Zufall, sonst der Nächste
  const newIndex=shuffleEnabled?Math.floor(Math.random()*maxIdx):(currentSongIndex+1)%maxIdx;
  changeSong(newIndex, true, true); // true = alten Song in History speichern
}

function togglePlayPause(){if(player.paused)player.play();else player.pause();}
function toggleLoop(){loopEnabled=!loopEnabled;player.loop=loopEnabled;loopBtn.classList.toggle('active',loopEnabled);}
function toggleShuffle(){shuffleEnabled=!shuffleEnabled;shuffleBtn.classList.toggle('active',shuffleEnabled);}

function toggleQuality(){
  if(!qualityButton) return;
  losslessEnabled=!losslessEnabled;
  qualityButton.textContent=`🔊 Qualität: ${losslessEnabled?'WAV':'MP3'}`;
  // Qualitätswechsel ist kein Songwechsel für die History (deshalb false)
  if(currentSongIndex!==null)changeSong(currentSongIndex, !player.paused, false); 
}

prevBtn.addEventListener('click',previousSong);
playBtn.addEventListener('click',togglePlayPause);
nextBtn.addEventListener('click',nextSong);
loopBtn.addEventListener('click',toggleLoop);
shuffleBtn.addEventListener('click',toggleShuffle);

if(qualityButton) {
  qualityButton.addEventListener('click',toggleQuality);
}

volumeSlider.addEventListener('input',()=>player.volume=volumeSlider.value);

/* === Media Session API === */
if ('mediaSession' in navigator) {
  const updateMediaMetadata = () => {
    const song = allSongs[currentSongIndex];
    if(!song) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: song.title,
      artist: 'Schwafelpatie FM',
      album: song.album,
      artwork: [
        { src: `songs/${song.album}/cover.jpg`, sizes: '512x512', type: 'image/jpeg' }
      ]
    });
  };

  navigator.mediaSession.setActionHandler('play', togglePlayPause);
  navigator.mediaSession.setActionHandler('pause', togglePlayPause);
  navigator.mediaSession.setActionHandler('previoustrack', () => { previousSong(); updateMediaMetadata(); });
  navigator.mediaSession.setActionHandler('nexttrack', () => { nextSong(); updateMediaMetadata(); });
  navigator.mediaSession.setActionHandler('seekbackward', (details) => { player.currentTime = Math.max(player.currentTime - (details.seekOffset || 10), 0); });
  navigator.mediaSession.setActionHandler('seekforward', (details) => { player.currentTime = Math.min(player.currentTime + (details.seekOffset || 10), player.duration); });
  navigator.mediaSession.setActionHandler('stop', () => { player.pause(); });
}

function updateMediaSessionMetadata() {
  if('mediaSession' in navigator){
    const song = allSongs[currentSongIndex];
    if(!song) return;
    navigator.mediaSession.metadata.title = song.title;
    navigator.mediaSession.metadata.album = song.album;
    navigator.mediaSession.metadata.artist = 'Schwafelpatie FM';
    navigator.mediaSession.metadata.artwork = [
      { src: `songs/${song.album}/cover.jpg`, sizes: '512x512', type: 'image/jpeg' }
    ];
  }
}

albumSelector.addEventListener('change',()=>{
  const val=albumSelector.value;
  songHistory = []; // History leeren bei Albumwechsel
  if(val==='all'){currentAlbumIndex='all';populateAllSongs();if(allSongs.length>0)changeSong(0,true,false);}
  else{currentAlbumIndex=parseInt(val);populateSongs(currentAlbumIndex);if(albums[albumKeys[currentAlbumIndex]].length>0)changeSong(0,true,false);}
});

songSelector.addEventListener('change',()=>changeSong(parseInt(songSelector.value), true, true));

player.addEventListener('loadedmetadata',()=>{lastTime=0;playedSeconds=0;streamCounted=false;const d=player.duration||0;timeDisplay.textContent=formatTime(0)+' / '+formatTime(d);});
player.addEventListener('timeupdate',()=>{
  const c=player.currentTime,d=player.duration||0;
  if(isFinite(d)&&d>0){timeDisplay.textContent=formatTime(c)+' / '+formatTime(d);if(lastTime>0){const diff=c-lastTime;if(diff>0&&diff<5)playedSeconds+=diff;}lastTime=c;
  if(!streamCounted&&(c/d)>=0.8){streamCounted=true;fetch('counter.php?action=stream&song='+encodeURIComponent(allSongs[currentSongIndex].key)).then(r=>r.json()).then(resp=>{if(resp&&resp.global){globalStreamsEl.textContent=resp.global.streams??globalStreamsEl.textContent;songStreamsEl.textContent=resp.song.streams??songStreamsEl.textContent;}}).catch(()=>{});}}
});

player.addEventListener('ended',()=>{
  const totalSeconds = Math.floor(playedSeconds);
  if(totalSeconds > 0){
    fetch('counter.php?action=seconds&song='+encodeURIComponent(allSongs[currentSongIndex].key)+'&seconds='+encodeURIComponent(totalSeconds))
    .then(r=>r.json())
    .then(resp=>{
      if(resp&&resp.global){
        // KORREKTUR: Minuten in Sekunden umrechnen (* 60)
        songMinutesEl.textContent=formatStreamingTime(resp.song.minutes * 60);
        globalMinutesEl.textContent=formatStreamingTime(resp.global.minutes * 60);
      }
    }).catch(()=>{});
  }
  lastTime=0;
  playedSeconds=0;
  if(!loopEnabled)nextSong();
});

const newestSongBtn=document.getElementById('newestSongBtn');
if(newestSongBtn){
  newestSongBtn.addEventListener('click',()=>{
    const idx=allSongs.findIndex(s=>s.file===newestSongFile);
    if(idx>=0){
        currentAlbumIndex='all';
        songHistory = []; 
        changeSong(idx,true, true);
    }
  });
}

shareButton.addEventListener('click',()=>{
  const key=allSongs[currentSongIndex].key;
  const url=`${window.location.origin}${window.location.pathname}?song=${key}`;
  navigator.clipboard.writeText(url).then(()=>{shareButton.textContent='✅ Link kopiert!';setTimeout(()=>{shareButton.textContent='🔗 Link kopieren';},1500);});
});

window.addEventListener('load',()=>{
    currentAlbumIndex='all';
    albumSelector.value='all';
    populateAllSongs();
    
    const urlParams = new URLSearchParams(window.location.search);
    const songKeyParam = urlParams.get('song');
    const shuffleParam = urlParams.get('shuffle'); // Neuer Parameter

    // 1. Shuffle aktivieren, falls im Link gewünscht
    if(shuffleParam === '1' || shuffleParam === 'true' || shuffleParam === 'on') {
        toggleShuffle(); // Schaltet Variable auf true und Button auf active
    }

    let startIndex = 0;

    // 2. Start-Song bestimmen
    if(songKeyParam){
        // Wenn ein konkreter Song im Link steht, nimm diesen (hat Vorrang)
        const idx = allSongs.findIndex(s => s.key === songKeyParam);
        if(idx >= 0) startIndex = idx;
    } else if (shuffleEnabled) {
        // Wenn Shuffle an ist und kein Song gewählt wurde -> Zufälliger Start-Song
        startIndex = Math.floor(Math.random() * allSongs.length);
    }

    // 3. Song abspielen
    changeSong(startIndex, true, false);
});
</script>
</body>
</html>