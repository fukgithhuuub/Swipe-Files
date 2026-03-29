package com.example.swipefiles

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FileCard(file: File, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(450.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // Visual Center
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                if (file.isDirectory) {
                    val itemCount = file.listFiles()?.size ?: 0
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "📁",
                            fontSize = 80.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$itemCount items inside",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                } else {
                    val ext = file.extension.lowercase(Locale.ROOT)
                    if (ext in listOf("jpg", "jpeg", "png", "gif", "mp4", "webp")) {
                        AsyncImage(
                            model = file,
                            contentDescription = "File thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else if (ext == "pdf") {
                        var pdfBitmap by remember { mutableStateOf<Bitmap?>(null) }
                        var hasError by remember { mutableStateOf(false) }

                        LaunchedEffect(file) {
                            withContext(Dispatchers.IO) {
                                var fd: ParcelFileDescriptor? = null
                                var renderer: PdfRenderer? = null
                                try {
                                    fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                                    renderer = PdfRenderer(fd)
                                    if (renderer.pageCount > 0) {
                                        val page = renderer.openPage(0)
                                        // create a bitmap
                                        val bitmap = Bitmap.createBitmap(
                                            page.width,
                                            page.height,
                                            Bitmap.Config.ARGB_8888
                                        )
                                        // render the page
                                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        pdfBitmap = bitmap
                                        page.close()
                                    }
                                } catch (e: Exception) {
                                    hasError = true
                                } finally {
                                    renderer?.close()
                                    fd?.close()
                                }
                            }
                        }

                        if (pdfBitmap != null) {
                            Image(
                                bitmap = pdfBitmap!!.asImageBitmap(),
                                contentDescription = "PDF Thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else if (hasError) {
                            Text(text = "📄 PDF", fontSize = 60.sp)
                        } else {
                            CircularProgressIndicator()
                        }
                    } else {
                        // Generic icon
                        Text(text = "📄", fontSize = 80.sp)
                    }
                }
            }

            // Metadata Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val fileSizeMb = if (!file.isDirectory) file.length() / (1024.0 * 1024.0) else 0.0
                    val sizeColor = if (fileSizeMb > 100) Color.Red else MaterialTheme.colorScheme.onSurface

                    Text(
                        text = if (file.isDirectory) "Folder" else String.format(Locale.US, "%.1f MB", fileSizeMb),
                        color = sizeColor,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )

                    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
                    Text(
                        text = "Modified: ${sdf.format(Date(file.lastModified()))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Path: ${file.parentFile?.absolutePath ?: "/"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
