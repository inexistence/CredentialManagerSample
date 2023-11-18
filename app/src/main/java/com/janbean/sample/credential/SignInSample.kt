package com.janbean.sample.credential

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import com.janbean.sample.repository.ClientDatabase
import com.janbean.sample.utils.Storage
import com.janbean.server.CredentialAuthenticationServer
import org.json.JSONObject

object SignInSample {

    /**
     * 如果传了username，就会优先只用已记录的指定凭证（如果本地有记录的话），否则会让用户选择凭证
     */
    suspend fun signInWithPasskeys(context: Context, username: String? = null): Result<String> {
        try {
            val credentialIds = if (!username.isNullOrBlank()) {
                ClientDatabase.get().getCredentialIds(username)
            } else {
                null
            }
            // 拉取服务端配置
            // 可选：如果传了凭证id，就会直接使用指定的凭证，否则会让用户选择凭证
            val authJson = fetchAuthJsonFromServer(credentialIds)
            val challengeStr = JSONObject(authJson).optString("challenge")
            // 生成凭证
            val pubKeyCredential = getSavedCredentials(context, null, authJson)
            // 校验凭证并获取用户信息/完成登录
            return CredentialAuthenticationServer.sendSignInResponseToServer(challengeStr, pubKeyCredential.authenticationResponseJson)
        } catch (th: Throwable) {
            return Result.failure(th)
        }
    }

    suspend fun getSavedCredentials(context: Context, credentialCache: JSONObject?, authJson: String): PublicKeyCredential {
        // 可选：校验包信息
        val clientDataHash = credentialCache?.optString("clientDataJson")

        val getPublicKeyCredentialOption = GetPublicKeyCredentialOption(
            authJson,
            clientDataHash?.let {
                Base64.decode(
                    clientDataHash,
                    Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING
                )
            }
        )

        val credentialManager = CredentialManager.create(context)
        val result =
            credentialManager.getCredential(
                context,
                GetCredentialRequest(
                    listOf(
                        getPublicKeyCredentialOption
                    )
                )
            )

        return result.credential as PublicKeyCredential
    }

    suspend fun fetchAuthJsonFromServer(credentialIds: List<String>? = null): String {
        return CredentialAuthenticationServer.fetchAuthJsonFromServer(credentialIds)
    }
}