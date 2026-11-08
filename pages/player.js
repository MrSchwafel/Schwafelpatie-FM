const { albumKeys, allSongs, newestSong } =
  JSON.parse(document.getElementById('catalog').textContent);

const params = new URLSearchParams(window.location.search);
const obs = document.documentElement.dataset.player === 'obs' ? {
  banner: document.getElementById('obsBanner'),
  title: document.getElementById('obsTitle'),
  album: document.getElementById('obsAlbum'),
  status: document.getElementById('obsStatus'),
  play: document.getElementById('obsPlay'),
  progress: document.getElementById('obsProgress'),
} : null;

const albumSelector = document.getElementById('albumSelector');
const albumDialog = document.getElementById('albumDialog');
const albumSummary = document.getElementById('albumSummary');
const albumCheckboxes = [...albumDialog.querySelectorAll('input[name="album"]')];
const songSelector = document.getElementById('songSelector');
const songSearch = document.getElementById('songSearch');
const songList = document.getElementById('songList');
const songRowTemplate = document.getElementById('songRowTemplate');
const libraryEmpty = document.getElementById('libraryEmpty');
const player = document.getElementById('player');
const cover = document.getElementById('cover');
const metaTitle = document.getElementById('meta-title');
const metaAlbum = document.getElementById('meta-album');
const trackCount = document.getElementById('trackCount');
const elapsed = document.getElementById('elapsed');
const duration = document.getElementById('duration');
const progress = document.getElementById('progress');
const volume = document.getElementById('volumeSlider');
const playbackStatus = document.getElementById('playbackStatus');
const playbackLabel = document.getElementById('playbackLabel');
const playButton = document.getElementById('playBtn');
const playIcon = document.getElementById('playIcon');
const shuffleButton = document.getElementById('shuffleBtn');
const loopButton = document.getElementById('loopBtn');
const shareButton = document.getElementById('shareButton');
const shareLabel = document.getElementById('shareLabel');
const previousButton = document.getElementById('prevBtn');
const newestButton = document.getElementById('newestSongBtn');
const sebiButton = document.getElementById('sebiSoundtrack');
const appDownload = document.getElementById('appDownload');
const sebiSong = allSongs.find((song) => song.key === 'sebi-ist-schuld-die-hymne-des-gescheiterten-fortschritts');
const filterKey = obs ? `schwafelpatie:obs:albums:${params.get('profile')?.trim() || 'standard'}` : 'schwafelpatie:web:albums';

appDownload?.addEventListener('click', async (event) => {
  if (location.protocol === 'file:') return;
  event.preventDefault();
  if (appDownload.getAttribute('aria-busy') === 'true') return;
  appDownload.setAttribute('aria-busy', 'true');

  const timestamp = Date.now();
  const download = document.createElement('a');
  download.href = `downloads/Schwafelpatie-FM.apk?t=${timestamp}`;
  download.download = 'Schwafelpatie-FM.apk';
  try {
    const metadata = await fetch(`downloads/latest.json?t=${timestamp}`, {
      cache: 'no-store', signal: AbortSignal.timeout(4000),
    });
    if (metadata.ok) {
      const { file } = await metadata.json();
      if (typeof file === 'string' && /^Schwafelpatie-FM-\d+(?:\.\d+)*\.apk$/.test(file)) {
        download.href = `downloads/${file}`;
        download.download = file;
      }
    }
  } catch {
    // The stable download URL also serves the latest release.
  } finally {
    appDownload.removeAttribute('aria-busy');
  }
  download.click();
});

let playlist = allSongs;
let songsByKey = new Map();
let selectedAlbums = [];
let currentSong;
let shuffle = false;
let repeat = false;
const previousSongs = [];
const upcomingSongs = [];
let shareTimer;

function loadAlbums() {
  try {
    const saved = JSON.parse((obs ? localStorage : sessionStorage).getItem(filterKey));
    return Array.isArray(saved) ? saved : [];
  } catch {
    return [];
  }
}

