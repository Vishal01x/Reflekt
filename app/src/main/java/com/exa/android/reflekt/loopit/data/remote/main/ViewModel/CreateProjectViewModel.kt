package com.exa.android.reflekt.loopit.data.remote.main.ViewModel

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exa.android.reflekt.loopit.data.remote.main.MapDataSource.FirebaseDataSource
import com.exa.android.reflekt.loopit.data.remote.main.Repository.MediaSharingRepository
import com.exa.android.reflekt.loopit.data.remote.main.Repository.ProfileRepository
import com.exa.android.reflekt.loopit.data.remote.main.Repository.ProjectRepository
import com.exa.android.reflekt.loopit.util.application.getOrThrow
import com.exa.android.reflekt.loopit.util.model.PostType
import com.exa.android.reflekt.loopit.util.model.Project
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

@HiltViewModel  // <-- Add this
class CreateProjectViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val repository: ProjectRepository,
    private val auth: FirebaseAuth,
    private val mediaSharingRepo: MediaSharingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = mutableStateOf(CreateProjectState())
    val state: State<CreateProjectState> = _state

    init {
        loadAvailableFilters()
    }

    fun onTitleChange(title: String) {
        _state.value = _state.value.copy(
            title = title,
            titleError = if (title.length < 3) "Title must be at least 3 characters" else null
        )
        updateCanSubmit()
    }

    fun onDescriptionChange(description: String) {
        _state.value = _state.value.copy(
            description = description,
            descriptionError = if (description.length < 10) "Description must be at least 10 characters" else null
        )
        updateCanSubmit()
    }

    fun onRoleAdded(role: String) {
        _state.value = _state.value.copy(
            selectedRoles = _state.value.selectedRoles + role
        )
        updateCanSubmit()
    }

    fun onRoleRemoved(role: String) {
        _state.value = _state.value.copy(
            selectedRoles = _state.value.selectedRoles - role
        )
        updateCanSubmit()
    }

    fun onTagAdded(tag: String) {
        _state.value = _state.value.copy(
            selectedTags = _state.value.selectedTags + tag
        )
        updateCanSubmit()
    }

    fun onTagRemoved(tag: String) {
        _state.value = _state.value.copy(
            selectedTags = _state.value.selectedTags - tag
        )
        updateCanSubmit()
    }

    @SuppressLint("TimberArgCount")
    fun createProject() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                error = null,
                isSuccess = false
            )
            // Log.d("project", "profile: ${auth.currentUser} ${auth.currentUser?.uid}")

            val currentUser = auth.currentUser ?: run {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "User not authenticated"
                )
                return@launch
            }
            // Log.d("project", "profile: $currentUser")
            val profileResult = try {
                profileRepository.getUserProfile(currentUser.uid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Failed to fetch user profile: ${e.message}"
                )
                // Log.d("project", "profile: ${e.message}")
                return@launch
            }
            // Log.d("project", "profile: $profileResult")

            val fullName = profileResult.name.ifBlank {
                currentUser.displayName ?: "Anonymous"
            }
            val imageUrls = _state.value.images.map { uri ->
                try {
                    // Convert URI to File
                    val file = createTempFileFromUri(context, uri)
                    // Upload to Cloudinary
                    mediaSharingRepo.uploadFileToCloudinary(file)
                } catch (e: Exception) {
                    throw IOException("Failed to upload image: ${e.message}", e)
                }
            }

            Timber.d("project", "profile image urls: $imageUrls")
            Timber.d("project", "profile state images: ${_state.value.images}")
            Timber.d("project", "profile urls: ${_state.value.urls}")
            // Log.d("project", "profile full name: $fullName")

            val project = Project(
                id = UUID.randomUUID().toString(),
                title = _state.value.title.trim(),
                description = _state.value.description.trim(),
                rolesNeeded = _state.value.selectedRoles.toList(),
                tags = _state.value.selectedTags.toList(),
                createdBy = currentUser.uid,
                createdByName = fullName,
                createdAt = Timestamp.now(),
                type = _state.value.postType,
                imageUrls = imageUrls.filterNotNull(),
                links = _state.value.urls,
            )
            Log.d("project", "createProject: $project")
            try {
                repository.createProject(project)
                _state.value = _state.value.copy(
                    isLoading = false,
                    isSuccess = true
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to create project"
                )
            }
        }
    }
    private suspend fun createTempFileFromUri(context: Context, uri: Uri): File {
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: throw IOException("Could not open file")

            val file = File.createTempFile("upload_", ".tmp", context.cacheDir)

            try {
                FileOutputStream(file).use { output ->
                    inputStream.copyTo(output)
                }
            } finally {
                inputStream.close()
            }

            file
        }
    }

    private fun updateCanSubmit() {
        _state.value = _state.value.copy(
            canSubmit = _state.value.title.trim().isNotBlank() &&
                    _state.value.postType.isNotBlank() &&
                    _state.value.titleError == null
        )
    }

    private fun loadAvailableFilters() {
        viewModelScope.launch {
            try {
                val roles = repository.getAvailableRoles()
                val tags = repository.getAvailableTags()
                _state.value = _state.value.copy(
                    availableRoles = roles.getOrThrow(),
                    availableTags = tags.getOrThrow()
                )
            } catch (e: Exception) {
                // Fallback to default values if repository fails
                _state.value = _state.value.copy(
                    availableRoles = listOf(
                        "UI/UX Designer",
                        "React Developer",
                        "Backend Developer",
                        "Mobile Developer",
                        "Product Manager"
                    ),
                    availableTags = listOf(
                        "App Development",
                        "Web Development",
                        "Startup",
                        "Open Source",
                        "AI/ML"
                    ),
                    error = "Failed to load filters: ${e.message}"
                )
            }
        }
    }

    fun onNewRoleCreated(role: String) {
        if (role.isBlank()) return

        viewModelScope.launch {
            try {
                // Log.d("role", "New Role create viewModel: $role")
                repository.addNewRole(role).getOrThrow()
                // Refresh available roles
                val roles = repository.getAvailableRoles().getOrThrow()
                _state.value = _state.value.copy(
                    availableRoles = roles,
                    selectedRoles = _state.value.selectedRoles + role
                )
                // Log.d("role", "New Role create viewModel: ${_state.value.selectedRoles}")
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to add role: ${e.message}"
                )
            }
        }
    }

    fun onNewTagCreated(tag: String) {
        if (tag.isBlank()) return

        viewModelScope.launch {
            try {
                repository.addNewTag(tag).getOrThrow()
                // Refresh available tags
                val tags = repository.getAvailableTags().getOrThrow()
                _state.value = _state.value.copy(
                    availableTags = tags,
                    selectedTags = _state.value.selectedTags + tag
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to add tag: ${e.message}"
                )
            }
        }
    }
    fun togglePostTypeMenu(expanded: Boolean? = null) {
        _state.value = _state.value.copy(
            postTypeExpanded = expanded ?: !_state.value.postTypeExpanded
        )
    }

    fun setPostType(type: PostType) {
        _state.value = _state.value.copy(postType = type.displayName)
        updateCanSubmit()
    }

    fun addImages(uris: List<Uri>) {
        _state.value = _state.value.copy(images = _state.value.images + uris)
    }

    fun removeImage(uri: Uri) {
        _state.value = _state.value.copy(images = _state.value.images - uri)
    }

    fun addUrl(url: String) {
        _state.value = _state.value.copy(urls = _state.value.urls + url)
    }
    fun removeUrl(url: String) {
        _state.value = _state.value.copy(urls = _state.value.urls - url)
    }
}

data class CreateProjectState(
    val title: String = "",
    val titleError: String? = null,
    val description: String = "",
    val descriptionError: String? = null,
    val selectedRoles: Set<String> = emptySet(),
    val availableRoles: List<String> = emptyList(),
    val selectedTags: Set<String> = emptySet(),
    val availableTags: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val canSubmit: Boolean = false,
    val postType: String = "",
    val postTypeExpanded: Boolean = false,
    val images: List<Uri> = emptyList(),
    val urls: List<String> = emptyList(),
)