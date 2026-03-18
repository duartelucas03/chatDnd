package com.example.easychat.utils

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage

object SupabaseClientProvider {

    // TODO: mover para local.properties + BuildConfig antes de publicar.
    val client: SupabaseClient = createSupabaseClient(
        supabaseUrl  = "https://kylkgbuczqvmkxeykhth.supabase.co",
        supabaseKey  = "sb_publishable_eGzic_TV6-ZhBO4q6E8EAg_yGL_QPPd"
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
    }

    val auth     get() = client.auth
    val db       get() = client.postgrest
    val realtime get() = client.realtime
    val storage  get() = client.storage

    fun currentUserId(): String = auth.currentUserOrNull()?.id ?: ""
    fun isLoggedIn(): Boolean   = currentUserId().isNotEmpty()
}