function applyAlbums(selection, save = true) {
  selectedAlbums = albumKeys.filter((album) => selection.includes(album));
  if (selectedAlbums.length === albumKeys.length) selectedAlbums = [];
  if (save) {
    try {
      (obs ? localStorage : sessionStorage).setItem(filterKey, JSON.stringify(selectedAlbums));
    } catch {
      // Playback still works when browser storage is unavailable.
    }
  }
  playlist = selectedAlbums.length ? allSongs.filter((song) => selectedAlbums.includes(song.album)) : allSongs;
  songsByKey = new Map(playlist.map((song) => [song.key, song]));
  albumSummary.textContent = selectedAlbums.length > 1 ? `${selectedAlbums.length} Alben ausgewählt` : selectedAlbums[0] ?? 'Alle Alben';
  if (obs) {
    const placeholder = new Option('Song auswählen …', '');
    placeholder.disabled = true;
    songSelector.replaceChildren(placeholder, ...playlist.map((song) => new Option(
      selectedAlbums.length === 1 ? song.title : `${song.album} - ${song.title}`, song.key,
    )));
    songSelector.disabled = false;
  } else {
    renderSongList();
  }
  updateLibrarySelection();
  player.loop = repeat && songsByKey.has(currentSong?.key);
  updatePreviousButton();
}

function renderSongList() {
  const query = songSearch.value.trim().toLocaleLowerCase('de');
  const results = playlist.filter((song) => `${song.title} ${song.album}`.toLocaleLowerCase('de').includes(query));
  const rows = document.createDocumentFragment();
  for (const song of results) {
    const row = songRowTemplate.content.cloneNode(true);
    const button = row.querySelector('button');
    button.dataset.song = song.key;
    button.querySelector('img').src = song.cover;
    button.querySelector('strong').textContent = song.title;
    button.querySelector('.song-row-copy span').textContent = song.album;
    rows.append(row);
  }
  songList.replaceChildren(rows);
  songList.scrollTop = 0;
  libraryEmpty.hidden = results.length > 0;
  trackCount.textContent = query ? `${results.length} von ${playlist.length} Songs`
    : `${playlist.length} ${playlist.length === 1 ? 'Song' : 'Songs'}`;
}

function updateLibrarySelection() {
  if (obs) {
    songSelector.value = songsByKey.has(currentSong?.key) ? currentSong.key : '';
    return;
  }
  if (newestButton && newestSong.key === currentSong?.key) newestButton.setAttribute('aria-current', 'true');
  else newestButton?.removeAttribute('aria-current');
  for (const row of songList.querySelectorAll('.song-row')) {
    if (row.dataset.song === currentSong?.key) row.setAttribute('aria-current', 'true');
    else row.removeAttribute('aria-current');
  }
}

function selectSong(song, autoplay = true, remember = true) {
  if (!song) return;
  if (remember && currentSong?.key !== song.key) {
    if (currentSong) previousSongs.push(currentSong.key);
    upcomingSongs.length = 0;
  }
  currentSong = song;
  player.loop = repeat && songsByKey.has(song.key);
  updateLibrarySelection();
  updatePreviousButton();
  player.src = song.file;
  cover.src = song.cover;
  metaTitle.textContent = song.title;
  metaAlbum.textContent = song.album;
  if (obs) {
    obs.title.textContent = song.title;
    obs.album.textContent = song.album;
  }
  document.title = `${song.title} – Schwafelpatie FM`;
  updateTime();
  updatePlaybackState();

  if ('mediaSession' in navigator && 'MediaMetadata' in window) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: song.title,
      artist: 'Schwafelpatie FM',
      album: song.album,
      artwork: [{ src: new URL(song.cover, document.baseURI).href }],
    });
  }
  if (autoplay) play();
}

function play() {
  const source = player.src;
  player.play().catch((error) => {
    if (error.name === 'NotAllowedError' && player.src === source && player.paused) {
      playbackLabel.textContent = 'Zum Abspielen ▶ drücken';
      if (obs) obs.status.textContent = 'Zum Starten Logo anklicken';
    }
  });
}

