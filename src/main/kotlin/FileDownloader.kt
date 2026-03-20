import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.coroutines.executeAsync
import kotlin.io.path.Path
import kotlin.io.path.writeBytes

class FileDownloader(private val url: String, private val chunkCount: Int = 4) {

    private val client = OkHttpClient()

    private suspend fun getFileSize(filePath: String): Long {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .head()
            .build()
        client.newCall(request).executeAsync().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected response code, ${response.code}")
            return response.headers["Content-Length"]?.toLong()
                ?: throw Exception("Content-Length header is missing")
        }
    }

    fun calculateChunks(fileSize: Long): List<LongRange> {
        val chunkSize = fileSize / chunkCount
        val chunks = mutableListOf<LongRange>()
        for (i in 0..<chunkCount) {
            if (i == chunkCount - 1)
                chunks.add(LongRange(i * chunkSize, fileSize - 1))
            else
                chunks.add(LongRange(i * chunkSize, (i + 1) * chunkSize - 1))
        }
        return chunks
    }

    private suspend fun downloadChunk(filePath: String, range: LongRange): ByteArray {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .addHeader("Range", "bytes=${range.first}-${range.last}")
            .get()
            .build()
        client.newCall(request).executeAsync().use { response ->
            if (response.code != 206) throw Exception("Expected 206 Partial Content, received ${response.code}")
            return withContext(Dispatchers.IO) {
                response.body.bytes()
            }
        }
    }

    suspend fun download(filePath: String) {
        coroutineScope {
            println("${System.currentTimeMillis()} Starting $filePath")
            val size = getFileSize(filePath)
            val chunkRanges = calculateChunks(size)
            val chunks = chunkRanges.map { range ->
                async { downloadChunk(filePath, range) }
            }.awaitAll()
            Path(filePath).writeBytes(chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes })
            println("${System.currentTimeMillis()} Finishing $filePath")
        }
    }

    fun shutdown() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}