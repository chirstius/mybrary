package com.mybrary.app.ui

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mybrary.app.ui.addbook.AddBookScreen
import com.mybrary.app.ui.auth.AuthScreen
import com.mybrary.app.ui.auth.AuthViewModel
import com.mybrary.app.ui.bookdetail.BookDetailScreen
import com.mybrary.app.ui.library.LibraryScreen
import com.mybrary.app.ui.scanner.ScannerScreen

private object Routes {
    const val AUTH = "auth"
    const val LIBRARY = "library"
    const val SCANNER = "scanner"
    const val BOOK_DETAIL = "book/{bookId}"
    const val ADD_BOOK = "add?isbn={isbn}"

    fun bookDetail(bookId: String) = "book/$bookId"
    fun addBook(isbn: String = "") = "add?isbn=$isbn"
}

@Composable
fun MybraryApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = Routes.AUTH) {
        composable(Routes.AUTH) {
            AuthScreen(
                viewModel = authViewModel,
                onSignedIn = {
                    navController.navigate(Routes.LIBRARY) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                },
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
