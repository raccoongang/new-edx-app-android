package org.openedx.core.module.download

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.openedx.core.BaseViewModel
import org.openedx.core.BlockType
import org.openedx.core.data.storage.CorePreferences
import org.openedx.core.domain.model.Block
import org.openedx.core.module.DownloadWorkerController
import org.openedx.core.module.db.DownloadDao
import org.openedx.core.module.db.DownloadModel
import org.openedx.core.module.db.DownloadedState
import org.openedx.core.utils.Sha1Util
import java.io.File

abstract class BaseDownloadViewModel(
    private val downloadDao: DownloadDao,
    private val preferencesManager: CorePreferences,
    private val workerController: DownloadWorkerController
) : BaseViewModel() {

    private val allBlocks = mutableListOf<Block>()

    private val downloadableChildrenMap = hashMapOf<String, List<String>>()
    private val downloadModelsStatus = hashMapOf<String, DownloadedState>()

    private val _downloadModelsStatusFlow = MutableSharedFlow<HashMap<String, DownloadedState>>()
    protected val downloadModelsStatusFlow = _downloadModelsStatusFlow.asSharedFlow()

    private var downloadingModelsList = listOf<DownloadModel>()
    private val _downloadingModelsFlow = MutableSharedFlow<List<DownloadModel>>()
    protected val downloadingModelsFlow = _downloadingModelsFlow.asSharedFlow()

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        viewModelScope.launch {
            downloadDao.readAllData().map { list -> list.map { it.mapToDomain() } }
                .collect { downloadModels ->
                    updateDownloadModelsStatus(downloadModels)
                    _downloadModelsStatusFlow.emit(downloadModelsStatus)
                }
        }
    }

    protected suspend fun initDownloadModelsStatus() {
        updateDownloadModelsStatus(getDownloadModelList())
        _downloadModelsStatusFlow.emit(downloadModelsStatus)
    }

    private suspend fun getDownloadModelList(): List<DownloadModel> {
        return downloadDao.readAllData().first().map { it.mapToDomain() }
    }

    private suspend fun updateDownloadModelsStatus(models: List<DownloadModel>) {
        for (item in downloadableChildrenMap) {
            if (models.find { item.value.contains(it.id) && it.downloadedState.isWaitingOrDownloading } != null) {
                downloadModelsStatus[item.key] = DownloadedState.DOWNLOADING
            } else if (item.value.all { id -> models.find { it.id == id && it.downloadedState == DownloadedState.DOWNLOADED } != null }) {
                downloadModelsStatus[item.key] = DownloadedState.DOWNLOADED
            } else {
                downloadModelsStatus[item.key] = DownloadedState.NOT_DOWNLOADED
            }
        }

        downloadingModelsList = models.filter {
            it.downloadedState == DownloadedState.DOWNLOADING ||
                    it.downloadedState == DownloadedState.WAITING
        }
        _downloadingModelsFlow.emit(downloadingModelsList)
    }

    protected fun setBlocks(list: List<Block>) {
        downloadableChildrenMap.clear()
        allBlocks.clear()
        allBlocks.addAll(list)
    }

    fun isBlockDownloading(id: String): Boolean {
        val blockDownloadingState = downloadModelsStatus[id]
        return blockDownloadingState == DownloadedState.DOWNLOADING || blockDownloadingState == DownloadedState.WAITING
    }

    fun isBlockDownloaded(id: String): Boolean {
        val blockDownloadingState = downloadModelsStatus[id]
        return blockDownloadingState == DownloadedState.DOWNLOADED
    }

    fun isAllBlocksDownloadedOrDownloading(): Boolean {
        downloadableChildrenMap.keys.forEach { id ->
            if (!isBlockDownloaded(id) && !isBlockDownloading(id)) return false
        }
        return true
    }

    open fun saveDownloadModels(folder: String, id: String) {
        viewModelScope.launch {
            val saveBlocksIds = downloadableChildrenMap[id] ?: listOf()
            val downloadModels = mutableListOf<DownloadModel>()
            for (blockId in saveBlocksIds) {
                allBlocks.find { it.id == blockId }?.let { block ->
                    val videoInfo =
                        block.studentViewData?.encodedVideos?.getPreferredVideoInfoForDownloading(
                            preferencesManager.videoSettings.videoDownloadQuality
                        )
                    val size = videoInfo?.fileSize ?: 0
                    val url = videoInfo?.url ?: ""
                    val extension = url.split('.').lastOrNull() ?: "mp4"
                    val path =
                        folder + File.separator + "${Sha1Util.SHA1(block.displayName)}.$extension"
                    if (getDownloadModelList().find { it.id == blockId && it.downloadedState.isDownloaded } == null) {
                        downloadModels.add(
                            DownloadModel(
                                block.id,
                                block.displayName,
                                size,
                                path,
                                url,
                                block.downloadableType,
                                DownloadedState.WAITING,
                                null
                            )
                        )
                    }
                }
            }
            workerController.saveModels(*downloadModels.toTypedArray())
        }
    }

    open fun saveAllDownloadModels(folder: String) {
        downloadableChildrenMap.keys.forEach { id ->
            allBlocks.find { it.id == id }?.let { block ->
                if (!isBlockDownloaded(block.id) && !isBlockDownloading(block.id)) {
                    saveDownloadModels(folder, block.id)
                }
            }
        }
    }

    fun removeDownloadedModels(id: String) {
        viewModelScope.launch {
            val saveBlocksIds = downloadableChildrenMap[id] ?: listOf()
            val downloaded =
                getDownloadModelList().filter { saveBlocksIds.contains(it.id) && it.downloadedState.isDownloaded }
            downloaded.forEach {
                downloadDao.removeDownloadModel(it.id)
            }
        }
    }

    fun removeOrCancelAllDownloadModels() {
        viewModelScope.launch {
            downloadableChildrenMap.keys.forEach { id ->
                if (isBlockDownloading(id)) {
                    cancelWork(id)
                } else {
                    removeDownloadedModels(id)
                }
            }
        }
    }

    fun getDownloadModelsStatus() = downloadModelsStatus.toMap()

    fun getRemainingDownloadModelsCount(): Int {
        return downloadableChildrenMap.keys
            .filter { !isBlockDownloaded(it) }
            .sumOf { downloadableChildrenMap[it]?.size ?: 0 }
    }

    fun getRemainingDownloadModelsSize(): Long {
        return downloadableChildrenMap.keys
            .filter { !isBlockDownloaded(it) }
            .sumOf { id ->
                var size = 0L
                downloadableChildrenMap[id]?.forEach { downloadableBlock ->
                    val block = allBlocks.find { it.id == downloadableBlock }
                    val videoInfo =
                        block?.studentViewData?.encodedVideos?.getPreferredVideoInfoForDownloading(
                            preferencesManager.videoSettings.videoDownloadQuality
                        )
                    size += videoInfo?.fileSize ?: 0
                }
                size
            }
    }

    fun getAllDownloadModelsCount(): Int {
        return downloadableChildrenMap.values
            .sumOf { it.size }
    }

    fun getAllDownloadModelsSize(): Long {
        return downloadableChildrenMap.values
            .sumOf { list ->
                var size = 0L
                list.forEach { downloadableBlock ->
                    val block = allBlocks.find { it.id == downloadableBlock }
                    val videoInfo =
                        block?.studentViewData?.encodedVideos?.getPreferredVideoInfoForDownloading(
                            preferencesManager.videoSettings.videoDownloadQuality
                        )
                    size += videoInfo?.fileSize ?: 0
                }
                size
            }
    }

    fun hasDownloadModelsInQueue() = downloadingModelsList.isNotEmpty()

    open fun cancelWork(blockId: String) {
        viewModelScope.launch {
            val downloadableChildren = downloadableChildrenMap[blockId] ?: listOf()
            val ids = getDownloadModelList().filter {
                (it.downloadedState == DownloadedState.DOWNLOADING ||
                        it.downloadedState == DownloadedState.WAITING) && downloadableChildren.contains(
                    it.id
                )
            }.map { it.id }
            workerController.cancelWork(*ids.toTypedArray())
        }
    }

    protected fun addDownloadableChildrenForSequentialBlock(sequentialBlock: Block) {
        for (item in sequentialBlock.descendants) {
            allBlocks.find { it.id == item }?.let { blockDescendant ->
                if (blockDescendant.type == BlockType.VERTICAL) {
                    for (unitBlockId in blockDescendant.descendants) {
                        allBlocks.find { it.id == unitBlockId && it.isDownloadable }?.let {
                            val id = sequentialBlock.id
                            val children = downloadableChildrenMap[id] ?: listOf()
                            downloadableChildrenMap[id] = children + it.id
                        }
                    }
                }
            }
        }
    }

    protected fun addDownloadableChildrenForVerticalBlock(verticalBlock: Block) {
        for (unitBlockId in verticalBlock.descendants) {
            allBlocks.find { it.id == unitBlockId && it.isDownloadable }?.let {
                val id = verticalBlock.id
                val children = downloadableChildrenMap[id] ?: listOf()
                downloadableChildrenMap[id] = children + it.id
            }
        }
    }

}
