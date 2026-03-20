import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import java.io.RandomAccessFile

class FileDownloader(private val url: String, private val chunkCount: Int = 4, private val retryCount: Int = 2) {

    private val client = OkHttpClient()

    private suspend fun <T> retry(times: Int, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(times) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException!!
    }

    private suspend fun getFileSize(filePath: String): Long {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .head()
            .build()
        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected response code, ${response.code}")
            if (response.header("Accept-Ranges") != "bytes") {
                throw Exception("Accept-Ranges header is missing")
            }
            val size =
                response.headers["Content-Length"]?.toLong() ?: throw Exception("Content-Length header is missing")
            if (size == 0L) throw Exception("File is empty, Content-Length header is 0")
            return size
        }
    }

    fun calculateChunks(fileSize: Long): List<LongRange> {
        val actualChunkCount = minOf(chunkCount.toLong(), fileSize).toInt()
        val chunkSize = fileSize / actualChunkCount
        val chunks = mutableListOf<LongRange>()
        for (i in 0..<actualChunkCount) {
            if (i == actualChunkCount - 1)
                chunks.add(LongRange(i * chunkSize, fileSize - 1))
            else
                chunks.add(LongRange(i * chunkSize, (i + 1) * chunkSize - 1))
        }
        return chunks
    }

    private suspend fun downloadChunk(filePath: String, range: LongRange, file: RandomAccessFile) {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .addHeader("Range", "bytes=${range.first}-${range.last}")
            .get()
            .build()
        retry(times = retryCount) {
            client.newCall(request).executeAsync().use { response ->
                if (response.code != 206) throw Exception("Expected 206 Partial Content, received ${response.code}")
                response.body.byteStream().use { input ->
                    val channel = file.channel
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytesRead: Int
                    var position = range.first

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        val byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, bytesRead)
                        channel.write(byteBuffer, position)
                        position += bytesRead
                    }
                }
            }
        }
    }

    suspend fun download(filePath: String) {
        val size = getFileSize(filePath)
        val chunkRanges = calculateChunks(size)
        withContext(Dispatchers.IO) {
            RandomAccessFile(filePath, "rw").use { file ->
                file.setLength(size)
                coroutineScope {
                    chunkRanges.map { range ->
                        async { downloadChunk(filePath, range, file) }
                    }.awaitAll()
                }
            }
        }
    }

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}