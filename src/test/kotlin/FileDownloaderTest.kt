import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class FileDownloaderTest {
    @Test
    fun `downloads file correctly`() = runBlocking{
        val server = MockWebServer()
        val content = "hello world"
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.method) {
                    "HEAD" -> MockResponse.Builder()
                        .addHeader("Content-Length", content.length)
                        .addHeader("Accept-Ranges", "bytes")
                        .code(200)
                        .build()

                    "GET" -> {
                        val range = request.headers["Range"] ?: return MockResponse(code = 400)
                        val start = range.substringAfter("bytes=").substringBefore("-").toInt()
                        val end = range.substringAfter("-").toInt() + 1
                        MockResponse.Builder()
                            .body(content.substring(start, end))
                            .code(206)
                            .build()
                    }

                    else -> MockResponse(code = 400)
                }
            }
        }
        server.start()

        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 4)
        downloader.download("file.txt")

        assertEquals(content, Path("file.txt").readText())

        server.close()
    }
}