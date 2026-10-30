package com.retroforge.pcplayer.steam

import android.content.Context
import android.provider.Settings
import android.util.Base64
import android.util.Log
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.authentication.AuthPollResult
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IAuthenticator
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.QrAuthSession
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CompletableFuture
import kotlin.concurrent.thread

/**
 * The user's Steam account, for Steam games in RetroForge's PC player: sign-in (approve in the Steam app, account
 * name + password with Steam Guard, or a QR code for another device), done against Steam's own login service with
 * JavaSteam. RetroForge keeps only the refresh token Steam issues (in its private storage), never the password.
 *
 * At a game's start the player signs in with that token and asks Steam for the game's encrypted app ticket, which
 * only an account that owns the game gets (see [SteamGameSetup]).
 */
class SteamAccount(private val context: Context) {

    private val prefs = context.getSharedPreferences("pc_steam", Context.MODE_PRIVATE)

    val accountName: String? get() = prefs.getString(KEY_NAME, null)
    val steamId64: Long get() = prefs.getLong(KEY_STEAM_ID, 0)
    val signedIn: Boolean get() = prefs.getString(KEY_TOKEN, null) != null

    fun signOut() = prefs.edit().clear().apply()

    /** What a sign-in needs from the user while it runs. Answered through [SignInPrompts]. */
    interface SignInPrompts {
        /** A Steam Guard code from the Steam app ([email] null) or from an email to [email]. */
        suspend fun code(email: String?, previousWasWrong: Boolean): String
        /** Steam is waiting for the user to approve the sign-in in the Steam app. */
        fun approveInSteamApp()
        /** A QR sign-in link (show it as a QR code, or open it in the Steam app on this phone). Steam renews it. */
        fun qrLink(url: String)
    }

    /** Signs in with account name and password; Steam Guard goes through [prompts]. Returns the account name. */
    suspend fun signInWithPassword(name: String, password: String, prompts: SignInPrompts): String =
        session { client, scope ->
            val details = details(prompts, scope).apply {
                username = name.trim()
                this.password = password
            }
            val auth = client.authentication.beginAuthSessionViaCredentials(details).await()
            finish(client, auth.pollingWaitForResult().await())
        }

    /** Signs in by approving a link in the Steam app (on this phone, or by scanning its QR code). */
    suspend fun signInWithQr(prompts: SignInPrompts): String = session { client, scope ->
        val auth: QrAuthSession = client.authentication.beginAuthSessionViaQR(details(prompts, scope)).await()
        Log.i(TAG, "QR sign-in started; waiting for approval")
        prompts.qrLink(auth.challengeUrl)
        auth.challengeUrlChanged = object : IChallengeUrlChanged {
            override fun onChanged(qrAuthSession: QrAuthSession?) { qrAuthSession?.challengeUrl?.let(prompts::qrLink) }
        }
        val result = auth.pollingWaitForResult().await()
        Log.i(TAG, "QR sign-in approved")
        finish(client, result)
    }

