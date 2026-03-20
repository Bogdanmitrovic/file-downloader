import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.io.path.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileDownloaderTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    private fun setupDispatcher(content: String) {
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
    }

    @Test
    fun `downloads file correctly`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 4)
        downloader.download("file.txt")
        assertEquals(content, Path("file.txt").readText())
    }

    @Test
    fun `download works with chunkCount 1`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 1)
        downloader.download("file.txt")
        assertEquals(content, Path("file.txt").readText())
    }

    @Test
    fun `download works with chunkCount bigger than size`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = content.length + 1)
        downloader.download("file.txt")
        assertEquals(content, Path("file.txt").readText())
    }

    @Test
    fun `download works with a existing file and overwrites`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'))
        downloader.download("file.txt")
        downloader.download("file.txt")
        assertEquals(content, Path("file.txt").readText())
    }

    @Test
    fun `download fails for a non-existing file`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse.Builder().code(404).build()
            }
        }
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'))
        val exception = assertFailsWith<Exception> {
            downloader.download("file.txt")
        }
        assertContains(exception.message.toString(), "404")
    }

    @Test
    fun `throws exception when Accept-Ranges header is missing`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse.Builder().code(200).build()
            }
        }
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 1)
        val exception = assertFailsWith<Exception> {
            downloader.download("file.txt")
        }
        assertContains(exception.message.toString(), "Accept-Ranges")
    }

    @Test
    fun `throws exception when file is empty`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return MockResponse.Builder().code(200).addHeader("Accept-Ranges", "bytes").build()
            }
        }
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 1)
        val exception = assertFailsWith<Exception> {
            downloader.download("file.txt")
        }
        assertContains(exception.message.toString(), "empty")
    }
}