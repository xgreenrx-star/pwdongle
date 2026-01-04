package com.pwdongle.recorder

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Manages macro file storage and retrieval
 */
class MacroFileManager(private val context: Context) {
    
    companion object {
        private const val MACRO_DIR = "PWDongle"
        private const val MACRO_EXTENSION = ".txt"
    }
    
    private val macroDir: File by lazy {
        // Use app-scoped Documents (no legacy storage permission required on API 29+)
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: File(context.filesDir, Environment.DIRECTORY_DOCUMENTS ?: "Documents")
        val dir = File(base, MACRO_DIR)
        dir.mkdirs()
        dir
    }
    
    /**
     * Save macro to file
     */
    suspend fun saveMacro(filename: String, content: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val safeFilename = sanitizeFilename(filename)
            val file = File(macroDir, "$safeFilename$MACRO_EXTENSION")
            
            file.writeText(content, Charsets.UTF_8)
            Result.success(file.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Load macro from file
     */
    suspend fun loadMacro(filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(macroDir, "$filename$MACRO_EXTENSION")
            if (!file.exists()) {
                return@withContext Result.failure(Exception("File not found: $filename"))
            }
            
            val content = file.readText(Charsets.UTF_8)
            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List all macro files
     */
    suspend fun listMacros(): Result<List<MacroFile>> = withContext(Dispatchers.IO) {
        try {
            val files = macroDir.listFiles { file ->
                file.isFile && file.extension == "txt"
            }?.map { file ->
                MacroFile(
                    name = file.nameWithoutExtension,
                    filename = file.name,
                    path = file.absolutePath,
                    size = file.length(),
                    modified = file.lastModified()
                )
            }?.sortedByDescending { it.modified } ?: emptyList()
            
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete macro file
     */
    suspend fun deleteMacro(filename: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val file = File(macroDir, "$filename$MACRO_EXTENSION")
            val deleted = file.delete()
            if (deleted) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to delete file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Rename macro file
     */
    suspend fun renameMacro(oldName: String, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val oldFile = File(macroDir, "$oldName$MACRO_EXTENSION")
            val newFile = File(macroDir, "$newName$MACRO_EXTENSION")
            
            val renamed = oldFile.renameTo(newFile)
            if (renamed) {
                Result.success(true)
            } else {
                Result.failure(Exception("Failed to rename file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Export macro (share to another app)
     */
    suspend fun getMacroFile(filename: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val file = File(macroDir, "$filename$MACRO_EXTENSION")
            if (file.exists()) {
                Result.success(file)
            } else {
                Result.failure(Exception("File not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Generate macro with header
     */
    fun generateMacroContent(
        name: String,
        duration: Long,
        eventCount: Int,
        events: List<String>
    ): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val dateStr = formatter.format(Date())
        
        return buildString {
            appendLine("// Recorded macro: $name")
            appendLine("// Date: $dateStr")
            appendLine("// Duration: ${duration}ms (${duration / 1000.0}s)")
            appendLine("// Events: $eventCount")
            appendLine()
            appendLine("{{MOUSE:RESET}}")
            appendLine("{{DELAY:100}}")
            appendLine()
            events.forEach { event ->
                appendLine(event)
            }
        }
    }
    
    private fun sanitizeFilename(filename: String): String {
        return filename.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }
}

/**
 * Data class for macro file information
 */
data class MacroFile(
    val name: String,
    val filename: String,
    val path: String,
    val size: Long,
    val modified: Long
) {
    val sizeString: String
        get() = when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> "${size / (1024 * 1024)} MB"
        }
    
    val modifiedString: String
        get() {
            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.US)
            return formatter.format(Date(modified))
        }
}
