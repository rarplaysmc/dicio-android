/*
 * Taken from /e/OS Assistant
 *
 * Copyright (C) 2024 MURENA SAS
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.stypox.dicio.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.stypox.dicio.ui.util.Progress
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

private const val CHUNK_SIZE = 1024 * 256 // 0.25 MB
private const val CONTENT_LENGTH = "Content-Length"
private const val DOT_PART_SUFFIX = ".part"
private const val LAST_DOWNLOAD_URL_CHECK = ".url.txt"
private const val RESUME_SUFFIX = ".resume"
private const val MAX_ATTEMPTS = 8 // in a row without receiving any new data
private const val RETRY_DELAY_MILLIS = 2000L


data class FileToDownload(
    val url: String,
    val file: File,
    val lastDownloadedUrlFile: File = File(file.parentFile, file.name + LAST_DOWNLOAD_URL_CHECK)
) {
    fun needsToBeDownloaded(): Boolean {
        return try {
            lastDownloadedUrlFile.readText() != url
        } catch (e: IOException) {
            // lastDownloadedUrlFile file does not exist
            true
        }
    }
}

suspend fun downloadBinaryFilesWithPartial(
    urlsFiles: List<FileToDownload>,
    httpClient: OkHttpClient,
    cacheDir: File,
    progressCallback: (Progress) -> Unit,
) {
    progressCallback(Progress.UNKNOWN)
    val filesNeedingDownload = urlsFiles.filter(FileToDownload::needsToBeDownloaded)

    for ((i, f) in filesNeedingDownload.withIndex()) {
        yield()
        progressCallback(Progress(i, filesNeedingDownload.size, 0, 0))

        downloadBinaryFileResumable(
            httpClient = httpClient,
            url = f.url,
            file = f.file,
            cacheDir = cacheDir,
        ) { currentBytes, totalBytes ->
            progressCallback(Progress(i, filesNeedingDownload.size, currentBytes, totalBytes))
        }

        f.lastDownloadedUrlFile.writeText(f.url)
    }

    progressCallback(Progress(filesNeedingDownload.size, filesNeedingDownload.size, 0, 0))
}

/**
 * Downloads [url] into [file], surviving dropped connections: when the connection breaks, the
 * download is retried up to [MAX_ATTEMPTS] times and continues from where it stopped (using an
 * HTTP `Range` request) instead of starting over. The partially downloaded data is kept in
 * [cacheDir] under a name tied to the url, so even pressing "retry" later resumes the download.
 */
@Throws(IOException::class)
suspend fun downloadBinaryFileResumable(
    httpClient: OkHttpClient,
    url: String,
    file: File,
    cacheDir: File,
    progressCallback: (currentBytes: Long, totalBytes: Long) -> Unit,
) = withContext(Dispatchers.IO) {
    val resumeFile = File(cacheDir, file.name + "." + Integer.toHexString(url.hashCode()) + RESUME_SUFFIX)
    var lastError: IOException? = null
    var attemptsWithoutProgress = 0

    while (attemptsWithoutProgress < MAX_ATTEMPTS) {
        yield()
        val startOffset = if (resumeFile.exists()) resumeFile.length() else 0L
        try {
            val requestBuilder = Request.Builder().url(url)
            if (startOffset > 0) {
                requestBuilder.header("Range", "bytes=$startOffset-")
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 416) {
                    // the saved partial data does not match the file on the server, start over
                    resumeFile.delete()
                    throw IOException("Server rejected resuming the download")
                }
                if (!response.isSuccessful) {
                    throw IOException("Server answered with error ${response.code}")
                }

                val resuming = startOffset > 0 && response.code == 206
                val body = response.body ?: throw IOException("Response doesn't contain a file")
                val remainingBytes = response.header(CONTENT_LENGTH)?.toLongOrNull() ?: 0L
                val alreadyBytes = if (resuming) startOffset else 0L
                val totalBytes = if (remainingBytes > 0) alreadyBytes + remainingBytes else 0L

                FileOutputStream(resumeFile, resuming).use { output ->
                    BufferedInputStream(body.byteStream()).use { input ->
                        val dataBuffer = ByteArray(CHUNK_SIZE)
                        var currentBytes = alreadyBytes
                        progressCallback(currentBytes, totalBytes)
                        while (true) {
                            val readBytes = input.read(dataBuffer)
                            if (readBytes == -1) break
                            yield()
                            output.write(dataBuffer, 0, readBytes)
                            currentBytes += readBytes.toLong()
                            progressCallback(currentBytes, totalBytes)
                        }
                    }
                }

                if (totalBytes > 0 && resumeFile.length() < totalBytes) {
                    throw IOException("Connection closed before the download finished")
                }
            }

            // the file has been fully downloaded, so we can move it in place
            file.delete()
            if (!resumeFile.renameTo(file)) {
                throw IOException("Cannot rename partial file $resumeFile to actual file $file")
            }
            return@withContext

        } catch (e: IOException) {
            lastError = e
            val madeProgress = resumeFile.exists() && resumeFile.length() > startOffset
            attemptsWithoutProgress = if (madeProgress) 0 else attemptsWithoutProgress + 1
            delay(RETRY_DELAY_MILLIS)
        }
    }

    throw lastError ?: IOException("Download failed")
}

