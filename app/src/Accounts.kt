package com.harpreet.omniai.data

import android.content.Context
import android.os.Build
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class Account(val username: String, val displayName: String, val salt: String, val hash: String)

/**
 * Local-only accounts. Passwords are salted + hashed with PBKDF2 and never stored in plain text.
 * There is no server, so there is no cloud sync and no password recovery.
 */
class Accounts(context: Context) {
    private val prefs = context.getSharedPreferences("omniai_accounts", Context.MODE_PRIVATE)

    private fun load(): List<Account> {
        val raw = prefs.getString("accounts", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Account(o.getString("u"), o.getString("n"), o.getString("s"), o.getString("h"))
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(list: List<Account>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("u", it.username).put("n", it.displayName).put("s", it.salt).put("h", it.hash))
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    fun current(): Account? {
        val u = prefs.getString("current", "") ?: ""
        return load().firstOrNull { it.username == u }
    }

    fun signUp(username: String, displayName: String, password: String): Result<Account> = runCatching {
        val u = username.trim().lowercase()
        require(Regex("[a-z0-9_]{3,20}").matches(u)) { "Username: 3–20 letters, numbers or _" }
        require(password.length >= 6) { "Password must be at least 6 characters" }
        val list = load()
        require(list.none { it.username == u }) { "This username is already taken" }
        val saltBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val account = Account(
            u, displayName.trim().ifEmpty { u },
            Base64.encodeToString(saltBytes, Base64.NO_WRAP), hash(password, saltBytes)
        )
        save(list + account)
        prefs.edit().putString("current", u).apply()
        account
    }

    fun signIn(username: String, password: String): Result<Account> = runCatching {
        val u = username.trim().lowercase()
        val account = load().firstOrNull { it.username == u } ?: error("Wrong username or password")
        val expected = Base64.decode(account.hash, Base64.NO_WRAP)
        val actual = Base64.decode(hash(password, Base64.decode(account.salt, Base64.NO_WRAP)), Base64.NO_WRAP)
        if (!MessageDigest.isEqual(expected, actual)) error("Wrong username or password")
        prefs.edit().putString("current", u).apply()
        account
    }

    fun signOut() { prefs.edit().putString("current", "").apply() }

    fun delete(username: String) {
        save(load().filter { it.username != username })
        signOut()
    }

    private fun hash(password: String, salt: ByteArray): String {
        val algo = if (Build.VERSION.SDK_INT >= 26) "PBKDF2WithHmacSHA256" else "PBKDF2WithHmacSHA1"
        val spec = PBEKeySpec(password.toCharArray(), salt, 60000, 256)
        return Base64.encodeToString(SecretKeyFactory.getInstance(algo).generateSecret(spec).encoded, Base64.NO_WRAP)
    }
}
