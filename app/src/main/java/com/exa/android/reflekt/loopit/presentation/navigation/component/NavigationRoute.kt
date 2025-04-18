package com.exa.android.reflekt.loopit.presentation.navigation.component

import android.net.Uri
import com.google.gson.Gson

sealed class AuthRoute(val route : String){
    object Login : AuthRoute("login")
    object Register : AuthRoute("register")
    object Verification : AuthRoute("verification")
    object ForgetPassword : AuthRoute("forget_password")
    companion object{
        const val ROOT="auth"
    }
}



sealed class MainRoute(val route : String){
    object Home : MainRoute("home")
    object Profile : MainRoute("status")
    object Setting : MainRoute("setting")
    object Map : MainRoute("map")
    object Project : MainRoute("project")
    companion object{
        const val ROOT="main_app"
    }
}


sealed class HomeRoute(val route: String) {
    object ChatList : HomeRoute("chats_list")

    //    object ChatDetail : HomeRoute("chat_detail/{userJson}"){
//        fun createRoute(userJson : String) : String = "chat_detail/${userJson}"
//    }
    object ChatDetail : HomeRoute("chat_detail/{userId}") {
        fun createRoute(userId: String): String = "chat_detail/${userId}"
    }

    object SearchScreen : HomeRoute("search")
    object ZoomImage : HomeRoute("zoomImage/{imageId}") {
        fun createRoute(imageId: Int): String = "zoomImage/$imageId"
    }
}

sealed class MapInfo(val route: String) {
    object MapScreen : MapInfo("map_screen")
}

sealed class ProjectRoute(val route: String) {
    object ProjectList : ProjectRoute("project_list")
    object ProjectDetail : ProjectRoute("project_detail/{projectId}") {
        fun createRoute(projectId: String): String = "project_detail/${projectId}"
    }
    object CreateProject : ProjectRoute("create_project")
    object EditProject : ProjectRoute("edit_project/{projectId}") {
        fun createRoute(projectId: String): String = "edit_project/${projectId}"
    }
    fun withArgs(vararg args: String): String {
        return buildString {
            append(route)
            args.forEach { arg ->
                append("/$arg")
            }
        }
    }
}

sealed class MeetingRoute(val route: String) {
    data object LobbyScreen : MeetingRoute("meeting_lobby/{usersJson}") {
        fun createRoute(users: List<String>): String {
            val usersJson = Uri.encode(Gson().toJson(users))  // Convert list to JSON & encode
            return "meeting_lobby/$usersJson"  // Corrected the mismatch
        }
    }

    data object CallScreen : MeetingRoute("meeting_call")
}


sealed class ChatInfo(val route: String) {
    object ProfileScreen : ChatInfo("profile")
    object ChatMedia : ChatInfo("media")
    object ProfileImage : ChatInfo("photo")
    object StarredMessage : ChatInfo("starred")
    object MediaVisibility : ChatInfo("visibility")
    object BlockUser : ChatInfo("block")
}

sealed class Call(val route: String) {
    object VoiceCall : Call("voice")
    object VideoCall : Call("video")
}

sealed class NavigationCommand {
    object ToMainApp : NavigationCommand()
    object ToAuth : NavigationCommand()
}


var bottomSheet: Boolean = false
