package com.example.swipefiles

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object FileManager {

    private const val TRASH_FOLDER_NAME = ".swipe_trash"
    private val TAG = "FileManager"

    // Base directory for managing external storage
    val baseDir: File = Environment.getExternalStorageDirectory()

    // Trash directory
    val trashDir: File
        get() = File(baseDir, TRASH_FOLDER_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }

    fun listDirectory(path: String = baseDir.absolutePath): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            Log.e(TAG, "Directory does not exist or is not a directory: $path")
            return emptyList()
        }

        // Exclude the trash folder itself from the list
        val files = dir.listFiles { file ->
            file.name != TRASH_FOLDER_NAME
        }?.toList() ?: emptyList()

        // Sort: Folders first, then files, both alphabetically
        return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun moveToTrash(file: File): File? {
        if (!file.exists()) return null

        val trash = trashDir
        val destFile = File(trash, file.name)

        // Handle name collision in trash
        var finalDest = destFile
        var counter = 1
        while (finalDest.exists()) {
            finalDest = File(trash, "${file.nameWithoutExtension}_$counter.${file.extension}")
            counter++
        }

        return try {
            val success = file.renameTo(finalDest)
            if (!success) {
               Log.e(TAG, "Failed to move file to trash: ${file.absolutePath}")
               null
            } else {
               finalDest
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception moving file to trash", e)
            null
        }
    }

    fun restoreFromTrash(trashedFile: File, originalParentPath: String, originalName: String): Boolean {
        if (!trashedFile.exists() || trashedFile.parentFile?.name != TRASH_FOLDER_NAME) return false

        val originalParent = File(originalParentPath)
        if (!originalParent.exists()) {
            originalParent.mkdirs()
        }

        val destFile = File(originalParent, originalName)
        return try {
            trashedFile.renameTo(destFile)
        } catch(e: Exception) {
            Log.e(TAG, "Exception restoring file", e)
            false
        }
    }

    fun organizeFile(file: File, folderName: String): File? {
        if (!file.exists()) return null
        val targetDir = File(baseDir, folderName).apply { if (!exists()) mkdirs() }
        var finalDest = File(targetDir, file.name)
        var counter = 1
        while (finalDest.exists()) {
            finalDest = File(targetDir, "${file.nameWithoutExtension}_$counter.${file.extension}")
            counter++
        }
        return try {
            if (file.renameTo(finalDest)) finalDest else null
        } catch(e: Exception) {
            null
        }
    }

    fun restoreFromOrganize(file: File, originalParentPath: String, originalName: String): Boolean {
        if (!file.exists()) return false
        val originalParent = File(originalParentPath).apply { if (!exists()) mkdirs() }
        val destFile = File(originalParent, originalName)
        return try {
            file.renameTo(destFile)
        } catch(e: Exception) {
            false
        }
    }

    fun deleteTrash(): Long {
        var deletedCount = 0L
        val trash = trashDir
        if (trash.exists() && trash.isDirectory) {
            trash.listFiles()?.forEach { file ->
                if (file.deleteRecursively()) {
                    deletedCount++
                }
            }
        }
        return deletedCount
    }

    fun getTrashContents(): List<File> {
        val trash = trashDir
        if(!trash.exists() || !trash.isDirectory) return emptyList()
        return trash.listFiles()?.toList() ?: emptyList()
    }
}
