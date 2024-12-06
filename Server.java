import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 8818;
    private static final ConcurrentHashMap<String, PrintWriter> clientWriters = new ConcurrentHashMap<>();
    private static final ExecutorService executor = Executors.newCachedThreadPool();
    private static final String LOG_FILE_PATH = "chat_log.txt";

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Чат-сервер запущен на порту " + PORT);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новый клиент подключился: " + clientSocket.getInetAddress());
                executor.submit(new ClientHandler(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Ошибка при запуске сервера: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private PrintWriter writer;
        private String nickname;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        @Override
        public void run() {
            try {
                writer = new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                writer.println("Введите ваш ник:");
                nickname = reader.readLine();
                clientWriters.put(nickname, writer);
                System.out.println("Клиент (" + nickname + ") добавлен в список");
                logMessage(nickname + " присоединился к чату.");

                String message;
                while ((message = reader.readLine()) != null) {
                    handleCommand(message.trim());
                }
            } catch (SocketException e) {
                System.err.println("Клиент отключился: " + e.getMessage());
            } catch (IOException e) {
                System.err.println("Ошибка при обработке клиента: " + e.getMessage());
            } finally {
                disconnectClient();
            }
        }

        private void handleCommand(String message) {
            if (message.equalsIgnoreCase("/exit")) {
                disconnectClient();
            } else if (message.equalsIgnoreCase("/users")) {
                showConnectedUsers();
            } else {
                broadcastMessage(message);
            }
        }

        private void showConnectedUsers() {
            StringBuilder userList = new StringBuilder("Подключенные пользователи: ");
            for (String userNickname : clientWriters.keySet()) {
                userList.append(userNickname).append(", ");
            }
            if (userList.length() > 0) {
                userList.setLength(userList.length() - 2);
            }
            writer.println(userList.toString());
        }

        private void broadcastMessage(String message) {
            //проверка, начинается ли сообщение с команды "encrypt"
            if (message.startsWith("encrypt ")) {
                String textToEncrypt = message.substring(8);

                message = "Зашифрованное сообщение от " + nickname + ": " + encrypt(textToEncrypt);
            } else {
                message = nickname + ": " + message;
            }

            for (PrintWriter clientWriter : clientWriters.values()) {
                clientWriter.println(message);
                logMessage(message);
            }
        }

        private String encrypt(String text) {
            //шифр цезаря со сдвигом на 3
            StringBuilder encrypted = new StringBuilder();
            for (char c : text.toCharArray()) {
                encrypted.append((char) (c + 3)); //сдвиг символа на 3
            }
            return encrypted.toString();
        }

        private void disconnectClient() {
            if (nickname != null) {
                clientWriters.remove(nickname);
                logMessage(nickname + " покинул чат.");
            }
            System.out.println("Клиент (" + nickname + ") отключился: " + clientSocket.getInetAddress());
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии сокета клиента: " + e.getMessage());
            }
        }

        private void logMessage(String message) {
            try (BufferedWriter logWriter = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
                logWriter.write(message);
                logWriter.newLine();
            } catch (IOException e) {
                System.err.println("Ошибка логирования сообщения: " + e.getMessage());
            }
        }
    }
}
