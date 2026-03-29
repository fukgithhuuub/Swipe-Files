package com.example.swipefiles

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : ComponentActivity() {

    private val TAG = "MainActivity"
    private var isStoragePermissionGranted = mutableStateOf(false)

    // Launcher for MANAGE_EXTERNAL_STORAGE permission on Android 11+
    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                Log.d(TAG, "Storage permission granted")
                isStoragePermissionGranted.value = true
            } else {
                Toast.makeText(this, "Permission required to access files", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Launcher for legacy storage permissions (Android 10 and below)
    private val legacyPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) {
             Log.d(TAG, "Legacy storage permission granted")
             isStoragePermissionGranted.value = true
        } else {
             Toast.makeText(this, "Permission required to access files", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestStoragePermission()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(this, isStoragePermissionGranted.value)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check permissions when returning to the app
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
             isStoragePermissionGranted.value = Environment.isExternalStorageManager()
        } else {
             val readGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
             val writeGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
             isStoragePermissionGranted.value = readGranted && writeGranted
        }
    }

    private fun requestStoragePermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                manageStorageLauncher.launch(intent)
            } else {
                isStoragePermissionGranted.value = true
            }
        } else {
            val readGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (!readGranted || !writeGranted) {
                legacyPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                )
            } else {
                isStoragePermissionGranted.value = true
            }
        }
    }

    fun openFilePreview(context: Context, file: File) {
        if (!file.exists()) {
            Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
            return
        }

        if (file.isDirectory) return

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()) ?: "*/*"

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "Open File With..."))

        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "FileProvider error: ${e.message}")
            Toast.makeText(context, "Could not open file", Toast.LENGTH_SHORT).show()
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "No activity found to handle intent: ${e.message}")
            Toast.makeText(context, "No app found to open this file", Toast.LENGTH_SHORT).show()
        }
    }
}

// Action classes for Undo functionality
sealed class SwipeAction {
    data class Discard(val originalFile: File, val trashedFile: File, val originalParent: String) : SwipeAction()
    data class Keep(val file: File, val originalParent: String) : SwipeAction()
    data class Organize(val originalFile: File, val organizedFile: File, val originalParent: String) : SwipeAction()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(activity: MainActivity, isPermissionGranted: Boolean) {
    var currentPath by remember { mutableStateOf(FileManager.baseDir.absolutePath) }
    // Maintain a "processed" set for the current path
    var processedFiles by remember { mutableStateOf(setOf<String>()) }
    var currentFiles by remember { mutableStateOf(emptyList<File>()) }
    var actionHistory by remember { mutableStateOf(listOf<SwipeAction>()) }
    var swipedForDeletion by remember { mutableStateOf(listOf<File>()) }
    var showSessionSummary by remember { mutableStateOf(false) }

    var showOrganizeDialog by remember { mutableStateOf<File?>(null) }

    // Path history stack for proper back navigation without resetting state of everything
    var pathStack by remember { mutableStateOf(listOf<String>()) }

    fun loadFiles() {
        if (isPermissionGranted) {
             val allFiles = FileManager.listDirectory(currentPath)
             currentFiles = allFiles.filter { !processedFiles.contains(it.absolutePath) }
        }
    }

    // Load files when path changes
    LaunchedEffect(currentPath, isPermissionGranted) {
        loadFiles()
    }

    // Fallback refresh button if permission granted after load
    var refreshTrigger by remember { mutableStateOf(0) }
    LaunchedEffect(refreshTrigger) {
         processedFiles = emptySet() // Reset processed files on manual refresh
         loadFiles()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
             Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                  Button(onClick = {
                      val parent = File(currentPath).parentFile
                      if (parent != null && parent.absolutePath.startsWith(FileManager.baseDir.absolutePath)) {
                          currentPath = parent.absolutePath
                          // Clear processed files for the parent so we see everything not processed yet
                          processedFiles = emptySet()
                      } else {
                          Toast.makeText(activity, "Root reached", Toast.LENGTH_SHORT).show()
                      }
                  }) {
                      Text("Back")
                  }

                  Button(onClick = { showSessionSummary = true }) {
                      Text("Finalize Session")
                  }
             }

