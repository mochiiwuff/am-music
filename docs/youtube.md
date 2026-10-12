# Talking to YouTube Music

Notes from October 2026, based on Metrolist, its `innertubex` library (v0.8.4) and SimpMusic.
Both projects are active. Metrolist is Android only and is the main reference.

## Audio streams

Main method: `innertubex` (`com.github.MetrolistGroup.innertubex:innertubex`, JitPack, GPL-3).
It is Metrolist's extraction engine published as a library. It takes care of:

- choosing an InnerTube client and falling back from one to the next. Automatic clients by priority:
  `TVHTML5` (75) → `WEB_REMIX` (70) → `WEB_EMBEDDED_PLAYER` (65) → `WEB_CREATOR` (55) → `TVHTML5_SIMPLY` (42).
  The others (`ANDROID_VR`, `IOS`…) are only probed
- signature and `n` parameter deciphering: the YouTube player JS runs on the device (QuickJS). Function names come
  from a remote table (`player_configs.json`), with a fallback on parsing the script and on a cache
- the PoToken contract: the app provides a `TokenProvider`

Settings taken from Metrolist (`InnerTubeXPlayer.kt`): `allowSabr = false`, `allowHls = false`
(ExoPlayer reads direct URLs), bounded range requests when the stream requires them.

PoToken: generated in a hidden WebView (BotGuard), code taken from `Metrolist/app/.../utils/potoken/` (4 files).
It waits 8 s at most, then carries on without a token.

403 / 410 errors during playback (Metrolist's `MusicService.kt`):

1. drop the cached URL for the song
2. mark the client as failed for that song for 5 minutes
3. refresh the deciphering table (`refreshAfterStreamRejection`)
4. retry from the same position, a limited number of times

Fallback: NewPipeExtractor (`com.github.teamnewpipe:NewPipeExtractor`), as SimpMusic does. Only used when
`innertubex` returns no playable stream.

Final chain: `innertubex` → NewPipeExtractor → error message with a retry button.
It all lives in the `youtube` module behind `StreamResolver`.

## Browsing (home, search, pages, library)

InnerTube `browse` / `search` / `next` / `player` requests with the `WEB_REMIX` client.
The code is Metrolist's `innertube` module (requests and response parsing). HTTP is Ktor on OkHttp with kotlinx.serialization.

Things to watch for, seen in SimpMusic:

- when signed in, radio queues come in `playlistPanelVideoWrapperRenderer`
- `musicVideoType` is in `watchEndpointMusicSupportedConfigs` (tells a song from a music video)
- the "More" button of a section carries its page type in `browseEndpointContextMusicConfig.pageType`

## Sign-in

WebView on `https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com`.
Once on `music.youtube.com`:

- cookies: `CookieManager.getCookie("https://music.youtube.com")`
- `VISITOR_DATA` and `DATASYNC_ID` are read with JavaScript from `window.yt.config_`

The `Authorization: SAPISIDHASH …` header is computed from the `SAPISID` cookie.
The session is stored encrypted with an Android Keystore key. Signing out clears the cookies and the WebView.

## Lyrics

LRCLIB is the timing reference (line synced). LyricsPlus, then BetterLyrics, provide word-level timing, which is
only used when it can be aligned on the LRCLIB lines. Unsynced lyrics are shown as plain text.

## SponsorBlock

Optional. Uses the hashed endpoint `sponsor.ajay.app/api/skipSegments/<first 4 hex chars of sha256(videoId)>`,
categories `music_offtopic`, `intro`, `outro`, `sponsor`, `selfpromo`, `interaction`, `preview`.

## When it breaks

See `MAINTENANCE.md`. First thing to try: bump `innertubex`, then NewPipeExtractor.
