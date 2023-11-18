package com.janbean.sample.credential

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException
import com.janbean.sample.repository.ClientDatabase
import com.janbean.sample.utils.Storage
import com.janbean.server.CredentialRegisterServer
import org.json.JSONObject

object SignUpSample {

    suspend fun signUpWithPasskeys(context: Context, username: String): Result<Unit> {
        try {
            val credentialManager = CredentialManager.create(context)
            // 拉取配置
            val registrationJson = fetchRegistrationJsonFromServer(username)
            // 创建凭证
            val data = createPasskey(context, credentialManager, registrationJson)
            // 校验并将凭证和账号绑定
            registerResponse(JSONObject(registrationJson).optString("challenge"), data)
            // 可选，在客户端存储账号和凭证id的关系，下次自动根据id使用对应凭证，用于处理多id多凭证的情况
            saveDataInClientIfNeeded(username, data)
        } catch (th: Throwable) {
            return Result.failure(th)
        }
        return Result.success(Unit)
    }

    @Throws(CreateCredentialException::class)
    private suspend fun createPasskey(
        context: Context,
        credentialManager: CredentialManager,
        registrationJson: String
    ): CreatePublicKeyCredentialResponse {
        val request = CreatePublicKeyCredentialRequest(registrationJson)

        // Throws:
        // CreateCredentialException - If the request fails
        return credentialManager.createCredential(
            context,
            request
        ) as CreatePublicKeyCredentialResponse
    }

    private suspend fun fetchRegistrationJsonFromServer(username: String): String {
        return CredentialRegisterServer.fetchRegistrationJsonFromServer(username)
    }

    private suspend fun registerResponse(challengeStr: String, data: CreatePublicKeyCredentialResponse): Result<Unit> {
        return CredentialRegisterServer.registerPubKeyAndAccount(challengeStr, data.registrationResponseJson)
    }

    /**
     * 可选，存储账号和凭证id的关系
     */
    private suspend fun saveDataInClientIfNeeded(username: String, data: CreatePublicKeyCredentialResponse) {
        val registration = JSONObject(data.registrationResponseJson)
        val registrationResponse = registration.optJSONObject("response")

        val id = registration.getString("id")
        val clientDataJson = registrationResponse?.optString("clientDataJSON")

        ClientDatabase.get().addCredential(username, id, JSONObject().apply {
            put("id", id)
            put("clientDataJson", clientDataJson)
        }.toString())
    }
}