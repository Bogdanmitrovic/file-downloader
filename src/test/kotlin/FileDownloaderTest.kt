import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class FileDownloaderTest {
    @Test
    fun `downloads file correctly`() {
        val server = MockWebServer()
        val content = "hello world"
        val chunkCount = 4
        server.enqueue(
            MockResponse(
                headers = headersOf("Content-Length", "11"),
                code = 200
            )
        )
        val chunkSize = content.length / chunkCount
        for (i in 0..<chunkCount) {
            val start = i * chunkSize
            val end = if (i == chunkCount - 1) content.length else (i + 1) * chunkSize
            server.enqueue(MockResponse(body = content.substring(start, end), code = 206))
        }
        server.start()

        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 4)
        downloader.download("file.txt")

        assertEquals(content, Path("file.txt").readText())

        server.close()
    }
}