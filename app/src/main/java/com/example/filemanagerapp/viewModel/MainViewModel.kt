package com.example.filemanagerapp.viewModel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.filemanagerapp.model.Repository
import com.example.filemanagerapp.model.room.FileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.lang.Exception
import java.math.BigInteger
import java.security.MessageDigest

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tag= "MainViewModel"

    private val repository = Repository.getRepository(getApplication())
    val defaultDirectory = File(Environment.getExternalStorageDirectory().path)

    private var curFile = defaultDirectory
    private val curFileMutableFlow: MutableStateFlow<File> = MutableStateFlow(curFile)
    val curFileStateFlow: StateFlow<File> = curFileMutableFlow

    private val sortByMutableStateFlow: MutableStateFlow<Int> = MutableStateFlow(1)
    val sortByStateFlow: StateFlow<Int> = sortByMutableStateFlow

    private val fileTypesList: MutableList<String> = mutableListOf()
    private val fileTypesMutableStateFlow: MutableStateFlow<List<String>> = MutableStateFlow(fileTypesList.toList())
    val fileTypesStateFlow: StateFlow<List<String>> = fileTypesMutableStateFlow

    private var dateOfCreationDatabase: String
    private var isDatabaseCreated= false
    private val saveInfo: SharedPreferences

    init {
        saveInfo = application.getSharedPreferences("saveInfo", Context.MODE_PRIVATE)
        dateOfCreationDatabase = saveInfo.getString("dateOfCreationDatabase", "") ?: ""
        isDatabaseCreated = saveInfo.getBoolean("isDatabaseCreated", false)
    }

    fun setCurFile(newFile: File) {
        curFile = newFile
        curFileMutableFlow.value = curFile
    }

    fun getCurFile(): File {
        return curFile
    }

    fun setSortBy(sortBy: Int) {
        sortByMutableStateFlow.value = sortBy
    }

    fun getSortBy(): Int {
        return sortByMutableStateFlow.value
    }

    fun setFileTypes(newList: List<String>) {
        fileTypesList.clear()
        fileTypesList.addAll(newList)

        fileTypesMutableStateFlow.value = fileTypesList.toList()
    }

    fun getFileTypes(): List<String> {
        return fileTypesMutableStateFlow.value
    }

    fun getRecentChanged(): Flow<List<FileEntity>> {
        return repository.getAllRecentChanged()
    }

    fun fillDatabase(newFilesList: List<File>) {
        viewModelScope.launch {
            if (!isDatabaseCreated) {
                for (i in newFilesList.indices) {
                    val curFile = newFilesList[i]
                    val newHash = createSHA256(curFile)
                    val newFileEntity = FileEntity(0, curFile.path, newHash, false)
                    repository.addFile(newFileEntity)
                }
                isDatabaseCreated = true
                saveInfo.edit().putBoolean("isDatabaseCreated", isDatabaseCreated).apply()
                return@launch
            }

            val filesFromDatabase = repository.getAllFiles()
            for (i in filesFromDatabase.indices) {
                val tempFile = File(filesFromDatabase[i].path)
                if (!tempFile.exists()) {
                    repository.deleteFile(filesFromDatabase[i].id)
                }
            }

            for (i in newFilesList.indices) {
                val curFile = newFilesList[i]
                if (repository.isExist(curFile.path)) {
                    val oldFile = repository.getFileId(curFile.path)
                    val newHash = createSHA256(curFile)
                    if (newHash.isNotEmpty()) {
                        if (oldFile.hash == newHash) {
                            repository.updateRecentChanged(false, oldFile.id)
                        } else {
                            repository.updateHash(newHash, true, oldFile.id)
                        }
                    }
                } else {
                    val hash = createSHA256(curFile)
                    if (hash.isNotEmpty()) {
                        val newFile = FileEntity(0, curFile.path, hash, true)
                        repository.addFile(newFile)
                    }
                }
            }
        }
    }

    fun createSHA256(file: File): String {
        try {
            val buffer = ByteArray(1024)
            val inputStream = FileInputStream(file)

            val md = MessageDigest.getInstance("SHA-256")

            while (true) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    md.update(buffer, 0, bytesRead)
                } else {
                    break
                }
            }

            val messageDigest = md.digest()
            val no = BigInteger(1, messageDigest)
            var hashText = no.toString(16)
            while (hashText.length < 32) {
                hashText = "0$hashText"
            }
            return hashText
        } catch (e: Exception) {
            Log.e(tag, "createSHA_256: Сouldn't open the file: ${file.path}, ${e.stackTrace}")
        }
        return ""
    }
}