function updatePreviousButton() {
  previousButton.disabled = !previousSongs.some((key) => key !== currentSong?.key && songsByKey.has(key));
}

function takeFromHistory(history) {
  while (history.length) {
    const key = history.pop();
    if (key !== currentSong?.key && songsByKey.has(key)) return songsByKey.get(key);
  }
}

function previousSong() {
  const song = takeFromHistory(previousSongs);
  if (!song) return;
  upcomingSongs.push(currentSong.key);
  selectSong(song, true, false);
}

function nextSong() {
  const revisited = takeFromHistory(upcomingSongs);
  if (revisited) {
    previousSongs.push(currentSong.key);
    selectSong(revisited, true, false);
    return;
  }
  const index = playlist.findIndex((song) => song.key === currentSong?.key);
  const candidates = shuffle ? playlist.filter((song) => song.key !== currentSong?.key) : [];
  const next = candidates.length ? candidates[Math.floor(Math.random() * candidates.length)] : playlist[(index + 1) % playlist.length];
  selectSong(next);
}

function setShuffle(enabled) {
  shuffle = enabled;
  shuffleButton.setAttribute('aria-pressed', enabled);
}

function formatTime(time) {
  const seconds = Number.isFinite(time) ? Math.floor(time) : 0;
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function updateTime() {
  const hasDuration = Number.isFinite(player.duration) && player.duration > 0;
  elapsed.textContent = formatTime(player.currentTime);
  duration.textContent = formatTime(player.duration);
  progress.disabled = !hasDuration;
  progress.max = hasDuration ? player.duration : 1;
  progress.value = hasDuration ? player.currentTime : 0;
  const position = Number(progress.value) / Number(progress.max);
  progress.style.setProperty('--filled', `${100 * position}%`);
  if (obs) obs.progress.style.setProperty('--obs-position', position);
  progress.setAttribute('aria-valuetext', `${elapsed.textContent} von ${duration.textContent}`);
}

function updatePlaybackState() {
  const playing = !player.paused && !player.ended && !player.error;
  const label = playing ? 'Pausieren' : 'Abspielen';
  playButton.setAttribute('aria-label', label);
  playButton.title = label;
  playIcon.setAttribute('href', playing ? '#icon-pause' : '#icon-play');
  playbackStatus.dataset.state = playing ? 'playing' : 'idle';
  if (player.error) playbackLabel.textContent = 'Song konnte nicht geladen werden';
  else if (playing) playbackLabel.textContent = 'Wird abgespielt';
  else playbackLabel.textContent = player.currentTime > 0 ? 'Pausiert' : 'Bereit zur Wiedergabe';

  if (obs) {
    const audible = playing && player.readyState >= HTMLMediaElement.HAVE_FUTURE_DATA;
    obs.banner.dataset.state = audible ? 'playing' : 'idle';
    obs.play.setAttribute('aria-label', label);
    if (player.error) obs.status.textContent = 'Ladefehler';
    else if (audible) obs.status.textContent = 'Läuft gerade';
    else if (playing) obs.status.textContent = 'Lädt …';
    else obs.status.textContent = player.currentTime > 0 ? 'Pausiert' : 'Bereit';
  }
}

function togglePlayback() {
  if (player.paused) play();
  else player.pause();
}

albumSelector.addEventListener('click', () => {
  for (const checkbox of albumCheckboxes) checkbox.checked = selectedAlbums.includes(checkbox.value);
  albumDialog.returnValue = '';
  albumDialog.showModal();
});
albumDialog.addEventListener('close', () => {
  if (albumDialog.returnValue === 'all') applyAlbums([]);
  if (albumDialog.returnValue === 'apply') {
    applyAlbums(albumCheckboxes.filter((checkbox) => checkbox.checked).map((checkbox) => checkbox.value));
  }
  if (['all', 'apply'].includes(albumDialog.returnValue) && player.paused && !songsByKey.has(currentSong?.key)) {
    selectSong(playlist[0], false, false);
  }
});
songSelector.addEventListener('change', () => selectSong(songsByKey.get(songSelector.value)));
songSearch.addEventListener('input', () => {
  renderSongList();
  updateLibrarySelection();
});
songList.addEventListener('click', (event) => {
  const row = event.target.closest('.song-row');
  if (row) selectSong(songsByKey.get(row.dataset.song));
});
previousButton.addEventListener('click', previousSong);
document.getElementById('nextBtn').addEventListener('click', nextSong);
playButton.addEventListener('click', togglePlayback);
loopButton.addEventListener('click', () => {
  repeat = !repeat;
  player.loop = repeat && songsByKey.has(currentSong?.key);
  loopButton.setAttribute('aria-pressed', repeat);
});
shuffleButton.addEventListener('click', () => setShuffle(!shuffle));
volume.addEventListener('input', () => {
  player.volume = Number(volume.value);
});
player.addEventListener('volumechange', () => {
  volume.value = player.volume;
  volume.style.setProperty('--filled', `${100 * player.volume}%`);
});
progress.addEventListener('input', () => {
  if (!progress.disabled) player.currentTime = Number(progress.value);
});
player.addEventListener('durationchange', updateTime);
player.addEventListener('timeupdate', updateTime);
for (const event of ['play', 'pause', 'error']) player.addEventListener(event, updatePlaybackState);
if (obs) {
  const root = document.documentElement;
  const options = document.getElementById('obsOptions');
  const close = document.getElementById('obsClose');
  obs.play.addEventListener('click', togglePlayback);
  options.addEventListener('click', () => {
    root.classList.add('obs-controls');
    albumSelector.focus();
  });
  const showOverlay = () => {
    root.classList.remove('obs-controls');
    options.focus();
  };
  close.addEventListener('click', showOverlay);
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && !albumDialog.open && root.classList.contains('obs-controls') && event.target.tagName !== 'SELECT') {
      showOverlay();
    }
  });
  for (const event of ['playing', 'waiting']) player.addEventListener(event, updatePlaybackState);
}
player.addEventListener('ended', nextSong);

