package com.janbean.sample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.coroutineScope
import com.janbean.sample.credential.SignInSample
import com.janbean.sample.credential.SignUpSample
import com.janbean.sample.ui.theme.CredentialManagerSampleTheme
import com.janbean.sample.utils.ApkCert
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CredentialManagerSampleTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column {
                        Greeting("Register") {
                            // TODO 要在子线程处理，会耗时
                            lifecycle.coroutineScope.launch {
                                val result = SignUpSample.signUpWithPasskeys(this@MainActivity, "huangjianbin")

                                if (result.isFailure) {
                                    Log.e(TAG, "signUpWithPasskeys $result", result.exceptionOrNull())
                                } else {
                                    Log.i(TAG, "signUpWithPasskeys $result")
                                }
                                Toast.makeText(this@MainActivity, "$result", Toast.LENGTH_LONG).show()
                            }
                        }
                        Greeting(name = "Authentication") {
                            // TODO 要在子线程处理，会耗时
                            lifecycle.coroutineScope.launch {
                                val result = SignInSample.signInWithPasskeys(
                                    this@MainActivity
                                )

                                if (result.isFailure) {
                                    Log.e(TAG, "signInWithPasskeys $result", result.exceptionOrNull())
                                } else {
                                    Log.i(TAG, "signInWithPasskeys $result")
                                }
                                Toast.makeText(this@MainActivity, "$result", Toast.LENGTH_LONG).show()
                            }
                        }
                    }

                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        modifier = modifier,
        onClick = onClick
    ) {
        Text(text = name)
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CredentialManagerSampleTheme {
        Greeting("Android") {}
    }
}