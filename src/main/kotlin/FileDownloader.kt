import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.io.path.Path
import kotlin.io.path.outputStream

class FileDownloader(private val url: String) {

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

    fun download(filePath: String) {
        val path = "$url/$filePath"
        val request = Request.Builder()
            .url(path)
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("Unexpected response code: ${response.code}")
            response.body.byteStream().use { input ->
                Path(filePath).outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}