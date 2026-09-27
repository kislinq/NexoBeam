package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.RegisterScreen
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.chatlist.ChatListScreen
import com.example.ui.screens.chatlist.SearchUsersScreen
import com.example.ui.screens.profile.ProfileScreen
import com.example.ui.screens.profile.ProfileSetupScreen
import com.example.ui.screens.splash.SplashScreen
import com.example.ui.theme.NexoBeamTheme
import com.example.ui.theme.NexoDesignConcept
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val notificationChatId = intent?.getStringExtra("chat_id")

        setContent {
            val sessionManager = NexoApplication.instance.sessionManager
            val currentConcept by sessionManager.designConceptFlow.collectAsState(
                initial = NexoDesignConcept.INDUSTRIAL_MONOCHROME
            )
            val scope = rememberCoroutineScope()

            NexoBeamTheme(concept = currentConcept) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NexoAppNavigation(
                        notificationChatId = notificationChatId,
                        onChangeConcept = { newConcept ->
                            scope.launch {
                                sessionManager.setDesignConcept(newConcept)
                            }
                        }
                    )
                }
            }
        }
    }
}

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val PROFILE_SETUP = "profile_setup"
    const val CHAT_LIST = "chat_list"
    const val SEARCH = "search"
    const val CHAT = "chat/{chatId}"
    const val PROFILE = "profile"

    fun chatRoute(chatId: String) = "chat/$chatId"
}

@Composable
fun NexoAppNavigation(
    notificationChatId: String?,
    onChangeConcept: (NexoDesignConcept) -> Unit
) {
    val navController = rememberNavController()
    val startDestination = if (notificationChatId != null) Routes.chatRoute(notificationChatId) else Routes.SPLASH

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onNavigateToChatList = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToProfileSetup = {
                    navController.navigate(Routes.PROFILE_SETUP) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Routes.REGISTER)
                },
                onLoginSuccess = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToProfileSetup = {
                    navController.navigate(Routes.PROFILE_SETUP) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.popBackStack()
                },
                onRegisterSuccess = {
                    navController.navigate(Routes.PROFILE_SETUP) {
                        popUpTo(Routes.REGISTER) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILE_SETUP) {
            ProfileSetupScreen(
                onSetupCompleted = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.PROFILE_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onOpenChat = { chatId ->
                    navController.navigate(Routes.chatRoute(chatId))
                },
                onNavigateToSearch = {
                    navController.navigate(Routes.SEARCH)
                },
                onNavigateToProfile = {
                    navController.navigate(Routes.PROFILE)
                },
                onChangeConcept = onChangeConcept
            )
        }

        composable(Routes.SEARCH) {
            SearchUsersScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onChatOpened = { chatId ->
                    navController.navigate(Routes.chatRoute(chatId)) {
                        popUpTo(Routes.SEARCH) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            ChatScreen(
                chatId = chatId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onNavigateBack = {
                    navController.popBackStack()
                },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onChangeConcept = onChangeConcept
            )
        }
    }
}