    /** The game's encrypted app ticket (base64), which proves this account owns [appId]; null if Steam gives none. */
    suspend fun encryptedAppTicket(appId: Int): String? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return session { client, _ ->
            logOn(client, accountName.orEmpty(), token)
            val apps = client.getHandler(SteamApps::class.java) ?: throw IOException("Steam's app handler is missing")
            val reply = withTimeoutOrNull(15_000) { apps.requestEncryptedAppTicket(appId, null).await() }
                ?: throw IOException("Steam didn't send the game's ticket in time")
            Log.i(TAG, "Ticket for $appId: ${reply.result}")
            if (reply.result != EResult.OK || reply.encryptedAppTicket == null) null
            else Base64.encodeToString(reply.encryptedAppTicket!!.toByteArray(), Base64.NO_WRAP)
        }
    }

    private fun details(prompts: SignInPrompts, scope: CoroutineScope) = AuthSessionDetails().apply {
        deviceFriendlyName = deviceName()
        clientOSType = EOSType.Android9
        persistentSession = true
        authenticator = object : IAuthenticator {
            override fun getDeviceCode(previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
                scope.future { prompts.code(null, previousCodeWasIncorrect) }

            override fun getEmailCode(email: String?, previousCodeWasIncorrect: Boolean): CompletableFuture<String> =
                scope.future { prompts.code(email ?: "your email", previousCodeWasIncorrect) }

            // Prefer approving in the Steam app: no code to type, and it works with the app on the same phone.
            override fun acceptDeviceConfirmation(): CompletableFuture<Boolean> {
                prompts.approveInSteamApp()
                return CompletableFuture.completedFuture(true)
            }
        }
    }

    /** Signs in with the new token to check it and learn the SteamID, then keeps the token. */
    private suspend fun finish(client: SteamClient, result: AuthPollResult): String {
        Log.i(TAG, "Signing in with the new token")
        val steamId = logOn(client, result.accountName, result.refreshToken)
        Log.i(TAG, "Signed in")
        prefs.edit()
            .putString(KEY_NAME, result.accountName)
            .putString(KEY_TOKEN, result.refreshToken)
            .putLong(KEY_STEAM_ID, steamId)
            .apply()
        return result.accountName
    }

    private suspend fun logOn(client: SteamClient, name: String, refreshToken: String): Long {
        val user = client.getHandler(SteamUser::class.java) ?: throw IOException("Steam's user handler is missing")
        val loggedOn = CompletableDeferred<LoggedOnCallback>()
        val subscription = manager!!.subscribe(LoggedOnCallback::class.java) { loggedOn.complete(it) }
        try {
            user.logOn(
                LogOnDetails(
                    username = name,
                    accessToken = refreshToken,
                    shouldRememberPassword = true,
                    loginID = loginId(),
                    machineName = deviceName(),
                ),
            )
            val result = withTimeoutOrNull(30_000) { loggedOn.await() }
                ?: throw IOException("Steam didn't answer the sign-in in time")
            Log.i(TAG, "Logon result ${result.result}")
            if (result.result != EResult.OK) {
                if (result.result == EResult.AccessDenied || result.result == EResult.Expired || result.result == EResult.InvalidPassword) signOut()
                throw IOException("Steam refused the sign-in (${result.result})")
            }
            return result.clientSteamID?.convertToUInt64() ?: 0
        } finally {
            subscription.close()
        }
    }

    private var manager: CallbackManager? = null

    /** Connects to Steam, runs [block], disconnects. Steam's callbacks are pumped on their own thread meanwhile. */
    private suspend fun <T> session(block: suspend (SteamClient, CoroutineScope) -> T): T = withContext(Dispatchers.IO) {
        ensureCrypto()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val client = SteamClient(CONFIGURATION, scope)
        val callbacks = CallbackManager(client)
        manager = callbacks
        val connected = CompletableDeferred<Unit>()
        val subscriptions = mutableListOf<Closeable>(
            callbacks.subscribe(ConnectedCallback::class.java) { connected.complete(Unit) },
            callbacks.subscribe(DisconnectedCallback::class.java) {
                if (!connected.isCompleted) connected.completeExceptionally(IOException("Couldn't reach Steam"))
            },
        )
        var running = true
        val pump = thread(name = "steam-callbacks", isDaemon = true) {
            while (running) runCatching { callbacks.runWaitCallbacks(500) }
        }
        try {
            client.connect()
            withTimeoutOrNull(30_000) { connected.await() } ?: throw IOException("Couldn't reach Steam's servers")
            Log.i(TAG, "Connected to Steam")
            block(client, scope)
        } finally {
            running = false
            subscriptions.forEach { it.close() }
            runCatching { client.disconnect() }
            pump.join(2_000)
            scope.cancel()
            manager = null
        }
    }

    private fun deviceName(): String =
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) ?: "RetroForge (Android)"

    private fun loginId(): Int = (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "retroforge").hashCode()

    companion object {
        private const val TAG = "PcSteam"

        /**
         * Steam's servers close a quiet connection after about a minute, which would end a sign-in while the user
         * is still approving it; a ping every 15 seconds keeps it open (GameNative does the same).
         */
        private val CONFIGURATION: SteamConfiguration by lazy {
            SteamConfiguration.create {
                it.withConnectionTimeout(60_000L)
                it.withHttpClient(
                    okhttp3.OkHttpClient.Builder()
                        .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .pingInterval(15, java.util.concurrent.TimeUnit.SECONDS)
                        .build(),
                )
            }
        }
        private const val KEY_NAME = "account_name"
        private const val KEY_TOKEN = "refresh_token"
        private const val KEY_STEAM_ID = "steam_id"

        /** JavaSteam's encryption needs a full Bouncy Castle provider, which Android doesn't ship; SpongyCastle is it. */
        @Synchronized
        private fun ensureCrypto() {
            if (java.security.Security.getProvider("SC") == null) {
                java.security.Security.addProvider(org.spongycastle.jce.provider.BouncyCastleProvider())
            }
        }
    }
}

private fun <T> CoroutineScope.future(block: suspend () -> T): CompletableFuture<T> {
    val future = CompletableFuture<T>()
    launch {
        runCatching { block() }.onSuccess { future.complete(it) }.onFailure { future.completeExceptionally(it) }
    }
    return future
}
