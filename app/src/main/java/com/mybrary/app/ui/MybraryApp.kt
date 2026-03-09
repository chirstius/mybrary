package com.mybrary.app.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mybrary.app.ui.addbook.AddBookScreen
import com.mybrary.app.ui.auth.AuthScreen
import com.mybrary.app.ui.auth.AuthViewModel
import com.mybrary.app.ui.bookdetail.BookDetailScreen
import com.mybrary.app.ui.library.LibraryScreen
import com.mybrary.app.ui.scanner.ScannerScreen
import com.mybrary.app.ui.settings.SettingsScreen
import com.mybrary.app.ui.setup.SetupScreen

internal object Routes {
    const val AUTH = "auth"
    const val SETUP = "setup"
    const val SETTINGS = "settings"
    const val LIBRARY = "library"
    const val SCANNER = "scanner"
    const val BOOK_DETAIL = "book/{bookId}"
    const val ADD_BOOK = "add?isbn={isbn}"

    fun bookDetail(bookId: String) = "book/$bookId"
    fun addBook(isbn: String = "") = "add?isbn=$isbn"
}

@Composable
fun MybraryApp(
    startIsbn: String = "",
    onNavControllerReady: (NavController) -> Unit = {},
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    LaunchedEffect(Unit) { onNavControllerReady(navController) }

    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(
                viewModel = authViewModel,
                onSignedIn = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                    if (startIsbn.isNotBlank()) {
                        navController.navigate(Routes.addBook(startIsbn))
                    }
                },
            )
        }

        // Accessible from the Library's "Sheet Settings" menu for power users
        composable(Routes.SETUP) {
            SetupScreen(
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.LIBRARY) {
            LibraryScreen(
                onBookClick = { bookId -> navController.navigate(Routes.bookDetail(bookId)) },
                onScanClick = { navController.navigate(Routes.SCANNER) },
                onAddClick = { navController.navigate(Routes.addBook()) },
                onSignOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.SCANNER) {
            ScannerScreen(
                onBack = { navController.popBackStack() },
                onBookAdded = { bookId ->
                    navController.navigate(Routes.bookDetail(bookId)) {
                        popUpTo(Routes.LIBRARY)
                    }
                },
                onAddManually = { isbn ->
                    navController.navigate(Routes.addBook(isbn)) {
                        popUpTo(Routes.LIBRARY)
                    }
                },
            )
        }

        composable(
            route = Routes.BOOK_DETAIL,
            arguments = listOf(navArgument("bookId") { type = NavType.StringType }),
        ) {
            BookDetailScreen(
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.ADD_BOOK,
            arguments = listOf(navArgument("isbn") {
                type = NavType.StringType
                defaultValue = ""
            }),
        ) { backStackEntry ->
            val isbn = backStackEntry.arguments?.getString("isbn") ?: ""
            AddBookScreen(
                prefillIsbn = isbn,
                onBack = { navController.popBackStack() },
                onSaved = { bookId ->
                    navController.navigate(Routes.bookDetail(bookId)) {
                        popUpTo(Routes.LIBRARY)
                    }
                },
            )
        }
    }
}
