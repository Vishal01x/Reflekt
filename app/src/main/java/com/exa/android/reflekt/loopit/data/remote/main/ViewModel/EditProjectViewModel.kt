package com.exa.android.reflekt.loopit.data.remote.main.ViewModel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exa.android.reflekt.loopit.data.remote.main.Repository.MediaSharingRepository
import com.exa.android.reflekt.loopit.data.remote.main.Repository.ProjectRepository
import com.exa.android.reflekt.loopit.util.model.Comment
import com.exa.android.reflekt.loopit.util.model.PostType
import com.exa.android.reflekt.loopit.util.model.Project
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject

data class EditProjectState(
    val projectId: String = "",
    val title: String = "",
    val description: String = "",
    val selectedRoles: Set<String> = emptySet(),
    val selectedTags: Set<String> = emptySet(),
    val availableRoles: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val requestedMembers: List<RequestedMember> = emptyList(),
    val enrolledMembers: List<RequestedMember> = emptyList(),
    val createdBy: String = "",
    val createdByName: String = "",
    val createdAt: Timestamp? = null,
    val titleError: String? = null,
    val descriptionError: String? = null,
    val isLoading: Boolean = false,
    val isInitialLoadComplete: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val canSubmit: Boolean = false,
    val urls: List<String> = emptyList(),
    val newImages: List<Uri> = emptyList(),        // Newly added local images
    val existingImageUrls: List<String> = emptyList(), // Existing Cloudinary URLs
    val removedImageUrls: List<String> = emptyList(),  // Track removed existing URLs
    val likes: List<String> = emptyList(),
    val comments: List<Comment> = emptyList(),
    val type: String = PostType.PROJECT.displayName,
)

data class RequestedMember(
    val id: String,
    val name: String
)

