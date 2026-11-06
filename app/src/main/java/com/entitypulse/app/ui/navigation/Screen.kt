package com.entitypulse.app.ui.navigation

sealed class Screen(val route: String) {
    object Hub : Screen("hub")
    object Feed : Screen("feed")
    object Settings : Screen("settings")

    object EntityDetail : Screen("entity/{entityId}") {
        fun createRoute(entityId: Long) = "entity/$entityId"
    }

    object PostDetail : Screen("post/{postId}") {
        fun createRoute(postId: Long) = "post/$postId"
    }
}
