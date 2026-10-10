package app.ammusic.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.core.content.edit
import app.ammusic.data.library.LibraryRepository
import app.ammusic.youtube.YouTubeMusic
import app.ammusic.youtube.model.YtAccount
import app.ammusic.youtube.model.YtSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SavedAccount(
    val cookie: String,
    val visitorData: String?,
    val dataSyncId: String?,
    val name: String,
    val email: String?,
    val avatar: String?,
)

/**
 * Compte YouTube Music connecté (ou aucun : mode invité).
 * Les cookies sont enregistrés chiffrés, avec une clé gardée par l'Android Keystore : elle ne sort jamais du téléphone.
 */
@Singleton
class AccountStore @Inject constructor(
    @ApplicationContext context: Context,
    private val library: LibraryRepository,
) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _account = MutableStateFlow<YtAccount?>(null)
    val account: StateFlow<YtAccount?> = _account.asStateFlow()

    /** Au démarrage de l'appli : reprend la session enregistrée, avant toute requête à YouTube. */
    fun restore() {
        val saved = prefs.getString(Key, null)?.let(::decrypt)?.let { runCatching { Json.decodeFromString<SavedAccount>(it) }.getOrNull() } ?: return
        YouTubeMusic.signIn(YtSession(saved.cookie, saved.visitorData, saved.dataSyncId))
        _account.value = YtAccount(saved.name, saved.email, saved.avatar)
        scope.launch { library.sync() }
    }

    /** Appelé par l'écran de connexion une fois les cookies récupérés. */
    suspend fun signIn(session: YtSession) {
        YouTubeMusic.signIn(session)
        val info = YouTubeMusic.account().getOrNull() ?: YtAccount("Mon compte", null, null)
        val saved = SavedAccount(session.cookie, session.visitorData, session.dataSyncId, info.name, info.email, info.avatar)
        prefs.edit { putString(Key, encrypt(Json.encodeToString(SavedAccount.serializer(), saved))) }
        _account.value = info
        scope.launch { library.sync() }
    }

    /** Efface la session, les cookies de la fenêtre de connexion et ce qui venait du compte dans la bibliothèque. */
    suspend fun signOut() {
        YouTubeMusic.signIn(null)
        prefs.edit { remove(Key) }
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
        library.clearRemote()
        _account.value = null
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getEntry(Alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val spec = KeyGenParameterSpec.Builder(Alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec) }.generateKey()
    }

    /** Résultat : le vecteur d'initialisation (12 octets) suivi du texte chiffré, en Base64. */
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(Transformation).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? = runCatching {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(Transformation).apply {
            init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IvSize))
        }
        String(cipher.doFinal(bytes, IvSize, bytes.size - IvSize))
    }.getOrNull()

    private companion object {
        const val Key = "session"
        const val Alias = "am_account"
        const val Transformation = "AES/GCM/NoPadding"
        const val IvSize = 12
    }
}