@HiltViewModel
class EditProjectViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val auth: FirebaseAuth,
    private val mediaSharingRepo: MediaSharingRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(EditProjectState())
    val state = _state.asStateFlow()

    init {
        loadAvailableFilters()
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val result = repository.getProjectById(projectId)
            result.fold(
                onSuccess = { project ->
                    _state.update {
                        it.copy(
                            projectId = project.id,
                            title = project.title,
                            description = project.description,
                            selectedRoles = project.rolesNeeded.toSet(),
                            selectedTags = project.tags.toSet(),
                            requestedMembers = project.requestedPersons.map { (id, name) ->
                                RequestedMember(id, name)
                            },
                            enrolledMembers = project.enrolledPersons.map { (id, name) -> RequestedMember(id, name) },
                            createdBy = project.createdBy,
                            createdByName = project.createdByName,
                            createdAt = project.createdAt,
                            isInitialLoadComplete = true,
                            isLoading = false,
                            canSubmit = validateForm(project.title, project.description),
                            existingImageUrls = project.imageUrls,
                            newImages = emptyList(),
                            removedImageUrls = emptyList(),
                            urls = project.links,
                            likes = project.likes,
                            comments = project.comments,
                            type = project.type
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            error = e.message ?: "Failed to load project",
                            isLoading = false
                        )
                    }
                }
            )
        }
    }

    fun updateProject() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val currentState = state.value
            val currentUserId = auth.currentUser?.uid ?: run {
                _state.update { it.copy(
                    isLoading = false,
                    error = "User not authenticated"
                )}
                return@launch
            }
            val newUploadedUrls = state.value.newImages.mapNotNull { uri ->
                mediaSharingRepo.uploadFileToCloudinary(
                    createTempFileFromUri(context, uri)
                )
            }
            val finalImageUrls = state.value.existingImageUrls + newUploadedUrls

            val project = Project(
                id = currentState.projectId,
                title = currentState.title,
                description = currentState.description,
                rolesNeeded = currentState.selectedRoles.toList(),
                tags = currentState.selectedTags.toList(),
                createdBy = currentState.createdBy,
                createdAt = currentState.createdAt,
                createdByName = currentState.createdByName,
                enrolledPersons = currentState.enrolledMembers.associate { it.id to it.name },
                requestedPersons = currentState.requestedMembers.associate { it.id to it.name },
                type = currentState.type,
                imageUrls = finalImageUrls,
                links = currentState.urls,
                likes = currentState.likes,
                comments = currentState.comments

            )

            val result = repository.updateProject(project)
            _state.update {
                it.copy(
                    isLoading = false,
                    isSuccess = result.isSuccess,
                    error = result.exceptionOrNull()?.message
                )
            }
        }
    }

    fun deleteProject() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            val result = repository.deleteProject(state.value.projectId)
            _state.update {
                it.copy(
                    isLoading = false,
                    isSuccess = result.isSuccess,
                    error = result.exceptionOrNull()?.message
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

    fun onTitleChange(newTitle: String) {
        val error = if (newTitle.isBlank()) "Title cannot be empty" else null
        _state.update {
            it.copy(
                title = newTitle,
                titleError = error,
                canSubmit = validateForm(newTitle, it.description)
            )
        }
    }

    fun onDescriptionChange(newDescription: String) {
        val error = if (newDescription.isBlank()) "Description cannot be empty" else null
        _state.update {
            it.copy(
                description = newDescription,
                descriptionError = error,
                canSubmit = validateForm(it.title, newDescription)
            )
        }
    }

    fun onRoleAdded(role: String) {
        _state.update {
            it.copy(
                selectedRoles = it.selectedRoles + role,
                canSubmit = validateForm(it.title, it.description)
            )
        }
    }

    fun onRoleRemoved(role: String) {
        _state.update {
            it.copy(
                selectedRoles = it.selectedRoles - role,
                canSubmit = validateForm(it.title, it.description)
            )
        }
    }

    fun onTagAdded(tag: String) {
        _state.update {
            it.copy(
                selectedTags = it.selectedTags + tag,
                canSubmit = validateForm(it.title, it.description)
            )
        }
    }

    fun onTagRemoved(tag: String) {
        _state.update {
            it.copy(
                selectedTags = it.selectedTags - tag,
                canSubmit = validateForm(it.title, it.description)
            )
        }
    }

    fun onNewRoleCreated(role: String) {
        viewModelScope.launch {
            val result = repository.addNewRole(role)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            availableRoles = it.availableRoles + role,
                            selectedRoles = it.selectedRoles + role,
                            canSubmit = validateForm(it.title, it.description)
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to add role") }
                }
            )
        }
    }

    fun onNewTagCreated(tag: String) {
        viewModelScope.launch {
            val result = repository.addNewTag(tag)
            result.fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            availableTags = it.availableTags + tag,
                            selectedTags = it.selectedTags + tag,
                            canSubmit = validateForm(it.title, it.description)
                        )
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(error = e.message ?: "Failed to add tag") }
                }
            )
        }
    }

    private fun loadAvailableFilters() {
        viewModelScope.launch {
            val rolesResult = repository.getAvailableRoles()
            val tagsResult = repository.getAvailableTags()

            _state.update {
                it.copy(
                    availableRoles = rolesResult.getOrElse { emptyList() },
                    availableTags = tagsResult.getOrElse { emptyList() },
                    error = rolesResult.exceptionOrNull()?.message
                        ?: tagsResult.exceptionOrNull()?.message
                )
            }
        }
    }

    private fun validateForm(title: String, description: String): Boolean {
        return title.isNotBlank() && description.isNotBlank()
    }

    fun addUrl(url: String) {
        _state.update { it.copy(urls = it.urls + url) }
    }

    fun removeUrl(url: String) {
        _state.update { it.copy(urls = it.urls - url) }
    }
    // In EditProjectViewModel
    fun addImages(uris: List<Uri>) {
        _state.update { it.copy(newImages = it.newImages + uris) }
    }

    fun removeNewImage(uri: Uri) {
        _state.update { it.copy(newImages = it.newImages - uri) }
    }

    fun removeExistingImage(url: String) {
        _state.update {
            it.copy(
                existingImageUrls = it.existingImageUrls - url,
                removedImageUrls = it.removedImageUrls + url
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}