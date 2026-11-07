<?php
// counter.php - Korrigierte Version mit File Locking und Sekunden-Zählung
header('Content-Type: application/json');

// Caching verhindern (wichtig, da wir Daten schreiben!)
header("Cache-Control: no-store, no-cache, must-revalidate, max-age=0");
header("Cache-Control: post-check=0, pre-check=0", false);
header("Pragma: no-cache");

$counterFile = __DIR__ . '/counter.json';

// Sicherstellen, dass die Datei existiert und initialisiert ist
if (!file_exists($counterFile)) {
    file_put_contents($counterFile, json_encode(['songs' => [], 'global' => ['streams' => 0, 'minutes' => 0]], JSON_PRETTY_PRINT));
}

// Parameter auslesen (Unterstützung für POST und GET)
$action = $_REQUEST['action'] ?? null;
$songKey = $_REQUEST['song'] ?? null;

// Initial Response
$response = ['success' => false];

if ($action && $songKey) {
    // DATEI SPERREN (Wichtig gegen Race Conditions/Datenverlust!)
    // c+ zum Lesen und Schreiben, Cursor am Anfang
    $fp = fopen($counterFile, 'c+'); 
    
    // Versuche, eine exklusive Sperre zu erhalten (LOCK_EX)
    if ($fp && flock($fp, LOCK_EX)) { 
        
        // Dateiinhalt lesen
        $fileContent = stream_get_contents($fp);
        // Fallback, falls JSON leer/defekt ist
        $counter = json_decode($fileContent, true) ?: ['songs' => [], 'global' => ['streams' => 0, 'minutes' => 0]];

        // Song initialisieren falls nötig
        if (!isset($counter['songs'][$songKey])) {
            $counter['songs'][$songKey] = ['streams' => 0, 'minutes' => 0];
        }

        $saveNeeded = false;

        if ($action === 'stream') {
            // Zählt den Stream bei 80%
            $counter['songs'][$songKey]['streams'] += 1;
            $counter['global']['streams'] += 1;
            $saveNeeded = true;
        } 
        elseif ($action === 'seconds') { 
            // Zählt die Streaming-Zeit in Sekunden und rechnet in Minuten um
            $seconds = (int)($_REQUEST['seconds'] ?? 0);
            if ($seconds > 0) {
                // Umrechnen in Minuten (als Float speichern für Genauigkeit)
                $minutesToAdd = round($seconds / 60, 4);
                
                $counter['songs'][$songKey]['minutes'] += $minutesToAdd;
                $counter['global']['minutes'] += $minutesToAdd;
                $saveNeeded = true;
            }
        }

        // Speichern
        if ($saveNeeded) {
            ftruncate($fp, 0); // Datei leeren
            rewind($fp); // Zum Anfang
            fwrite($fp, json_encode($counter, JSON_PRETTY_PRINT));
            $response['success'] = true;
        }

        // Response Daten vorbereiten
        $response['song'] = $counter['songs'][$songKey];
        $response['global'] = $counter['global'];

        flock($fp, LOCK_UN); // Sperre lösen
    }
    fclose($fp);
} elseif ($action === 'get' && $songKey) {
    // Nur lesen (Ohne Schreib-Lock, aber mit Lese-Lock zur Sicherheit)
    $fp = fopen($counterFile, 'r');
    if ($fp && flock($fp, LOCK_SH)) { // Lese-Lock
        $content = stream_get_contents($fp);
        $counter = json_decode($content, true);
        
        $response['success'] = true;
        $response['song'] = $counter['songs'][$songKey] ?? ['streams' => 0, 'minutes' => 0];
        $response['global'] = $counter['global'] ?? ['streams' => 0, 'minutes' => 0];
        
        flock($fp, LOCK_UN);
    }
    fclose($fp);
}

echo json_encode($response);
?>