/**
 * Deletes any partial files in the application's cache directory.
 */
fun deletePartialFiles(cacheDir: File) {
    cacheDir.list { _, name -> name.endsWith(DOT_PART_SUFFIX) }
        ?.forEach { File(cacheDir, it).delete() }
}

/**
 * Downloads a file with a temporary name into the cache directory, and when the download is
 * finished moves the file to the actual position, ensuring that the file is created/overwritten
 * only if it has been surely downloaded fully. Even has a progress callback, updated every
 * [CHUNK_SIZE] bytes.
 *
 * @param response the prepared network [Response]
 * @param file the file where to save the downloaded data, once download has fully finished
 * @param cacheDir the directory to place the temporary partial file, while download is in progress
 * @param progressCallback called every [CHUNK_SIZE] bytes with the current and total bytes, or
 * `0, 0` if there is no [CONTENT_LENGTH] header in the response
 */
@Throws(IOException::class)
suspend fun downloadBinaryFileWithPartial(
    response: Response,
    file: File,
    cacheDir: File,
    progressCallback: (currentBytes: Long, totalBytes: Long) -> Unit,
) {
    // use a partial file so that the file won't be considered as already downloaded
    // if the download gets interrupted
    val partialFile = withContext(Dispatchers.IO) {
        File.createTempFile(file.name, DOT_PART_SUFFIX, cacheDir)
    }
    partialFile.outputStream().use {
        downloadBinaryFile(response, it, progressCallback)
    }

    // delete the previous file, if any
    withContext(Dispatchers.IO) {
        file.delete()
    }

    // the file has been fully downloaded, so we can rename it
    if (withContext(Dispatchers.IO) { !partialFile.renameTo(file) }) {
        throw IOException("Cannot rename partial file $partialFile to actual file $file")
    }
}

/**
 * @param response the prepared network [Response]
 * @param outputStream where to put downloaded data
 * @param progressCallback called every [CHUNK_SIZE] bytes with the current and total bytes, or
 * `0, 0` if there is no [CONTENT_LENGTH] header in the response
 */
@Throws(IOException::class)
suspend fun downloadBinaryFile(
    response: Response,
    outputStream: OutputStream,
    progressCallback: (currentBytes: Long, totalBytes: Long) -> Unit,
) {
    val responseBody = response.body ?: throw IOException("Response doesn't contain a file")
    val totalBytes = response.header(CONTENT_LENGTH)?.toLong() ?: 0

    progressCallback(0, totalBytes)
    BufferedInputStream(responseBody.byteStream()).use { input ->
        val dataBuffer =
            ByteArray(CHUNK_SIZE)
        var readBytes: Int
        var currentBytes: Long = 0
        while (input.read(dataBuffer).also { readBytes = it } != -1) {
            yield() // manually yield because the input/output streams are blocking
            currentBytes += readBytes.toLong()
            outputStream.write(dataBuffer, 0, readBytes)
            progressCallback(currentBytes, totalBytes)
        }
    }
}

fun OkHttpClient.getResponse(url: String): Response {
    val request: Request = Request.Builder().url(url).build()
    return this.newCall(request).execute()
}
