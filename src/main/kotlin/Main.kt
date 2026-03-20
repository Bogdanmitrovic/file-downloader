val fileDownloader = FileDownloader("http://localhost:8080")
fun main() {
    print("File size: ${fileDownloader.getFileSize("1.jpg")} bytes\n")
    fileDownloader.download("1.jpg")
}
