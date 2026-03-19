package com.example.easychat.ui.compose.components


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.easychat.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
// ─── Avatar circular com fallback de ícone ───────────────────────────────────

@Composable
fun UserAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(avatarUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.person_icon),
                contentDescription = "Avatar padrão",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}

// ─── Botão primário com estado de loading ────────────────────────────────────

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp
            )
        } else {
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ─── Campo de texto estilizado ───────────────────────────────────────────────

@Composable
fun EasyChatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeAction: (() -> Unit)? = null,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            enabled = enabled,
            onValueChange = onValueChange,
            label = { Text(label) },
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onDone   = { onImeAction?.invoke() },
                onNext   = { onImeAction?.invoke() },
                onSearch = { onImeAction?.invoke() }
            ),
            singleLine = singleLine,
            maxLines = maxLines,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = leadingIcon
        )
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

// ─── Indicador de progresso centralizado ────────────────────────────────────

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

// ─── Snackbar host padrão ────────────────────────────────────────────────────

@Composable
fun EasyChatSnackbarHost(hostState: SnackbarHostState) {
    SnackbarHost(hostState) { data ->
        Snackbar(
            snackbarData = data,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

// ─── Linha de item de chat recente ───────────────────────────────────────────

@Composable
fun RecentChatRow(
    displayName: String,
    lastMessagePreview: String,
    lastMessageTime: String,
    avatarUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarUrl = avatarUrl, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = displayName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = lastMessageTime,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = lastMessagePreview,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

// ─── Linha de resultado de busca de usuário ──────────────────────────────────

@Composable
fun SearchUserRow(
    username: String,
    phone: String,
    avatarUrl: String?,
    isSelected: Boolean = false,
    selectionMode: Boolean = false,
    isSelf: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isSelf, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarUrl = avatarUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isSelf) "$username (Eu)" else username,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp
            )
            Text(
                text = phone,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (selectionMode && !isSelf) {
            Checkbox(checked = isSelected, onCheckedChange = null)
        }
    }
}

// ─── Bolha de mensagem ───────────────────────────────────────────────────────

@Composable
fun MessageBubble(
    text: String?,
    timeFormatted: String,
    statusSymbol: String,
    showStatus: Boolean,
    isFromMe: Boolean,
    highlightKeyword: String = "",
    modifier: Modifier = Modifier
) {
    val bubbleBg = if (isFromMe)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isFromMe)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(
                RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd   = 16.dp,
                    bottomStart = if (isFromMe) 16.dp else 4.dp,
                    bottomEnd   = if (isFromMe) 4.dp  else 16.dp
                )
            )
            .background(bubbleBg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column {
            if (!text.isNullOrBlank()) {
                HighlightedText(
                    text = text,
                    keyword = highlightKeyword,
                    color = textColor
                )
            }
            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text  = timeFormatted,
                    fontSize = 10.sp,
                    color = textColor.copy(alpha = 0.7f)
                )
                if (showStatus) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text  = statusSymbol,
                        fontSize = 10.sp,
                        color = if (statusSymbol == "✓✓")
                            Color(0xFF9FFF81)  // verde
                        else
                            textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// ─── Texto com highlight de keyword ─────────────────────────────────────────

@Composable
fun HighlightedText(
    text: String,
    keyword: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (keyword.isBlank()) {
        Text(text = text, color = color, modifier = modifier, fontSize = 14.sp)
        return
    }
    val lower = text.lowercase()
    val kw    = keyword.lowercase()
    val parts = mutableListOf<Pair<String, Boolean>>()
    var start = 0
    var idx   = lower.indexOf(kw)
    while (idx >= 0) {
        if (idx > start) parts.add(text.substring(start, idx) to false)
        parts.add(text.substring(idx, idx + kw.length) to true)
        start = idx + kw.length
        idx = lower.indexOf(kw, start)
    }
    if (start < text.length) parts.add(text.substring(start) to false)

    val annotated = buildAnnotatedString {
        parts.forEach { (part, highlighted) ->
            if (highlighted) {
                withStyle(SpanStyle(
                    background = Color(0xFFFFEB3B),
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )) { append(part) }
            } else {
                withStyle(SpanStyle(color = color)) { append(part) }
            }
        }
    }
    Text(text = annotated, modifier = modifier, fontSize = 14.sp)
}