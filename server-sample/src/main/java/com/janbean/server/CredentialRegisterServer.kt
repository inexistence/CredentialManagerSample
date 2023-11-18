package com.janbean.server

import android.util.Base64
import android.util.Log
import com.webauthn4j.WebAuthnRegistrationManager
import com.webauthn4j.authenticator.Authenticator
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.RegistrationData
import com.webauthn4j.data.RegistrationParameters
import com.webauthn4j.data.RegistrationRequest
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.server.ServerProperty
import com.webauthn4j.validator.exception.ValidationException
import org.json.JSONObject
import java.nio.charset.Charset
import java.security.SecureRandom


object CredentialRegisterServer {

    const val TAG = "CredentialRegisterServer"

    /**
     * @param username 用于登录的，用户本身已知的，服务端中用户账号的用户唯一标识符，如邮箱或手机或账号id
     */
    suspend fun fetchRegistrationJsonFromServer(username: String): String {
        val challenge = getEncodedChallenge()

        val user = JSONObject().apply {
            // 用户的唯一id，不包含用户身份信息
            // 可以考虑随机的16字节值，也可以服务端账号体系中的用户id，在登录时可以从凭证中读取到(userHandle)
            // 为了方便，这里将userId赋值为username
//            val userId = getEncodedUserId()
            val userId = username

            // 用户唯一标识符，如邮箱或手机或账号id，会显示在账号选择器中
            val userName = username

            // 易于理解的用户账号名，只用于显示，并会显示在账号选择器中。
            // 可以使用服务端账号体系中的用户名
            val userDisplayName = username

            putOpt("id", userId)
            putOpt("name", userName)
            putOpt("displayName", userDisplayName)
        }

        val rp = JSONObject().apply { // 应用信息
            // RP ID 是一个网域，网站可以指定其网域或可注册后缀。
            // 例如，如果 RP 的来源为 https://login.example.com:1337，则 RP ID 可以是 login.example.com 或 example.com。
            // 如果将 RP ID 指定为 example.com，则用户可以在 login.example.com 或 example.com 上的任何子网域上进行身份验证。
            // 参考：https://developer.android.com/training/sign-in/passkeys/?hl=zh-cn
            putOpt(
                "id",
                Constants.RP_ID
            )

            // RP 的名称
            putOpt("name", Constants.RP_NAME)
        }

        // 此字段用于指定 RP 支持的公钥算法。
        // 我们建议将其设置为 [{alg: -7, type: "public-key"},{alg: -257, type: "public-key"}]。
        // 这会指定支持采用 P-256 和 RSA PKCS#1 的 ECDSA，并且支持这些实现可以实现全面的覆盖率。
        val pubKeyCredParams = Constants.PUB_KEY_CRED_PARAMS_JSON

        val authenticatorSelection = JSONObject().apply {
            // 将其设置为 "platform"。这表示我们想要在平台设备中嵌入身份验证器，并且系统不会提示用户插入 USB 安全密钥等。
            putOpt("authenticatorAttachment", "platform")
            putOpt("residentKey", "required")
        }

        val result = JSONObject().apply {
            put("challenge", challenge)
            put("rp", rp)
            put("pubKeyCredParams", pubKeyCredParams)
            put("authenticatorSelection", authenticatorSelection)
            put("user", user)
        }.toString()

        Log.i(TAG, "fetchRegistrationJsonFromServer return $result")
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

    private fun getEncodedUserId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(64)
        random.nextBytes(bytes)
        return Base64.encodeToString(
            bytes,
            Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
        )
    }

