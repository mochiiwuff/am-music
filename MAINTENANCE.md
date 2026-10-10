# Quand YouTube casse la lecture

Le moteur YouTube arrive en phase 2 ; ce fichier sera complété à ce moment-là.
La méthode retenue est décrite dans `docs/youtube.md`.

Marche à suivre prévue :
1. Regarder si Metrolist a publié une nouvelle version d'`innertubex`
   (github.com/MetrolistGroup/innertubex) et monter le numéro dans `gradle/libs.versions.toml`.
2. Si ça ne suffit pas, faire pareil pour NewPipeExtractor (github.com/TeamNewPipe/NewPipeExtractor).
3. Recompiler, réinstaller, tester un titre.
4. Toujours cassé : regarder les derniers commits de Metrolist et SimpMusic sur la lecture
   et reporter le correctif dans le module `youtube`.
