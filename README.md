本项目为通过 Android 凭据管理器（[Credential Manager](https://developer.android.com/jetpack/androidx/releases/credentials)）使用通行密钥（[Passkey](https://developers.google.com/identity/passkeys?hl=zh-cn)） 进行无密码登录 Demo。

服务端部分通过拆分单独库([server-sample](./server-sample))进行模拟。

## 设置

以下配置本demo均已完成，这里提及主要是作为提醒和介绍。

**客户端**

1. 设置网络权限

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

2. 引入对应库

```
implementation("androidx.credentials:credentials:1.2.0")
// optional - needed for credentials support from play services, for devices running
// Android 13 and below.
implementation("androidx.credentials:credentials-play-services-auth:1.2.0")
```

3. 配置rp，可参考该[说明](https://developer.android.com/training/sign-in/passkeys/?hl=zh-cn#add-support-dal)。注意：使用 rp 对应域名下的配置相同的包名和签名。该库使用谷歌提供的[demo](https://github.com/android/identity-samples/tree/main/CredentialManager)中的rp以及对应的包名和签名。

**服务端**

自己选一个passkey校验库，该 demo 使用 [`webauthn4j`](https://github.com/webauthn4j/webauthn4j)

```
implementation("com.webauthn4j:webauthn4j-core:0.21.7.RELEASE")
```

## 流程说明

### 创建流程

整体流程参考 [SignUpSample.kt](./app/src/main/java/com/janbean/sample/credential/SignUpSample.kt) 的 `signUpWithPasskeys`

```kotlin
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
```

**客户端创建凭证**

客户端生成密钥，参考 [SignUpSample.kt](./app/src/main/java/com/janbean/sample/credential/SignUpSample.kt) 的 `createPasskey`

```kotlin
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
```

registrationJson 参数：由服务端返回。参考 [CredentialRegisterServer.kt](./server-sample/src/main/java/com/janbean/server/CredentialRegisterServer.kt)  的 `fetchRegistrationJsonFromServer`

* [challenge](https://w3c.github.io/webauthn/#dom-publickeycredentialcreationoptions-challenge)：服务器生成的随机字符串，其中包含足够的熵，使得猜测不可行。它的长度至少应为 16 个字节。这是必需的，但在注册期间不会使用，除非进行[证明](https://developer.mozilla.org/docs/Web/API/Web_Authentication_API/Attestation_and_Assertion)。

* [rp](https://w3c.github.io/webauthn/#dom-publickeycredentialcreationoptions-rp)：The Relying Party Entity corresponds to your application details. It needs :
  * a name : your application name

  * an ID : corresponds to the domain or subdomain.

  * an icon (optional).

* [pubKeyCredParams](https://w3c.github.io/webauthn/#dom-publickeycredentialcreationoptionsjson-pubkeycredparams)：此字段用于指定 RP 支持的公钥算法。我们建议将其设置为 [{alg: -7, type: "public-key"},{alg: -257, type: "public-key"}]。这会指定支持采用 P-256 和 RSA PKCS#1 的 ECDSA，并且支持这些实现可以实现全面的覆盖率
* [authenticatorSelection](https://w3c.github.io/webauthn/#dom-publickeycredentialcreationoptions-authenticatorselection)：该对象的目的是选择合适的验证器来参与创建操作
  * authenticatorAttachment：将其设置为 "platform"。这表示我们想要在平台设备中嵌入身份验证器，并且系统不会提示用户插入 USB 安全密钥等
  * residentKey：创建passkey需要传入"required"
  * userVerification：只有能够满足此要求的验证器才会与用户交互

* user
  * [id](https://w3c.github.io/webauthn/#dom-publickeycredentialuserentity-id): 用户的唯一id，不包含用户信息，如服务端为用户随机生成的唯一id
  * [name](https://w3c.github.io/webauthn/#dom-publickeycredentialentity-name): 用户已知的唯一标识符，比如邮箱、手机或用户自己登录时输入的账号名
  * [displayName](https://w3c.github.io/webauthn/#dom-publickeycredentialuserentity-displayname)：用户昵称

* [timeout](https://w3c.github.io/webauthn/#dom-publickeycredentialcreationoptions-timeout)：超时时间，单位毫秒，不过可能被设备自己的配置覆盖
* [可选项] [excludeCredentials](https://w3c.github.io/webauthn/#dom-publickeycredentialcreationoptions-excludecredentials)：尝试注册设备的用户可能已经注册了其他设备。依赖方应该使用此可选成员来列出映射到此用户帐户的任何现有凭据（由 user.id 标识），这可确保不会在已包含映射到此用户帐户的凭据的身份验证器上创建新凭据，如果存在重复创建，则会提示用户使用新的凭证，或返回失败。（感觉有误解？我自己试即使没设，相同的userId也不会重复创建凭证）

创建的公钥凭证对象`registrationResponseJson`包含：

* id：所创建通行密钥的 Base64 编码 ID。此 ID 有助于浏览器在进行身份验证时确定设备中是否存在匹配的通行密钥。此值需要存储在后端的数据库中。
* rawId：凭据 ID 的 ArrayBuffer 版本。
* response.clientDataJSON：ArrayBuffer 编码的客户端数据。
* response.attestationObject：ArrayBuffer 编码的证明对象。其中包含一些重要信息，例如 RP ID、标志和公钥。
* authenticatorAttachment：如果在支持通行密钥的设备上创建此凭据，则返回 "platform"。
* type：此字段始终设置为 "public-key"

**服务端校验凭证并存储公钥凭证**

服务端收到客户端发来的公钥凭证对象`registrationResponseJson`，需要进行校验并将其与账号绑定，用以后期登录时校验。参考`CredentialRegisterServer.registerPubKeyAndAccount`

校验用参数

* origin：如果是从android app发起，应该是apk签名证书的SHA256，服务端需要保证app为你信任的app。如果是web发起，需要是网页的域名。
* rpId：和客户端创建时用到的一致
* challenge：校验用，防篡改
* registrationResponseJson：客户端创建的凭证信息

### 登录流程

整体流程参考 [SignInSample.kt](./app/src/main/java/com/janbean/sample/credential/SignInSample.kt) 的 `signInWithPasskeys`

```kotlin
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
```

详细的参数说明这里就先不写了，项目中有注释可参考，或阅读最后的参考文档

### 其他说明

#### RP的配置

*原文：[add-support-dal](https://developer.android.com/training/sign-in/passkeys/?hl=zh-cn#add-support-dal)*

如需让您的 Android 应用支持通行密钥，请将应用与其拥有的网站相关联。

官方提供的测试用RP_ID：passkeys-codelab.glitch.me

* 需要applicationId：com.google.credentialmanager.sample

* 需要特定的 debug.keystore（本项目已拷贝使用）

您可以通过完成以下步骤来声明此关联：

1. 创建一个 Digital Asset Links JSON 文件。例如，如需声明网站 `https://signin.example.com` 和软件包名称为 `com.example` 的 Android 应用可以共享登录凭据，请使用以下内容创建一个名为 `assetlinks.json` 的文件：

   ```json
   [
     {
       "relation" : [
         "delegate_permission/common.handle_all_urls",
         "delegate_permission/common.get_login_creds"
       ],
       "target" : {
         "namespace" : "android_app",
         "package_name" : "com.example.android",
         "sha256_cert_fingerprints" : [
           SHA_HEX_VALUE
         ]
       }
     }
   ]
   ```

   `relation` 字段是包含一个或多个字符串的数组，用于描述所声明的关系。如需声明应用和网站共享登录凭据，请指定 `delegate_permission/handle_all_urls` 和 `delegate_permission/common.get_login_creds` 关系。

   `target` 字段是一个对象，用于指定声明所适用的资源。
   以下字段用于标识 Android 应用：

   | `namespace`                | `android_app`                                                |
   | -------------------------- | ------------------------------------------------------------ |
   | `package_name`             | 应用的清单文件中声明的软件包名称，例如 `com.example.android` |
   | `sha256_cert_fingerprints` | 应用的[签名证书](https://developer.android.com/studio/publish/app-signing?hl=zh-cn#sign-apk)的 SHA256 指纹。 |

2. 将这个 Digital Asset Links JSON 文件托管在登录网域中的以下位置：

   ```
   https://domain[:optional_port]/.well-known/assetlinks.json
   ```

   例如，如果您的登录网域是 `signin.example.com`，请将 JSON 文件托管在 `https://signin.example.com/.well-known/assetlinks.json` 上。

   Digital Asset Links 文件的 MIME 类型需为 JSON。确保服务器在响应中发送 `Content-Type: application/json` 标头。

2. 确保您的主机允许 Google 检索您的 Digital Asset Links 文件。如果您有 `robots.txt` 文件，它必须允许 Googlebot 代理检索 `/.well-known/assetlinks.json`。大多数网站可以允许任何自动化代理检索 `/.well-known/` 路径下的文件，以便其他服务可以访问这些文件中的元数据：

   ```
   User-agent: *
   Allow: /.well-known/
   ```

3. 如果您通过凭据管理器使用密码登录方式，请按照此步骤在清单中配置数字资产关联。如果您仅使用通行密钥，则无需执行此步骤。

   在 Android 应用中声明关联。添加一个对象，用于指定要加载的 `assetlinks.json` 文件。您必须对字符串中的所有撇号和引号进行转义。例如：

   ```xml
   <string name="asset_statements" translatable="false">
   [{
     \"include\": \"https://signin.example.com/.well-known/assetlinks.json\"
   }]
   </string>
   ```

   **注意：**`https://signin.example.com/.well-known/assetlinks.json` 链接必须返回包含 JSON MIME 内容类型标头的 200 HTTP 响应。若返回 301 或 302 HTTP 重定向或非 JSON 内容类型，会导致验证失败。以下示例展示了一个请求及其相关的响应标头。

   ```
   > GET /.well-known/assetlinks.json HTTP/1.1
   > User-Agent: curl/7.35.0
   > Host: signin.example.com
   
   < HTTP/1.1 200 OK
   < Content-Type: application/json
   ```

#### 支持第三方 passkey 管理应用

*原文：[Make passkey endpoints well known url part of your passkey implementation](https://android-developers.googleblog.com/2023/10/make-passkey-endpoints-well-known-url-part-of-your-passkey-implementation.html)*

密码管理工具的使用率一直在稳步上升，我们预计大多数提供商也将集成密钥管理。您可以允许第三方工具和服务通过实施密钥端点众所周知的 URL 将您的用户引导至专用密钥管理页面。

最好的部分是，在大多数情况下，您可以在两个小时或更短的时间内实现此功能！您所需要做的就是在您的网站上托管一个简单的架构。查看下面的示例：

1. 对于 https://example.com 上的 Web 服务，众所周知的 URL 为 https://example.com/.well-known/passkey-endpoints
2. 查询 URL 时，响应应使用以下架构：

```
{ "enroll": "https://example.com/account/manage/passkeys/create",  "manage": "https://example.com/account/manage/passkeys" }
```

**注意：**您可以根据网站自身的配置来决定注册和管理的 URL 的确切值。

如果您有移动应用程序，我们强烈建议使用[深层链接](https://developer.android.com/training/app-links/deep-linking)，让这些 URL 直接在您的应用程序中打开每个活动的相应屏幕，以“注册”或“管理”密钥。这将使您的用户集中注意力并按计划注册密钥。

#### 问题

注册/登录passkey失败，原因未知，疑似 google 的问题。

* 退出gp账号，重新登录gp账号后，注册/登录passkey失败。

* 先注册passkey，然后重启设备，登录passkey，一直报没有凭证，可是明明有。

以上两个问题，有时重启设备后正常，有时进入“设置 -> Google -> 自动填充 -> Google 自动填充 -> 密码”， 点击对应应用密码查看详情后，再进入应用使用 passkey 正常。

```
androidx.credentials.exceptions.NoCredentialException: During begin sign in, failure response from one tap: 16: Cannot find a matching credential.
at androidx.credentials.playservices.controllers.CredentialProviderBaseController$Companion.getCredentialExceptionTypeToException$credentials_play_services_auth_release(CredentialProviderBaseController.kt:108)
at androidx.credentials.playservices.controllers.BeginSignIn.CredentialProviderBeginSignInController$resultReceiver$1$onReceiveResult$1.invoke(CredentialProviderBeginSignInController.kt:93)
at androidx.credentials.playservices.controllers.BeginSignIn.CredentialProviderBeginSignInController$resultReceiver$1$onReceiveResult$1.invoke(CredentialProviderBeginSignInController.kt:93)
at androidx.credentials.playservices.controllers.CredentialProviderController.maybeReportErrorFromResultReceiver(CredentialProviderController.kt:159)
at androidx.credentials.playservices.controllers.BeginSignIn.CredentialProviderBeginSignInController.access$maybeReportErrorFromResultReceiver(CredentialProviderBeginSignInController.kt:55)
at androidx.credentials.playservices.controllers.BeginSignIn.CredentialProviderBeginSignInController$resultReceiver$1.onReceiveResult(CredentialProviderBeginSignInController.kt:91)
at android.os.ResultReceiver$MyRunnable.run(ResultReceiver.java:50)
at android.os.Handler.handleCallback(Handler.java:907)
at android.os.Handler.dispatchMessage(Handler.java:105)
at android.os.Looper.loop(Looper.java:216)
at android.app.ActivityThread.main(ActivityThread.java:7779)
at java.lang.reflect.Method.invoke(Native Method)
at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:524)
at com.android.internal.os.ZygoteInit.main(ZygoteInit.java:990)
```

## 参考

- [使用凭据管理器让用户登录](https://developer.android.com/training/sign-in/passkeys/?hl=zh-cn)
- [实现参考](https://medium.com/androiddevelopers/bringing-seamless-authentication-to-your-apps-using-credential-manager-api-b3f0d09e0093#172a)
- [实现参考2](https://web.dev/articles/passkey-registration?hl=zh-cn#send-the-returned-public-key-credential-to-the-backend)
- [交互建议](https://developer.android.com/design/ui/mobile/guides/patterns/passkeys?hl=zh-cn)
- [常见问题解答](https://developer.android.com/training/sign-in/credential-manager-faq?hl=zh-cn)
- [WebAuthn](https://w3c.github.io/webauthn)
- [WebAuthn和Passkey的关系](https://blog.passwordless.id/webauthn-vs-passkeys): webauthn 是规范，passkey 是 webauthn 的具体实现
- [Make passkey endpoints well known url part of your passkey implementation](https://android-developers.googleblog.com/2023/10/make-passkey-endpoints-well-known-url-part-of-your-passkey-implementation.html)
- 开源库
    - Webauthn解析库列表：[https://github.com/herrjemand/awesome-webauthn](https://github.com/herrjemand/awesome-webauthn)
    - Android官方demo：[github demo](https://github.com/android/identity-samples/tree/main/CredentialManager)
    - 本项目模拟后台用的解析库：[https://github.com/webauthn4j/webauthn4j](https://github.com/webauthn4j/webauthn4j)
