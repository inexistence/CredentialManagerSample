package com.janbean.server

import com.webauthn4j.data.PublicKeyCredentialParameters
import com.webauthn4j.data.PublicKeyCredentialType
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier
import org.json.JSONArray
import org.json.JSONObject

object Constants {
    // 需要配置认证
    const val RP_ID = "passkeys-codelab.glitch.me"
    const val RP_NAME = "CredMan App Test"

    // 如果是从android app发起，应该是apk签名证书的SHA256，可以通过ApkCert.getHashKey查看确认。服务端需要保证app为你信任的app
    const val ORIGIN = "android:apk-key-hash:TyBHH9maupZHjVknwsim6o7SjRTAtqI5mZ-jTUc9-hE"

    // 此字段用于指定 RP 支持的公钥算法。
    // 我们建议将其设置为 [{alg: -7, type: "public-key"},{alg: -257, type: "public-key"}]。
    // 这会指定支持采用 P-256 和 RSA PKCS#1 的 ECDSA，并且支持这些实现可以实现全面的覆盖率。
    val PUB_KEY_CRED_PARAMS by lazy {
        arrayListOf(
            PublicKeyCredentialParameters(
                PublicKeyCredentialType.PUBLIC_KEY,
                COSEAlgorithmIdentifier.ES256
            ),
            PublicKeyCredentialParameters(
                PublicKeyCredentialType.PUBLIC_KEY,
                COSEAlgorithmIdentifier.RS256
            )
        )
    }

    val PUB_KEY_CRED_PARAMS_JSON by lazy {
        JSONArray().apply {
            PUB_KEY_CRED_PARAMS.forEach { param ->
                put(JSONObject().apply {
                    putOpt("type", param.type.value)
                    putOpt("alg", param.alg.value)
                })
            }
        }
    }
}