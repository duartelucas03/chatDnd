package com.example.easychat.ui.main

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.easychat.R
import com.example.easychat.ui.profile.ProfileScreen
import com.example.easychat.ui.search.SearchUserActivity

sealed class MainTab(val label: String, val iconRes: Int) {
    object Chat    : MainTab("Conversas", R.drawable.chat_icon)
    object Profile : MainTab("Perfil",    R.drawable.person_icon)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf<MainTab>(MainTab.Chat) }
    val tabs = listOf(MainTab.Chat, MainTab.Profile)

    Scaffold(
        topBar = {
            if (currentTab == MainTab.Chat) {
                TopAppBar(
                    title = { Text("EasyChat") },
                    actions = {
                        IconButton(onClick = {
                            context.startActivity(Intent(context, SearchUserActivity::class.java))
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.icon_search),
                                contentDescription = "Buscar usuário"
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(80.dp)
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = tab.label,
                                modifier = Modifier.size(20.dp),

                            )
                        },
                        label = {
                            Text(tab.label, fontSize = 11.sp)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentTab) {
                MainTab.Chat    -> ChatListScreen(viewModel = viewModel)
                MainTab.Profile -> ProfileScreen()
            }
        }
    }
}