package org.thoughtcrime.securesms.preferences

import android.app.Dialog
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import network.loki.messenger.BuildConfig
import network.loki.messenger.R
import org.session.libsignal.utilities.ExternalStorageUtil
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.ApplicationContext
import org.thoughtcrime.securesms.createSessionDialog
import org.thoughtcrime.securesms.util.FileProviderUtil
import org.thoughtcrime.securesms.util.StreamUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Objects
import java.util.concurrent.TimeUnit

class ShareLogsDialog : DialogFragment() {

    private var shareJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = createSessionDialog {
        title(R.string.dialog_share_logs_title)
        text(R.string.dialog_share_logs_explanation)
        button(R.string.share) { shareLogs() }
        cancelButton { dismiss() }
    }

    private fun shareLogs() {
        shareJob?.cancel()
        shareJob = lifecycleScope.launch(Dispatchers.IO) {
            val persistentLogger = ApplicationContext.getInstance(context).persistentLogger
            try {
                val context = requireContext()
                val outputUri: Uri = ExternalStorageUtil.getDownloadUri()
                val mediaUri = getExternalFile()
                if (mediaUri == null) {
                    // show toast saying media saved
                    dismiss()
                    return@launch
                }

                val inputStream = persistentLogger.logs.get().byteInputStream()
                val updateValues = ContentValues()
                if (outputUri.scheme == ContentResolver.SCHEME_FILE) {
                    FileOutputStream(mediaUri.path).use { outputStream ->
                        StreamUtil.copy(inputStream, outputStream)
                        MediaScannerConnection.scanFile(context, arrayOf(mediaUri.path), arrayOf("text/plain"), null)
                    }
                } else {
                    context.contentResolver.openOutputStream(mediaUri, "w").use { outputStream ->
                        val total: Long = StreamUtil.copy(inputStream, outputStream)
                        if (total > 0) {
                            updateValues.put(MediaStore.MediaColumns.SIZE, total)
                        }
                    }
                }
                if (Build.VERSION.SDK_INT > 28) {
                    updateValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                if (updateValues.size() > 0) {
                    requireContext().contentResolver.update(mediaUri, updateValues, null, null)
                }

                val shareUri = if (mediaUri.scheme == ContentResolver.SCHEME_FILE) {
                    FileProviderUtil.getUriFor(context, File(mediaUri.path!!))
                } else {
                    mediaUri
                }

                withContext(Main) {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, shareUri)
                        type = "text/plain"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)))
                }

                dismiss()
            } catch (e: Exception) {
                withContext(Main) {
                    Log.e("Loki", "Error saving logs", e)
                    Toast.makeText(context,"Error saving logs", Toast.LENGTH_LONG).show()
                }
                dismiss()
            }
        }
    }

    @Throws(IOException::class)
    private fun pathTaken(outputUri: Uri, dataPath: String): Boolean {
        requireContext().contentResolver.query(outputUri, arrayOf(MediaStore.MediaColumns.DATA),
            MediaStore.MediaColumns.DATA + " = ?", arrayOf(dataPath),
            null).use { cursor ->
            if (cursor == null) {
                throw IOException("Something is wrong with the filename to save")
            }
            return cursor.moveToFirst()
        }
    }

    private fun getExternalFile(): Uri? {
        val context = requireContext()
        val base = "${Build.MANUFACTURER}-${Build.DEVICE}-API${Build.VERSION.SDK_INT}-v${BuildConfig.VERSION_NAME}-${System.currentTimeMillis()}"
        val extension = "txt"
        val fileName = "$base.$extension"
        val mimeType = MimeTypeMap.getSingleton().getExtensionFromMimeType("text/plain")
        val outputUri: Uri = ExternalStorageUtil.getDownloadUri()
        val contentValues = ContentValues()
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        contentValues.put(MediaStore.MediaColumns.DATE_ADDED, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
        contentValues.put(MediaStore.MediaColumns.DATE_MODIFIED, TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()))
        if (Build.VERSION.SDK_INT > 28) {
            contentValues.put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else if (Objects.equals(outputUri.scheme, ContentResolver.SCHEME_FILE)) {
            val outputDirectory = File(outputUri.path)
            var outputFile = File(outputDirectory, "$base.$extension")
            var i = 0
            while (outputFile.exists()) {
                outputFile = File(outputDirectory, base + "-" + ++i + "." + extension)
            }
            if (outputFile.isHidden) {
                throw IOException("Specified name would not be visible")
            }
            return Uri.fromFile(outputFile)
        } else {
            var outputFileName = fileName
            val externalPath = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)!!
            var dataPath = String.format("%s/%s", externalPath, outputFileName)
            var i = 0
            while (pathTaken(outputUri, dataPath)) {
                outputFileName = base + "-" + ++i + "." + extension
                dataPath = String.format("%s/%s", externalPath, outputFileName)
            }
            contentValues.put(MediaStore.MediaColumns.DATA, dataPath)
        }
        return context.contentResolver.insert(outputUri, contentValues)
    }


}