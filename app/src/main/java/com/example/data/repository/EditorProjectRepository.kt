package com.example.data.repository

import com.example.data.local.dao.EditorProjectDao
import com.example.data.local.entity.EditorProjectEntity
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.TimelineProjectState
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EditorProjectRepository @Inject constructor(
    private val projectDao: EditorProjectDao,
    private val json: Json
) {
    val allProjects: Flow<List<EditorProjectEntity>> = projectDao.observeAllProjects()

    suspend fun createNewProject(
        name: String = "Studio Edit ${(System.currentTimeMillis() % 1000)}",
        aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_16_9
    ): TimelineProjectState {
        val id = UUID.randomUUID().toString()
        val state = TimelineProjectState(
            projectId = id,
            name = name.trim().ifBlank { "Untitled Studio Edit" },
            aspectRatio = aspectRatio
        )
        saveProject(state, isAutosave = false)
        return state
    }

    suspend fun loadProject(projectId: String): TimelineProjectState? {
        val entity = projectDao.getProjectById(projectId) ?: return null
        return runCatching {
            json.decodeFromString<TimelineProjectState>(entity.timelineJson)
        }.getOrNull()
    }

    suspend fun saveProject(state: TimelineProjectState, isAutosave: Boolean = true) {
        val existing = projectDao.getProjectById(state.projectId)
        val now = System.currentTimeMillis()
        val thumb = state.videoClips.firstOrNull()?.sourceUri
            ?: state.overlayClips.firstOrNull()?.sourceUri
            ?: existing?.thumbnailUri
        val entity = EditorProjectEntity(
            id = state.projectId,
            name = state.name,
            aspectRatio = state.aspectRatio.name,
            durationMs = state.totalDurationMs,
            clipCount = state.videoClips.size + state.overlayClips.size + state.audioClips.size + state.textLayers.size,
            thumbnailUri = thumb,
            timelineJson = json.encodeToString(state),
            createdAtMs = existing?.createdAtMs ?: now,
            updatedAtMs = now,
            autosavedAtMs = if (isAutosave) now else (existing?.autosavedAtMs ?: now)
        )
        projectDao.upsertProject(entity)
    }

    suspend fun duplicateProject(projectId: String): TimelineProjectState? {
        val source = loadProject(projectId) ?: return null
        val copy = source.copy(
            projectId = UUID.randomUUID().toString(),
            name = "${source.name} (Copy)"
        )
        saveProject(copy, isAutosave = false)
        return copy
    }

    suspend fun deleteProject(projectId: String) {
        projectDao.deleteProjectById(projectId)
    }
}