newestButton?.addEventListener('click', () => selectSong(newestSong));
sebiButton.hidden = !sebiSong || Boolean(obs);
sebiButton.addEventListener('click', () => {
  if (!sebiSong) return;
  applyAlbums([sebiSong.album]);
  selectSong(sebiSong);
});

shareButton.addEventListener('click', async () => {
  const url = new URL(window.location.href);
  url.search = '';
  url.hash = '';
  url.searchParams.set('song', currentSong.key);
  try {
    await navigator.clipboard.writeText(url.href);
    clearTimeout(shareTimer);
    shareLabel.textContent = 'Link kopiert!';
    shareTimer = setTimeout(() => { shareLabel.textContent = 'Link kopieren'; }, 1500);
  } catch {
    window.prompt('Link zum Kopieren', url.href);
  }
});

if ('mediaSession' in navigator) {
  const actions = {
    play,
    pause: () => player.pause(),
    previoustrack: previousSong,
    nexttrack: nextSong,
    seekbackward: ({ seekOffset = 10 }) => { player.currentTime = Math.max(0, player.currentTime - seekOffset); },
    seekforward: ({ seekOffset = 10 }) => {
      if (Number.isFinite(player.duration)) {
        player.currentTime = Math.min(player.duration, player.currentTime + seekOffset);
      }
    },
    stop: () => player.pause(),
  };
  for (const [action, handler] of Object.entries(actions)) {
    try {
      navigator.mediaSession.setActionHandler(action, handler);
    } catch {
      // Browsers can expose Media Session while supporting only some actions.
    }
  }
}

applyAlbums(loadAlbums(), false);
const requestedSong = params.get('song');
const requestedShuffle = params.get('shuffle');
setShuffle(requestedShuffle === null ? Boolean(obs) : ['1', 'true', 'on'].includes(requestedShuffle));
const startSong = (obs ? songsByKey.get(requestedSong) : allSongs.find((song) => song.key === requestedSong))
  ?? playlist[shuffle ? Math.floor(Math.random() * playlist.length) : 0];
selectSong(startSong, true, false);
