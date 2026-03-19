package com.example.easychat.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.example.easychat.model.UserModel


/** Claude AI - início
 * Prompt: Crie um objeto utilitário com helpers gerais do Android: mostrar Toast, passar e recuperar o modelo de usuário e dados de chatroom via Intent, e carregar foto de perfil com Glide em formato circular.
 */
object AndroidUtil {

    fun showToast(context: Context?, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    fun passUserModelAsIntent(intent: Intent, model: UserModel) {
        intent.putExtra("user_model", model)
    }

    fun getUserModelFromIntent(intent: Intent): UserModel =
        intent.getParcelableExtra("user_model") ?: UserModel()

    fun passChatroomIdAsIntent(intent: Intent, chatroomId: String, displayName: String, avatarUrl: String?) {
        intent.putExtra("chatroom_id", chatroomId)
        intent.putExtra("chatroom_display_name", displayName)
        intent.putExtra("chatroom_avatar_url", avatarUrl ?: "")
    }

    fun getChatroomIdFromIntent(intent: Intent): String? =
        intent.getStringExtra("chatroom_id")

    fun setProfilePic(context: Context, imageUri: Uri, imageView: ImageView) {
        Glide.with(context).load(imageUri)
            .apply(RequestOptions.circleCropTransform()).into(imageView)
    }

    fun setProfilePicFromUrl(context: Context, url: String?, imageView: ImageView) {
        if (url.isNullOrBlank()) return
        Glide.with(context).load(url)
            .apply(RequestOptions.circleCropTransform()).into(imageView)
    }
}

/** Claude AI - final */