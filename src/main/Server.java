import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private List<String> validPaths;
    private final int PORT;
    private final int NUMBER_OF_THREADS;

    public Server(List<String> validPaths, int PORT, int NUMBER_OF_THREADS) {
        this.validPaths = validPaths;
        this.PORT = PORT;
        this.NUMBER_OF_THREADS = NUMBER_OF_THREADS;
    }

    public void start() {
        try (final var serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started");

            ExecutorService service = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

            while (true) {
                var socket = serverSocket.accept();
                service.execute(() -> handle(socket));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handle(Socket socket) {

        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream())
        ) {
            final var requestLine = in.readLine();

            System.out.println(Thread.currentThread().getName() + " received a request: " + requestLine);
            if (requestLine == null) {
                System.out.println("Bad request");
                return;
            }

            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                return;
            }

            final var path = parts[1];
            if (!validPaths.contains(path)) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);

            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
                return;
            }

            final var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
