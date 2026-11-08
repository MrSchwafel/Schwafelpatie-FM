<?php
// Generate a self-contained GitHub Pages site from the local music library.
declare(strict_types=1);

$root = dirname(__DIR__);
chdir($root);
$output = $argv[1] ?? $root . '/dist';
if (file_exists($output) || is_link($output)) {
    throw new RuntimeException("Build-Verzeichnis existiert bereits: $output");
}

function readJson(string $path): array {
    return json_decode(file_get_contents($path), true, 512, JSON_THROW_ON_ERROR);
}

function songKey(string $name): string {
    $name = strtr($name, ['ä' => 'ae', 'ö' => 'oe', 'ü' => 'ue', 'ß' => 'ss', 'Ä' => 'Ae', 'Ö' => 'Oe', 'Ü' => 'Ue']);
    return trim(preg_replace('/[^a-z0-9]+/', '-', strtolower($name)), '-');
}

function assetUrl(string $path): string {
    return implode('/', array_map('rawurlencode', explode('/', $path)));
}

function copyAsset(string $root, string $output, string $path): void {
    $destination = "$output/$path";
    $directory = dirname($destination);
    if ((!is_dir($directory) && !mkdir($directory, 0777, true)) || !copy("$root/$path", $destination)) {
        throw new RuntimeException("Datei konnte nicht übernommen werden: $path");
    }
}

$files = [];
$iterator = new RecursiveIteratorIterator(new RecursiveDirectoryIterator("$root/songs/standard"));
foreach ($iterator as $file) {
    if ($file->isFile() && strtolower($file->getExtension()) === 'mp3') {
        $path = substr($file->getPathname(), strlen($root) + 1);
        $files[str_replace(DIRECTORY_SEPARATOR, '/', $path)] = $file->getMTime();
    }
}
if (!$files) {
    throw new RuntimeException('Keine MP3s in songs/standard gefunden.');
}
ksort($files, SORT_STRING);

// Existing shared links retain their keys. New MP3s are discovered automatically.
$keys = array_intersect(readJson("$root/song_keys.json"), array_keys($files));
$knownPaths = array_flip($keys);
foreach ($files as $path => $mtime) {
    if (isset($knownPaths[$path])) {
        continue;
    }
    $key = songKey(pathinfo($path, PATHINFO_FILENAME));
    if ($key === '' || isset($keys[$key])) {
        throw new RuntimeException("Bitte in song_keys.json eine eindeutige Song-ID für $path vergeben.");
    }
    $keys[$key] = $path;
}

// Git checkouts reset file times. Use the latest commit touching existing MP3s
// in CI, and use file times when building from an unpacked copy.
$candidates = $files;
if (is_dir("$root/.git") || is_file("$root/.git")) {
    $latest = shell_exec('git log -1 --format= --name-only -z --diff-filter=AM -- ":(glob,icase)songs/standard/**/*.mp3"');
    $changed = array_intersect_key($files, array_flip(explode("\0", $latest ?? '')));
    if ($changed) {
        $candidates = $changed;
    }
}
arsort($candidates, SORT_NUMERIC);
$newestPath = array_key_first($candidates);
// Preserve the existing newest track if the first commit imports the whole library.
$originalNewest = $keys['likegeil'] ?? '';
if (count($candidates) === count($files) && ($candidates[$originalNewest] ?? -1) === $candidates[$newestPath]) {
    $newestPath = $originalNewest;
}

// The bundled getID3 version emits deprecation notices on newer PHP versions.
$errorReporting = error_reporting(E_ALL & ~E_DEPRECATED);
require_once "$root/getid3/getid3.php";
$getID3 = new getID3();
$albums = [];
$newestSong = null;
$assets = array_fill_keys(array_keys($files), true);
$assets['default-cover.jpg'] = true;
$assets['assets/logo.svg'] = true;
$assets['pages/player.js'] = true;
$assets['pages/player.css'] = true;
$androidApk = 'downloads/Schwafelpatie-FM.apk';
$apkVersion = null;
if (is_file("$root/$androidApk")) {
    $assets[$androidApk] = true;
    $apkVersion = substr(hash_file('sha256', "$root/$androidApk"), 0, 16);
}
foreach ($keys as $key => $path) {
    $album = basename(dirname($path));
    $info = $getID3->analyze("$root/$path");
    $title = $info['tags']['id3v2']['title'][0] ?? $info['tags']['id3']['title'][0] ?? pathinfo($path, PATHINFO_FILENAME);
    $cover = "songs/$album/cover.jpg";
    if (is_file("$root/$cover")) {
        $assets[$cover] = true;
    } else {
        $cover = 'default-cover.jpg';
    }
    $song = [
        'key' => (string) $key,
        'file' => assetUrl($path),
        'version' => hash_file('sha256', "$root/$path"),
        'title' => $title,
        'album' => $album,
        'cover' => assetUrl($cover),
    ];
    $albums[$album][] = $song;
    if ($path === $newestPath) {
        $newestSong = $song;
    }
}
error_reporting($errorReporting);

$albumKeys = array_keys($albums);
sort($albumKeys, SORT_STRING);
$allSongs = array_merge(...array_values($albums));
$styleVersion = substr(hash_file('sha256', "$root/pages/player.css"), 0, 16);
$scriptVersion = substr(hash_file('sha256', "$root/pages/player.js"), 0, 16);
$catalogJson = json_encode(
    compact('albums', 'albumKeys', 'allSongs', 'newestSong'),
    JSON_THROW_ON_ERROR | JSON_HEX_TAG | JSON_INVALID_UTF8_SUBSTITUTE
);

ob_start();
require "$root/pages/player.php";
$html = ob_get_clean();

$totalBytes = strlen($html) + strlen($catalogJson);
foreach (array_keys($assets) as $path) {
    if (!is_file("$root/$path")) {
        throw new RuntimeException("Datei fehlt: $path");
    }
    $totalBytes += filesize("$root/$path");
}
if ($totalBytes >= 1_000_000_000) {
    throw new RuntimeException('Die Website überschreitet das 1-GB-Limit von GitHub Pages.');
}

foreach (array_keys($assets) as $path) {
    copyAsset($root, $output, $path);
}
if (file_put_contents("$output/index.html", $html) === false) {
    throw new RuntimeException('index.html konnte nicht geschrieben werden.');
}
if (file_put_contents("$output/catalog.json", $catalogJson) === false) {
    throw new RuntimeException('catalog.json konnte nicht geschrieben werden.');
}

printf("%d MP3s, %.1f MB → %s\n", count($files), $totalBytes / 1_000_000, $output);