    suspend fun registerPubKeyAndAccount(
        challengeStr: String,
        registrationResponseJson: String
    ): Result<Unit> {
//        公钥凭据对象包含以下属性：
//
//        id：所创建通行密钥的 Base64网址 编码 ID。此 ID 有助于浏览器在进行身份验证时确定设备中是否存在匹配的通行密钥。此值需要存储在后端的数据库中。
//        rawId：凭据 ID 的 ArrayBuffer 版本。
//        response.clientDataJSON：ArrayBuffer 编码的客户端数据。
//        response.attestationObject：ArrayBuffer 编码的证明对象。其中包含一些重要信息，例如 RP ID、标志和公钥。
//        authenticatorAttachment：如果在支持通行密钥的设备上创建此凭据，则返回 "platform"。
//        type：此字段始终设置为 "public-key"
        Log.i(TAG, "registerPubKeyAndAccount registrationResponseJson=$registrationResponseJson")

        val registration = JSONObject(registrationResponseJson)
        val registrationResponse = registration.optJSONObject("response")

        val credentialId = registration.getString("id")
        val rawId = registration.opt("rawId")
        val type = registration.opt("type")

        // Client properties
        val attestationObject = Base64.decode(registrationResponse?.optString("attestationObject"), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) /* set attestationObject */
        val clientDataJSON: ByteArray? = Base64.decode(registrationResponse?.optString("clientDataJSON"), Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) /* set clientDataJSON */
        val clientExtensionJSON: String? =
            registration.optString("clientExtensionResults") /* set clientExtensionJSON */
        val transports = registrationResponse?.optJSONArray("transports")?.run {
            val set = HashSet<String>(length())
            for (index in 0 until this.length()) {
                optString(index)?.let { set.add(it) }
            }
            set
        }

        // Server properties
        val origin = Origin.create(Constants.ORIGIN) /* set origin */
        val rpId = Constants.RP_ID /* set rpId */
        val challenge =
            Challenge { Base64.decode(challengeStr, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING) } /* set challenge */
        val tokenBindingId: ByteArray? = null /* set tokenBindingId */
        val serverProperty = ServerProperty(origin, rpId, challenge, tokenBindingId)

        // expectations
        val userVerificationRequired = false
        val userPresenceRequired = true

        val registrationRequest = RegistrationRequest(
            attestationObject,
            clientDataJSON,
            clientExtensionJSON,
            transports
        )

        val registrationParameters = RegistrationParameters(
            serverProperty,
            Constants.PUB_KEY_CRED_PARAMS,
            userVerificationRequired,
            userPresenceRequired
        )

        val webAuthnManager = WebAuthnRegistrationManager.createNonStrictWebAuthnRegistrationManager()
        val registrationData: RegistrationData = try {
            webAuthnManager.parse(registrationRequest)
        } catch (e: DataConversionException) {
            // If you would like to handle WebAuthn data structure parse error, please catch DataConversionException
            throw e
        }
        try {
            webAuthnManager.validate(registrationData, registrationParameters)
        } catch (e: ValidationException) {
            // If you would like to handle WebAuthn data validation error, please catch ValidationException
            throw e
        }

       // please persist Authenticator object, which will be used in the authentication process.
        val authenticator: Authenticator =
            AuthenticatorImpl( // You may create your own Authenticator implementation to save friendly authenticator name
                registrationData.attestationObject!!.authenticatorData.attestedCredentialData!!,
                registrationData.attestationObject?.attestationStatement,
                registrationData.attestationObject?.authenticatorData?.signCount ?: 0
            )

        save(credentialId, authenticator)

        return Result.success(Unit)
    }

    private suspend fun save(credentialId: String, authenticator: Authenticator) {
        // 持久化凭证
        // 将这个凭证和账号绑定，或者如果 fetchRegistrationJsonFromServer 时返回的 user.id 已经在账号体系了，那认证时可以直接拿 userHandle（就是user.id）
        // 为了方便，这个示例将以user.id已经在账号体系的情况处理
        // 该数据库设计仅参考，可以按自己的需要设计数据库和关联关系，只要后续登录时能拿到需要的数据做校验即可
        ServerDatabase.get().bindAuthenticator(credentialId, authenticator)
    }
}