package com.exa.android.reflekt.loopit.presentation.main.profile.feedback

import android.app.LocaleConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.exa.android.reflekt.R
import com.exa.android.reflekt.loopit.data.remote.main.ViewModel.EditProfileViewModel
import com.exa.android.reflekt.loopit.presentation.main.Home.component.ImageUsingCoil
import com.exa.android.reflekt.loopit.presentation.main.Home.component.showLoader
import com.exa.android.reflekt.loopit.util.Response
import com.exa.android.reflekt.loopit.util.model.Profile.ProfileHeaderData
import com.exa.android.reflekt.loopit.util.showToast


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerifierScreen(
    userId: String?,
    onProfileClick: (String) -> Unit,
    onBackClick: () -> Unit,
    editProfileViewModel: EditProfileViewModel = hiltViewModel()
) {
    val usersResponse by editProfileViewModel.verifiers.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(userId) {
        editProfileViewModel.getAllVerifiersDetail(userId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Verifier Users",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onBackClick() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->

        when (val response = usersResponse) {
            is Response.Loading -> {
                showLoader(message = "Verifier Loading...")
            }

            is Response.Error -> {
                showToast(context, "Error in loading Verifiers")
            }

            is Response.Success -> {
                val users = response.data
                LazyColumn(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                ) {
                    items(users) { user ->
                        VerifierUserItem(
                            user = user,
                            onClick = {
                                // Navigate to user profile
                                onProfileClick(user.uid)
                            }
                        )

                        if (users.last() != user) {
                            Divider(
                                modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerifierUserItem(
    user: ProfileHeaderData,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Profile Image
        ImageUsingCoil(
            context, user.profileImageUrl, R.drawable.placeholder,
            Modifier
                .size(48.dp)
                .clip(CircleShape)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Name
            Text(
                text = user.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiary
            )

            // Headline
            Text(
                text = user.headline,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onTertiary
            )
        }
    }
}

