import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val fileDownloader = FileDownloader("http://localhost:8080")
    coroutineScope {
        launch {
            println("${System.currentTimeMillis()} launch 1 started")
            fileDownloader.download("big.bin")
        }
        launch {
            println("${System.currentTimeMillis()} launch 2 started")
            fileDownloader.download("2.jpg")
        }
    }
    fileDownloader.shutdown()
}