const { albums, albumKeys, allSongs, initialStats, newestSong } =
  JSON.parse(document.getElementById('catalog').textContent);

const albumSelector = document.getElementById('albumSelector');
const songSelector = document.getElementById('songSelector');
const player = document.getElementById('player');
const cover = document.getElementById('cover');
const metaTitle = document.getElementById('meta-title');
const timeDisplay = document.getElementById('timeDisplay');
const shuffleButton = document.getElementById('shuffleBtn');
const loopButton = document.getElementById('loopBtn');
const shareButton = document.getElementById('shareButton');

let playlist = allSongs;
let currentIndex = 0;
let shuffle = false;
const history = [];

function formatStreamingTime(minutes) {
  let seconds = Math.round(minutes * 60);
  const units = [
    [31_536_000, 'Jahr', 'Jahre'],
    [86_400, 'Tag', 'Tage'],
    [3_600, 'Std', 'Std'],
    [60, 'Min', 'Min'],
    [1, 'Sek', 'Sek'],
  ];
  const parts = [];
  for (const [size, singular, plural] of units) {
    const count = Math.floor(seconds / size);
    if (count > 0) parts.push(`${count} ${count === 1 ? singular : plural}`);
    seconds %= size;
  }
  return parts.join(' ') || '0 Min';
}

function showStats(prefix, stats) {
  document.getElementById(`${prefix}Streams`).textContent = stats?.streams ?? 0;
  document.getElementById(`${prefix}Minutes`).textContent = formatStreamingTime(stats?.minutes ?? 0);
}

function selectAlbum(value) {
  playlist = value === 'all' ? allSongs : albums[albumKeys[Number(value)]];
  albumSelector.value = value;
  songSelector.replaceChildren();
  playlist.forEach((song, index) => {
    const label = value === 'all' ? `${song.album} - ${song.title}` : song.title;
    songSelector.add(new Option(label, index));
  });
  songSelector.disabled = false;
  history.length = 0;
}

function selectSong(index, autoplay = true, remember = true) {
  const song = playlist[index];
  if (!song) return;
  if (remember && index !== currentIndex) history.push(currentIndex);
  currentIndex = index;
  songSelector.value = index;
  player.src = song.file;
  cover.style.backgroundImage = `url("${song.cover}")`;
  metaTitle.textContent = `Titel: ${song.title}`;
  document.title = `${song.title} – Schwafelpatie FM`;
  showStats('song', initialStats.songs?.[song.key]);

  if ('mediaSession' in navigator && 'MediaMetadata' in window) {
    navigator.mediaSession.metadata = new MediaMetadata({
      title: song.title,
      artist: 'Schwafelpatie FM',
      album: song.album,
      artwork: [{ src: new URL(song.cover, document.baseURI).href, type: 'image/jpeg' }],
    });
  }
  if (autoplay) play();
}

function play() {
  player.play().catch(() => {});
}

function previousSong() {
  const index = history.pop() ?? (currentIndex - 1 + playlist.length) % playlist.length;
  selectSong(index, true, false);
}

function nextSong() {
  let index = (currentIndex + 1) % playlist.length;
  if (shuffle && playlist.length > 1) {
    index = (currentIndex + 1 + Math.floor(Math.random() * (playlist.length - 1))) % playlist.length;
  }
  selectSong(index);
}

function setShuffle(enabled) {
  shuffle = enabled;
  shuffleButton.classList.toggle('active', enabled);
  shuffleButton.setAttribute('aria-pressed', enabled);
}

function formatTime(time) {
  const seconds = Number.isFinite(time) ? Math.floor(time) : 0;
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`;
}

function updateTime() {
  timeDisplay.textContent = `${formatTime(player.currentTime)} / ${formatTime(player.duration)}`;
}

albumSelector.addEventListener('change', () => {
  selectAlbum(albumSelector.value);
  selectSong(0, true, false);
});
songSelector.addEventListener('change', () => selectSong(Number(songSelector.value)));
document.getElementById('prevBtn').addEventListener('click', previousSong);
document.getElementById('nextBtn').addEventListener('click', nextSong);
document.getElementById('playBtn').addEventListener('click', () => {
  if (player.paused) play();
  else player.pause();
});
loopButton.addEventListener('click', () => {
  player.loop = !player.loop;
  loopButton.classList.toggle('active', player.loop);
  loopButton.setAttribute('aria-pressed', player.loop);
});
shuffleButton.addEventListener('click', () => setShuffle(!shuffle));
document.getElementById('volumeSlider').addEventListener('input', (event) => {
  player.volume = Number(event.target.value);
});
player.addEventListener('loadedmetadata', updateTime);
player.addEventListener('timeupdate', updateTime);
player.addEventListener('ended', nextSong);

document.getElementById('newestSongBtn')?.addEventListener('click', () => {
  selectAlbum('all');
  selectSong(allSongs.findIndex((song) => song.key === newestSong.key), true, false);
});

shareButton.addEventListener('click', async () => {
  const url = new URL(window.location.href);
  url.search = '';
  url.hash = '';
  url.searchParams.set('song', playlist[currentIndex].key);
  try {
    await navigator.clipboard.writeText(url.href);
    shareButton.textContent = '✅ Link kopiert!';
    setTimeout(() => { shareButton.textContent = '🔗 Link kopieren'; }, 1500);
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

showStats('global', initialStats.global);
selectAlbum('all');
const params = new URLSearchParams(window.location.search);
const requestedSong = params.get('song');
setShuffle(['1', 'true', 'on'].includes(params.get('shuffle')));
const requestedIndex = allSongs.findIndex((song) => song.key === requestedSong);
const startIndex = !requestedSong && shuffle ? Math.floor(Math.random() * allSongs.length) : Math.max(0, requestedIndex);
selectSong(startIndex, true, false);
