import kotlin.io.path.Path

val fileDownloader = FileDownloader("http://localhost:8080/1.jpg")
fun main() {
    fileDownloader.download(Path("1.jpg"))
}
