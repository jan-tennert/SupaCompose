// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import io.github.jan.supacompose.auth.Auth
import io.github.jan.supacompose.auth.auth
import io.github.jan.supacompose.auth.providers.Discord
import io.github.jan.supacompose.auth.providers.Email
import io.github.jan.supacompose.auth.providers.Google
import io.github.jan.supacompose.auth.sessionFile
import io.github.jan.supacompose.createSupabaseClient
import io.github.jan.supacompose.postgrest.Postgrest
import io.github.jan.supacompose.realtime.ChannelAction
import io.github.jan.supacompose.realtime.Realtime
import io.github.jan.supacompose.realtime.createChannel
import io.github.jan.supacompose.realtime.realtime
import io.github.jan.supacompose.storage.Storage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data class User(val id: String, val username: String)


suspend fun main() {
    val client = createSupabaseClient {

        supabaseUrl = "https://arnyfaeuskyqfxkvotgj.supabase.co"
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImFybnlmYWV1c2t5cWZ4a3ZvdGdqIiwicm9sZSI6ImFub24iLCJpYXQiOjE2NTMwMzkxMTEsImV4cCI6MTk2ODYxNTExMX0.ItmL8lfnOL9oy7CEX9N6TnYt10VVhk-KTlwley4aq1M"


        install(Auth) {
            sessionFile = File("C:\\Users\\jan\\AppData\\Local\\SupaCompose\\usersession.json")
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }
    println(client.supabaseHttpUrl)
    application {
        Window(::exitApplication) {
            val session by client.auth.currentSession.collectAsState()
            val status by client.realtime.status.collectAsState()
            val scope = rememberCoroutineScope()
            println(session?.accessToken)
            if (session != null) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Logged in as ${session?.user?.email}")
                }
                LaunchedEffect(Unit) {
                    scope.launch {
                        client.realtime.connect()
                    }
                }
                if(status == Realtime.Status.CONNECTED) {
                    LaunchedEffect(Unit) {
                        client.realtime.createChannel {
                            table = "products"
                            schema = "public"

                            on(ChannelAction.UPDATE) {
                                println(it.record)
                            }
                        }
                    }
                }
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    var email by remember { mutableStateOf("") }
                    var password by remember { mutableStateOf("") }
                    Column {
                        TextField(email, { email = it }, placeholder = { Text("Email") })
                        TextField(
                            password,
                            { password = it },
                            placeholder = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Button(onClick = {
                            scope.launch {
                                client.auth.loginWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                            }
                        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Login")
                        }
                        Button(onClick = {
                            scope.launch {
                                client.auth.loginWith(Google)
                            }
                        }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Login with Discord")
                        }
                        //
                    }
                }

            }
        }
    }

}

/*fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}*/