             Box(modifier = Modifier.weight(1f)) {
                 if (currentFiles.isNotEmpty() && isPermissionGranted) {
                     SwipeStack(
                         files = currentFiles,
                         onSwipeLeft = { file ->
                             // Discard
                             val trashedFile = FileManager.moveToTrash(file)
                             if (trashedFile != null) {
                                 val action = SwipeAction.Discard(file, trashedFile, currentPath)
                                 actionHistory = actionHistory + action
                                 swipedForDeletion = swipedForDeletion + trashedFile
                                 processedFiles = processedFiles + file.absolutePath
                                 currentFiles = currentFiles.filter { it.absolutePath != file.absolutePath }
                             }
                         },
                         onSwipeRight = { file ->
                             // Keep
                             val action = SwipeAction.Keep(file, currentPath)
                             actionHistory = actionHistory + action
                             processedFiles = processedFiles + file.absolutePath
                             currentFiles = currentFiles.filter { it.absolutePath != file.absolutePath }
                         },
                         onSwipeUp = { file ->
                             // Organize
                             showOrganizeDialog = file
                         },
                         onTap = { file ->
                             if (file.isDirectory) {
                                 currentPath = file.absolutePath
                                 processedFiles = emptySet()
                             } else {
                                 activity.openFilePreview(activity, file)
                             }
                         }
                     )
                 } else {
                      Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                          Text("No files or permission missing.")
                          Spacer(modifier = Modifier.height(8.dp))
                          Button(onClick = { refreshTrigger++ }) {
                              Text("Refresh")
                          }
                      }
                 }
             }
        }

        // Undo FAB
        if (actionHistory.isNotEmpty() && showOrganizeDialog == null) {
            FloatingActionButton(
                onClick = {
                    val lastAction = actionHistory.last()
                    actionHistory = actionHistory.dropLast(1)

                    when (lastAction) {
                        is SwipeAction.Discard -> {
                            if (FileManager.restoreFromTrash(lastAction.trashedFile, lastAction.originalParent, lastAction.originalFile.name)) {
                                swipedForDeletion = swipedForDeletion.filter { it.absolutePath != lastAction.trashedFile.absolutePath }
                                processedFiles = processedFiles - lastAction.originalFile.absolutePath
                                if (currentPath == lastAction.originalParent) {
                                    currentFiles = listOf(lastAction.originalFile) + currentFiles
                                }
                            }
                        }
                        is SwipeAction.Keep -> {
                            processedFiles = processedFiles - lastAction.file.absolutePath
                            if (currentPath == lastAction.originalParent) {
                                currentFiles = listOf(lastAction.file) + currentFiles
                            }
                        }
                        is SwipeAction.Organize -> {
                            if (FileManager.restoreFromOrganize(lastAction.organizedFile, lastAction.originalParent, lastAction.originalFile.name)) {
                                processedFiles = processedFiles - lastAction.originalFile.absolutePath
                                if (currentPath == lastAction.originalParent) {
                                    currentFiles = listOf(lastAction.originalFile) + currentFiles
                                }
                            }
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(32.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Undo")
            }
        }

        showOrganizeDialog?.let { file ->
             OrganizeMenuDialog(
                 file = file,
                 onOrganize = { folderName ->
                     val organizedFile = FileManager.organizeFile(file, folderName)
                     if(organizedFile != null) {
                         val action = SwipeAction.Organize(file, organizedFile, currentPath)
                         actionHistory = actionHistory + action
                         processedFiles = processedFiles + file.absolutePath
                         currentFiles = currentFiles.filter { it.absolutePath != file.absolutePath }
                     }
                     showOrganizeDialog = null
                 },
                 onDismiss = {
                     showOrganizeDialog = null
                 }
             )
        }

        if (showSessionSummary) {
             SessionSummaryDialog(
                 swipedFiles = swipedForDeletion,
                 onConfirm = {
                     val deletedCount = FileManager.deleteTrash()
                     Toast.makeText(activity, "Deleted $deletedCount files from trash", Toast.LENGTH_LONG).show()
                     swipedForDeletion = emptyList()
                     actionHistory = emptyList() // clear history on finalize
                     showSessionSummary = false
                 },
                 onDismiss = {
                     showSessionSummary = false
                 }
             )
        }
    }
}

@Composable
fun OrganizeMenuDialog(file: File, onOrganize: (String) -> Unit, onDismiss: () -> Unit) {
    val options = listOf("Starred", "Documents", "Projects", "Archive")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move '${file.name}' to...") },
        text = {
            Column {
                options.forEach { folder ->
                    Text(
                        text = "📁 $folder",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOrganize(folder) }
                            .padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun calculateTotalSize(files: List<File>): Long {
    var size = 0L
    for(file in files) {
       if (file.isDirectory) {
           val innerFiles = file.listFiles()
           if(innerFiles != null) {
               size += calculateTotalSize(innerFiles.toList())
           }
       } else {
           size += file.length()
       }
    }
    return size
}

@Composable
fun SessionSummaryDialog(swipedFiles: List<File>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val totalSize = calculateTotalSize(swipedFiles)
    val sizeMb = totalSize / (1024.0 * 1024.0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finalize Session") },
        text = {
            Text("You swiped ${swipedFiles.size} files (%.1f MB) for deletion. Confirm?".format(sizeMb))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Confirm", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
