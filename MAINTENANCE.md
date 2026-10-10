# Quand YouTube casse la lecture

Tout le code concerné est dans le module `youtube`. La méthode est décrite dans `docs/youtube.md`.

## 1. Regarder ce qui échoue
Brancher le téléphone, lancer un titre, puis :

    adb logcat -d -s AmPlayback:V

- `Flux … via innertubex/CLIENT` : la méthode principale marche.
- `Flux … via newpipe/…` : la méthode principale a échoué, le secours a pris le relais.
- `Erreur de lecture … (essai n)` : l'URL obtenue est refusée (souvent une erreur 403).

## 2. Mettre à jour les librairies (le plus souvent, ça suffit)
Dans `gradle/libs.versions.toml` :
- `innertubex` : dernière version sur github.com/MetrolistGroup/innertubex (onglet Releases).
- `newpipe` : dernière version sur github.com/TeamNewPipe/NewPipeExtractor.

Puis recompiler, réinstaller, tester un titre.

## 3. Mettre à jour le code repris de Metrolist
Si l'accueil ou la recherche ne s'affichent plus, ou si l'étape 2 ne compile pas :

    cd references/Metrolist && git pull
    cp -r innertube/src/main/kotlin/com ../../youtube/src/main/kotlin/
    cp -r app/src/main/kotlin/com/metrolist/music/utils/potoken ../../youtube/src/main/kotlin/com/metrolist/music/utils/
    cp app/src/main/assets/po_token.html ../../youtube/src/main/assets/

Dans `PoTokenWebView.kt`, retirer l'import de `BuildConfig` et remplacer `BuildConfig.DEBUG` par `false`.
Si `InnerTubeXPlayer.kt` de Metrolist a changé, reporter les différences dans
`youtube/src/main/kotlin/app/ammusic/youtube/stream/InnerTubeXSource.kt` (c'est la même logique, allégée).

## 4. Toujours cassé
Regarder les derniers commits de Metrolist et de SimpMusic sur la lecture, et reporter le correctif
dans le module `youtube`.

## À savoir
- Media3 : Metrolist reste en 1.10.1 car la 1.11.1 masquerait les commandes média sur Android 17.
  Nous sommes en 1.11.1 (téléphone en Android 16). À revoir si le téléphone passe à Android 17.
