const { albums, albumKeys, allSongs, newestSong } =
  JSON.parse(document.getElementById('catalog').textContent);

const albumSelector = document.getElementById('albumSelector');
const songSelector = document.getElementById('songSelector');
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

let playlist = allSongs;
let currentIndex = 0;
let shuffle = false;
const history = [];
let shareTimer;

function selectAlbum(value) {
  playlist = value === 'all' ? allSongs : albums[albumKeys[Number(value)]];
  albumSelector.value = value;
  trackCount.textContent = `${playlist.length} ${playlist.length === 1 ? 'Song' : 'Songs'}`;
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
  cover.src = song.cover;
  metaTitle.textContent = song.title;
  metaAlbum.textContent = song.album;
  document.title = `${song.title} – Schwafelpatie FM`;
  updateTime();
  updatePlaybackState();

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
  progress.style.setProperty('--filled', `${100 * Number(progress.value) / Number(progress.max)}%`);
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
}

albumSelector.addEventListener('change', () => {
  selectAlbum(albumSelector.value);
  selectSong(0, true, false);
});
songSelector.addEventListener('change', () => selectSong(Number(songSelector.value)));
document.getElementById('prevBtn').addEventListener('click', previousSong);
document.getElementById('nextBtn').addEventListener('click', nextSong);
playButton.addEventListener('click', () => {
  if (player.paused) play();
  else player.pause();
});
loopButton.addEventListener('click', () => {
  player.loop = !player.loop;
  loopButton.setAttribute('aria-pressed', player.loop);
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

selectAlbum('all');
const params = new URLSearchParams(window.location.search);
const requestedSong = params.get('song');
setShuffle(['1', 'true', 'on'].includes(params.get('shuffle')));
const requestedIndex = allSongs.findIndex((song) => song.key === requestedSong);
const startIndex = !requestedSong && shuffle ? Math.floor(Math.random() * allSongs.length) : Math.max(0, requestedIndex);
selectSong(startIndex, true, false);
