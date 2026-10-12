# When YouTube breaks playback

All the code involved is in the `youtube` module. How it works is described in `docs/youtube.md`.

## 1. See what fails

Plug the phone in, play a song, then:

    adb logcat -d -s AmPlayback:V

- `Flux … via innertubex/CLIENT`: the main method works.
- `Flux … via newpipe/…`: the main method failed and the fallback took over.
- `Erreur de lecture … (essai n)`: the stream URL was rejected (usually a 403).

## 2. Update the libraries (usually enough)

In `gradle/libs.versions.toml`:

- `innertubex`: latest release on github.com/MetrolistGroup/innertubex
- `newpipe`: latest release on github.com/TeamNewPipe/NewPipeExtractor

Rebuild, reinstall, try a song.

## 3. Update the code taken from Metrolist

If home or search stop loading, or if step 2 does not compile:

    cd references/Metrolist && git pull
    cp -r innertube/src/main/kotlin/com ../../youtube/src/main/kotlin/
    cp -r app/src/main/kotlin/com/metrolist/music/utils/potoken ../../youtube/src/main/kotlin/com/metrolist/music/utils/
    cp app/src/main/assets/po_token.html ../../youtube/src/main/assets/

In `PoTokenWebView.kt`, remove the `BuildConfig` import and replace `BuildConfig.DEBUG` with `false`.
If Metrolist's `InnerTubeXPlayer.kt` changed, port the differences to
`youtube/src/main/kotlin/app/ammusic/youtube/stream/InnerTubeXSource.kt` (same logic, trimmed down).

## 4. Still broken

Look at the latest playback commits in Metrolist and SimpMusic and port the fix to the `youtube` module.

## Notes

- Media3: Metrolist stays on 1.10.1 because 1.11.1 reportedly hides the media controls on Android 17.
  This project uses 1.11.1 (tested on Android 16). To revisit on Android 17.
