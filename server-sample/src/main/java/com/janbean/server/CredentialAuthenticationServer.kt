package com.janbean.server

import android.text.TextUtils
import android.util.Base64
import android.util.Log
import com.webauthn4j.WebAuthnAuthenticationManager
import com.webauthn4j.WebAuthnRegistrationManager
import com.webauthn4j.authenticator.Authenticator
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.AuthenticationData
import com.webauthn4j.data.AuthenticationParameters
import com.webauthn4j.data.AuthenticationRequest
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.validator.exception.ValidationException
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom


object CredentialAuthenticationServer {
    const val TAG = "CredentialAuthenticationServer"

    suspend fun fetchAuthJsonFromServer(credentialIds: List<String>? = null): String {
        val challenge = getEncodedChallenge()
        val timeout = 180000
        val rpId = Constants.RP_ID
        // 用于指示使用设备屏幕锁定功能进行的用户验证是 "required"、"preferred" 还是 "discouraged"。
        // 默认值为 "preferred"，这意味着身份验证器可以跳过用户验证。请将其设置为 "preferred" 或省略该属性。
        // 如果您希望始终要求进行用户验证，请将 userVerification 设置为 "required"。
        val userVerification = "required"

        // 用于此身份验证的可接受凭据数组。传递一个空数组，即可让用户从浏览器显示的列表中选择可用的通行密钥。
        val allowCredentials = if (!credentialIds.isNullOrEmpty()) {
            JSONArray().apply {
                credentialIds.forEach { credentialId ->
                    put(JSONObject().apply {
                        put("type", "public-key")
                        put("id", credentialId)
                    })
                }
            }
        } else {
            null
        }

        val result = JSONObject().apply {
            put("challenge", challenge)
            put("timeout", timeout)
            put("userVerification", userVerification)
            put("rpId", rpId)
            allowCredentials?.let {
                put("allowCredentials", it)
            }
        }.toString()

        Log.i(TAG, "fetchAuthJsonFromServer return $result")
        return result
    }




    private fun getEncodedChallenge(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    suspend fun sendSignInResponseToServer(challengeStr: String, authenticationResponseJson: String): Result<String> {
        Log.i(TAG, "authenticationResponseJson=$authenticationResponseJson")

        val authentication = JSONObject(authenticationResponseJson)
        val response = authentication.getJSONObject("response")

        // Client properties
        val credentialIdStr = authentication.optString("id")
        val credentialId: ByteArray? = Base64.decode(credentialIdStr, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        // 包含创建时设置的用户 ID 的 ArrayBuffer。对应注册时的user.id
        // 如果服务器需要选择其使用的 ID 值，或者后端希望避免为凭据 ID 创建索引，则可以使用此值来代替凭据 ID
        val userId = response.optString("userHandle")
        val userHandle: ByteArray? = Base64.decode(userId, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val authenticatorData: ByteArray? = Base64.decode(response.optString("authenticatorData"), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val clientDataJSON: ByteArray? = Base64.decode(response.optString("clientDataJSON"), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
        val clientExtensionJSON: String? = authentication.optString("clientExtensionResults")
        val signature: ByteArray? = Base64.decode(response.optString("signature"), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

        // Server properties
        val origin = Origin.create(Constants.ORIGIN)
        val rpId = Constants.RP_ID
        val challenge =
            Challenge { Base64.decode(challengeStr, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) }
        val tokenBindingId: ByteArray? = null /* set tokenBindingId */
        val serverProperty = ServerProperty(origin, rpId, challenge, tokenBindingId)

        // expectations
        val allowCredentials: List<ByteArray>? = null
        val userVerificationRequired = true
        val userPresenceRequired = true
        val authenticator = ServerDatabase.get().getAuthenticator(credentialIdStr)


        val authenticationRequest = AuthenticationRequest(
            credentialId,
            userHandle,
            authenticatorData,
            clientDataJSON,
            clientExtensionJSON,
            signature
        )
        val authenticationParameters = AuthenticationParameters(
            serverProperty,
            authenticator!!,
            allowCredentials,
            userVerificationRequired,
            userPresenceRequired
        )
        val webAuthnManager = WebAuthnAuthenticationManager()

        val authenticationData = try {
            webAuthnManager.parse(authenticationRequest)
        } catch (e: DataConversionException) {
            // If you would like to handle WebAuthn data structure parse error, please catch DataConversionException
            throw e
        }
        try {
            webAuthnManager.validate(authenticationData, authenticationParameters)
        } catch (e: ValidationException) {
            // If you would like to handle WebAuthn data validation error, please catch ValidationException
            throw e
        }

        // 校验通过，登录这个凭证对应的账号。可以使用 userHandle（注册 passkey 时的 user.id）。或根据自己设计的方案进行关联账号并登录
        Log.i(TAG, "login success. $userId signCount=${authenticator.counter}")
        // TODO 更新authenticator，会更新counter，但是好像无所谓

        return Result.success(userId)
    }
}