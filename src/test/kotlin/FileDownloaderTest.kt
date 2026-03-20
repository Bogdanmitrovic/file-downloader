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
    private lateinit var tempPath: String

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        val file = kotlin.io.path.createTempFile().toFile()
        tempPath = file.absolutePath
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
        downloader.download("file.txt", tempPath)
        assertEquals(content, Path(tempPath).readText())
    }

    @Test
    fun `download works with chunkCount 1`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 1)
        downloader.download("file.txt", tempPath)
        assertEquals(content, Path(tempPath).readText())
    }

    @Test
    fun `download works with chunkCount bigger than size`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = content.length + 1)
        downloader.download("file.txt", tempPath)
        assertEquals(content, Path(tempPath).readText())
    }

    @Test
    fun `download works with a existing file and overwrites`() = runBlocking {
        val content = "hello world"
        setupDispatcher(content)
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'))
        downloader.download("file.txt", tempPath)
        downloader.download("file.txt", tempPath)
        assertEquals(content, Path(tempPath).readText())
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
            downloader.download("file.txt", tempPath)
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
            downloader.download("file.txt", tempPath)
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
            downloader.download("file.txt", tempPath)
        }
        assertContains(exception.message.toString(), "empty")
    }

    @Test
    fun `retries on failed chunk`() = runBlocking {
        val content = "hello world"
        var attempts = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.method) {
                    "HEAD" -> MockResponse.Builder()
                        .addHeader("Content-Length", content.length)
                        .addHeader("Accept-Ranges", "bytes")
                        .code(200)
                        .build()

                    "GET" -> {
                        attempts++
                        if (attempts < 3) MockResponse.Builder().code(500).build()
                        else {
                            val range = request.headers["Range"]!!
                            val start = range.substringAfter("bytes=").substringBefore("-").toInt()
                            val end = range.substringAfter("-").toInt() + 1
                            MockResponse.Builder()
                                .body(content.substring(start, end))
                                .code(206)
                                .build()
                        }
                    }

                    else -> MockResponse(code = 400)
                }
            }
        }
        val downloader = FileDownloader(server.url("").toString().trimEnd('/'), chunkCount = 1, retryCount = 3)
        downloader.download("file.txt", tempPath)
        assertEquals(content, Path(tempPath).readText())
    }
}