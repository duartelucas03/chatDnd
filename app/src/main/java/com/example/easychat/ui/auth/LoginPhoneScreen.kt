package com.example.easychat.ui.auth

import android.content.Intent
import android.text.InputType
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.easychat.R
import com.example.easychat.ui.compose.components.PrimaryButton
import com.hbb20.CountryCodePicker

@Composable
fun LoginPhoneScreen() {
    val context = LocalContext.current
    var ccpRef: CountryCodePicker? by remember { mutableStateOf(null) }
    var phoneError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.phone_icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)      // reduzido de 80 para 64
        )
        Spacer(Modifier.height(16.dp))           // reduzido de 24 para 16
        Text("Seu número de telefone", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))            // reduzido de 8 para 4
        Text(
            "Vamos enviar um código para verificação",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(24.dp))           // reduzido de 32 para 24

        AndroidView(
            factory = { ctx ->
                val row = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }
                val ccp = CountryCodePicker(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setDefaultCountryUsingNameCode("BR")
                    showFlag(true)
                    showFullName(false)
                }
                val editText = android.widget.EditText(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                    )
                    hint = "Número de telefone"
                    inputType = InputType.TYPE_CLASS_PHONE
                }
                ccp.registerCarrierNumberEditText(editText)
                ccpRef = ccp
                row.addView(ccp)
                row.addView(editText)
                row
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (phoneError != null) {
            Text(
                text = phoneError!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(Modifier.height(16.dp))           // reduzido de 24 para 16

        PrimaryButton(
            text = "Enviar OTP",
            onClick = {
                val ccp = ccpRef
                if (ccp == null || !ccp.isValidFullNumber) {
                    phoneError = "Número de telefone inválido"
                } else {
                    phoneError = null
                    val phone = ccp.fullNumberWithPlus
                    context.startActivity(Intent(context, LoginOtpActivity::class.java).apply {
                        putExtra("phone", phone)
                    })
                }
            }
        )
    }
}