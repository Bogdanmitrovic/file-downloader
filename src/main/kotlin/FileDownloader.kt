import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.file.Path
import kotlin.io.path.outputStream

class FileDownloader(private val url: String) {

    private val client = OkHttpClient()

    fun download(outputPath: Path) {
        val request = Request.Builder()
            .url(url)
            .build()

        //client.newCall(request).execute().use { response ->
        //    if (!response.isSuccessful) throw okio.IOException("Unexpected code $response")
//
        //    for ((name, value) in response.headers) {
        //        println("$name: $value")
        //    }
//
        //    println(response.body.byteStream().use { input ->
        //
        //    }
        //}
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw okio.IOException("Unexpected response code: ${response.code}")

            response.body.byteStream().use { input ->
                outputPath.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}