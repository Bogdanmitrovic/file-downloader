import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class FileDownloaderTest {
    @Test
    fun `downloads file correctly`() {
        val server = MockWebServer()
        val content = "hello world"

        server.enqueue(MockResponse(body = content))
        server.start()

        val downloader = FileDownloader(server.url("").toString().trimEnd('/'))
        downloader.download("file.txt")

        assertEquals(content, Path("file.txt").readText())

        server.close()
    }
}