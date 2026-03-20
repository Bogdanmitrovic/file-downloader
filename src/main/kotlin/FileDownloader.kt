import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.io.path.Path
import kotlin.io.path.writeBytes

class FileDownloader(private val url: String, private val chunkCount: Int = 4) {

    private val client = OkHttpClient()

    fun getFileSize(filePath: String): Long {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .head()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected response code ${response.code}")
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

    fun downloadChunk(filePath: String, range: LongRange): ByteArray {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .addHeader("Range", "bytes=${range.first}-${range.last}")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code != 206) throw Exception("Unexpected response code: ${response.code}")
            return response.body.bytes()
        }
    }

    fun download(filePath: String) {
        val size = getFileSize(filePath)
        val chunkRanges = calculateChunks(size)
        val chunks = mutableListOf<ByteArray>()
        for (i in chunkRanges.indices) {
            chunks.add(downloadChunk(filePath, chunkRanges[i]))
        }
        Path(filePath).writeBytes(chunks.fold(ByteArray(0)) { acc, bytes -> acc + bytes })
        // combine and write to path
        //response.body.byteStream().use { input ->
        //    Path(filePath).outputStream().use { output ->
        //        input.copyTo(output)
        //    }
        //}
    }
}