# Accès à YouTube Music — méthode retenue

Recherche faite le 2026-10-10 sur Metrolist (commit 2026-10-08), sa librairie
`innertubex` v0.8.4 et SimpMusic (commit 2026-10-11, sous-module `core`).
Les deux projets sont actifs ; Metrolist (Android pur) sert de modèle principal.

## Flux audio

**Méthode principale : `innertubex`** (`com.github.MetrolistGroup.innertubex:innertubex`, JitPack, GPL-3).
C'est le moteur d'extraction de Metrolist, publié en librairie. Il gère seul :
- le choix du client InnerTube et le repli de l'un à l'autre. Clients automatiques,
  par priorité : `TVHTML5` (75) → `WEB_REMIX` (70) → `WEB_EMBEDDED_PLAYER` (65) →
  `WEB_CREATOR` (55) → `TVHTML5_SIMPLY` (42). Les autres (`ANDROID_VR`, `IOS`…) sont en sonde seulement ;
- le déchiffrement de signature et du paramètre `n` : le JS du lecteur YouTube est exécuté
  sur l'appareil (QuickJS), les noms des fonctions venant d'une table distante
  (`player_configs.json`) avec repli sur l'analyse du script et sur un cache ;
- le contrat PoToken : l'appli fournit un `TokenProvider`.

Réglages repris de Metrolist (`InnerTubeXPlayer.kt`) : `allowSabr = false`, `allowHls = false`
(ExoPlayer lit des URL directes), requêtes par plages bornées quand le flux l'exige.

**PoToken** : généré dans une WebView cachée (BotGuard), code repris de
`Metrolist/app/.../utils/potoken/` (4 fichiers). Délai max 8 s, sinon on continue sans jeton.

**Erreurs 403 / 410 pendant la lecture** (`MusicService.kt` de Metrolist) :
1. vider l'URL en cache pour ce titre ;
2. marquer le client fautif comme « en échec » pour ce titre pendant 5 min ;
3. rafraîchir la table de déchiffrement (`refreshAfterStreamRejection`) ;
4. relancer à la même position, avec un nombre d'essais limité.

**Secours n° 2 : NewPipeExtractor** (`com.github.teamnewpipe:NewPipeExtractor`), méthode de SimpMusic
(qui enchaîne PipePipe local → PipePipe via serveur → BravePipe, avec un test HEAD de l'URL).
Utilisé seulement si `innertubex` ne renvoie aucun flux lisible.

Chaîne finale dans notre appli : `innertubex` (5 clients) → NewPipeExtractor → message d'erreur + « Réessayer ».
Toute la chaîne vit dans le module `youtube`, derrière une seule interface `StreamResolver`.

## Navigation (accueil, recherche, pages, bibliothèque)

Requêtes InnerTube `browse` / `search` / `next` / `player` avec le client `WEB_REMIX`.
On adapte le module `innertube` de Metrolist (82 fichiers, ~9 400 lignes : requêtes + analyse des réponses),
en ne gardant que ce qui sert. HTTP : Ktor (moteur OkHttp) + kotlinx.serialization, comme eux.

Pièges relevés chez SimpMusic :
- connecté, les files radio arrivent dans `playlistPanelVideoWrapperRenderer` (à lire, sinon 80 % des titres disparaissent) ;
- `musicVideoType` se lit dans `watchEndpointMusicSupportedConfigs` (sert à distinguer titre / clip) ;
- le bouton « Plus » d'une section porte son type de page dans `browseEndpointContextMusicConfig.pageType`.

## Connexion au compte

WebView sur `https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com`
(`LoginScreen.kt` de Metrolist). Une fois sur `music.youtube.com` :
- cookies : `CookieManager.getCookie("https://music.youtube.com")` ;
- `VISITOR_DATA` et `DATASYNC_ID` lus en JavaScript dans `window.yt.config_`.
L'en-tête `Authorization: SAPISIDHASH …` est calculé à partir du cookie `SAPISID`.
Stockage chez nous : chiffré avec une clé Android Keystore. Déconnexion = effacer cookies + WebView.

## Paroles

Sources, dans l'ordre : LRCLIB (synchronisé ligne par ligne) → BetterLyrics (TTML, mot par mot) →
KuGou → paroles YouTube Music (non synchronisées) → sous-titres YouTube.
Metrolist a aussi LyricsPlus et Paxsenix (mot par mot) : à ajouter si besoin.

## SponsorBlock

API publique `sponsor.ajay.app/api/skipSegments`, catégorie `music_offtopic` (+ intro / outro).

## Quand ça casse

Voir `MAINTENANCE.md`. Premier réflexe : monter la version d'`innertubex` puis de NewPipeExtractor.
