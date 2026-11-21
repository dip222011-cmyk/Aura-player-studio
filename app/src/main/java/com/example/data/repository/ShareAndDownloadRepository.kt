package com.example.data.repository

import com.example.data.local.dao.DownloadDao
import com.example.data.local.dao.TransferDao
import com.example.data.local.entity.DownloadTaskEntity
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShareAndDownloadRepository @Inject constructor(
    private val transferDao: TransferDao,
    private val downloadDao: DownloadDao
) {
    val transferHistory: Flow<List<TransferRecordEntity>> = transferDao.observeTransferHistory()
    val pairedDevices: Flow<List<PairedDeviceEntity>> = transferDao.observePairedDevices()
    val downloads: Flow<List<DownloadTaskEntity>> = downloadDao.observeDownloads()

    suspend fun logTransfer(record: TransferRecordEntity) {
        transferDao.insertTransfer(record)
    }

    suspend fun deleteTransferRecord(id: String) {
        transferDao.deleteTransfer(id)
    }

    suspend fun clearTransferHistory() {
        transferDao.clearHistory()
    }

    suspend fun savePairedDevice(device: PairedDeviceEntity) {
        transferDao.upsertPairedDevice(device)
    }

    suspend fun removePairedDevice(deviceId: String) {
        transferDao.removePairedDevice(deviceId)
    }

    suspend fun upsertDownload(task: DownloadTaskEntity) {
        downloadDao.upsertDownload(task)
    }

    suspend fun deleteDownload(id: String) {
        downloadDao.deleteDownload(id)
    }
}
