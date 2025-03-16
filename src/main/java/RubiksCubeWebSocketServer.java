import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * WebSocket server for Rubik's Cube Platform
 */
public class RubiksCubeWebSocketServer extends WebSocketServer {

    private final Map<String, ClientConnection> clients = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 8080;

        // Allow port override from command line
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 8080.");
            }
        }

        RubiksCubeWebSocketServer server = new RubiksCubeWebSocketServer(port);
        server.start();

        System.out.println("WebSocket server started on port: " + port);
        System.out.println("Connect with: ws://localhost:" + port);
    }

    public RubiksCubeWebSocketServer(int port) {
        super(new InetSocketAddress(port));
    }

    @Override
    public void onStart() {
        System.out.println("Server started successfully");
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientId = generateClientId();
        clients.put(clientId, new ClientConnection(conn));

        System.out.println("Client connected: " + clientId + " from " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientId = getClientIdByConnection(conn);
        if (clientId != null) {
            System.out.println("Client disconnected: " + clientId + " with exit code: " + code + " Additional info: " + reason);
            clients.remove(clientId);
        }
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        String clientId = getClientIdByConnection(conn);
        if (clientId == null) {
            System.err.println("Received message from unregistered client");
            return;
        }

        try {
            JSONObject jsonMessage = new JSONObject(message);
            String messageType = jsonMessage.getString("type");

            System.out.println("Received message of type: " + messageType + " from client: " + clientId);

            handleClientMessage(clientId, conn, jsonMessage);

        } catch (JSONException e) {
            System.err.println("Error parsing client message: " + e.getMessage());
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        String clientId = getClientIdByConnection(conn);
        if (clientId != null) {
            System.err.println("Error from client " + clientId + ": " + ex.getMessage());
        } else {
            System.err.println("Server error: " + ex.getMessage());
        }
        ex.printStackTrace();
    }

    @Override
    public void onMessage(WebSocket conn, ByteBuffer message) {
        // We only expect text messages, so convert binary to string
        onMessage(conn, new String(message.array()));
    }

    private void handleClientMessage(String clientId, WebSocket conn, JSONObject message) {
        try {
            String messageType = message.getString("type");
            ClientConnection client = clients.get(clientId);

            switch (messageType) {
                case "handshake":
                    handleHandshake(conn, message);
                    break;

                case "game_state":
                    handleGameState(clientId, message);
                    break;

                case "execute_code_request":
                    handleCodeExecution(conn, message);
                    break;

                case "console_output":
                    handleConsoleOutput(clientId, message);
                    break;

                case "stop_execution":
                    handleStopExecution(clientId, conn, message);
                    break;

                default:
                    System.out.println("Unknown message type from client " + clientId + ": " + messageType);
            }
        } catch (JSONException e) {
            System.err.println("Error handling client message: " + e.getMessage());
        }
    }

    private void handleHandshake(WebSocket conn, JSONObject message) throws JSONException {
        // Prepare handshake response
        JSONObject response = new JSONObject();
        response.put("type", "handshake_response");

        JSONObject serverInfo = new JSONObject();
        serverInfo.put("version", "1.0.0");

        JSONArray supportedLanguages = new JSONArray();
        supportedLanguages.put("javascript");
        supportedLanguages.put("python");
        supportedLanguages.put("java");
        supportedLanguages.put("csharp");

        serverInfo.put("supportedLanguages", supportedLanguages);
        response.put("serverInfo", serverInfo);

        conn.send(response.toString());
    }

    private void handleGameState(String clientId, JSONObject message) throws JSONException {
        // Store the game state
        ClientConnection client = clients.get(clientId);

        if (message.has("state")) {
            client.setGameState(message.get("state"));

            // Broadcast to other clients
            broadcastGameState(clientId, client.getGameState());
        }
    }

    private void handleCodeExecution(WebSocket conn, JSONObject message) throws JSONException {
        String requestId = message.getString("requestId");
        String code = message.getString("code");
        String language = message.getString("language");

        System.out.println("Executing " + language + " code for request " + requestId);
        System.out.println("Code content: " + code.substring(0, Math.min(100, code.length())) + "...");

        // Create a response object
        JSONObject response = new JSONObject();
        response.put("type", "execution_result");
        response.put("requestId", requestId);

        JSONArray output = new JSONArray();
        JSONArray commands = new JSONArray();

        // Log start of execution
        JSONObject log1 = new JSONObject();
        log1.put("type", "log");
        log1.put("message", "Executing " + language + " code");
        output.put(log1);

        try {
            // Parse and extract commands based on the language
            List<String> extractedCommands = parseCodeByLanguage(code, language);
            System.out.println("Extracted " + extractedCommands.size() + " commands from " + language + " code");

            // If we extracted commands successfully, add them to the response
            for (String cmd : extractedCommands) {
                JSONObject command = new JSONObject();

                // Parse the command to determine the face and direction
                parseCommand(cmd, command);

                if (command.has("action")) {
                    commands.put(command);
                    System.out.println("Added command: " + command.toString());
                }
            }

            // Add log of successful execution
            JSONObject logSuccess = new JSONObject();
            logSuccess.put("type", "log");
            logSuccess.put("message", "Successfully parsed " + extractedCommands.size() + " cube commands");
            output.put(logSuccess);

            response.put("status", "completed");

        } catch (Exception e) {
            // Log error if parsing fails
            System.err.println("Error parsing code: " + e.getMessage());
            e.printStackTrace();

            JSONObject logError = new JSONObject();
            logError.put("type", "error");
            logError.put("message", "Error executing code: " + e.getMessage());
            output.put(logError);

            response.put("status", "error");
        }

        // Create a result object to match the expected client format
        JSONObject result = new JSONObject();
        result.put("commands", commands);
        result.put("output", output);
        result.put("status", response.getString("status"));

        // Put the result inside the response
        response.put("result", result);

        System.out.println("Sending response: " + response.toString());

        // Send the response immediately (no delay)
        conn.send(response.toString());
    }

    private List<String> parseCodeByLanguage(String code, String language) {
        List<String> commands = new ArrayList<>();

        System.out.println("Parsing " + language + " code with length: " + code.length());

        switch (language.toLowerCase()) {
            case "python":
                commands = parsePythonCode(code);
                break;
            case "java":
                commands = parseJavaCode(code);
                break;
            case "csharp":
                commands = parseCSharpCode(code);
                break;
            default:
                System.out.println("Unsupported language: " + language);
                break;
        }

        return commands;
    }

    private List<String> parsePythonCode(String code) {
        List<String> commands = new ArrayList<>();

        System.out.println("Parsing Python code:");

        // Extract cube method calls with flexible whitespace
        Pattern pattern = Pattern.compile("cube\\s*\\.\\s*(R|RPrime|R_prime|U|UPrime|U_prime|F|FPrime|F_prime|B|BPrime|B_prime|L|LPrime|L_prime|D|DPrime|D_prime)\\s*\\(\\s*\\)");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String command = matcher.group(1);
            // Convert Python snake_case to camelCase
            command = command.replace("_prime", "Prime");
            commands.add(command);
            System.out.println("Found Python command: " + command);
        }

        return commands;
    }

    private List<String> parseJavaCode(String code) {
        List<String> commands = new ArrayList<>();

        System.out.println("Parsing Java code:");

        // Improved pattern with flexible whitespace
        Pattern pattern = Pattern.compile("cube\\s*\\.\\s*(R|RPrime|U|UPrime|F|FPrime|B|BPrime|L|LPrime|D|DPrime)\\s*\\(\\s*\\)");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String command = matcher.group(1);
            commands.add(command);
            System.out.println("Found Java command: " + command);
        }

        return commands;
    }

    private List<String> parseCSharpCode(String code) {
        List<String> commands = new ArrayList<>();

        System.out.println("Parsing C# code:");

        // Improved pattern with flexible whitespace
        Pattern pattern = Pattern.compile("Cube\\s*\\.\\s*(R|RPrime|U|UPrime|F|FPrime|B|BPrime|L|LPrime|D|DPrime)\\s*\\(\\s*\\)");
        Matcher matcher = pattern.matcher(code);

        while (matcher.find()) {
            String command = matcher.group(1);
            commands.add(command);
            System.out.println("Found C# command: " + command);
        }

        return commands;
    }

    private void parseCommand(String cmd, JSONObject commandObj) {
        // Set the action type
        commandObj.put("action", "rotate");

        // Determine the face and direction
        if (cmd.endsWith("Prime")) {
            // Extract the face letter (R, U, F, B, L, D)
            String face = cmd.substring(0, cmd.length() - 5);
            commandObj.put("face", face);
            commandObj.put("direction", "counterclockwise");
            System.out.println("Parsed command: Face=" + face + ", Direction=counterclockwise");
        } else {
            // It's just the face letter
            commandObj.put("face", cmd);
            commandObj.put("direction", "clockwise");
            System.out.println("Parsed command: Face=" + cmd + ", Direction=clockwise");
        }
    }

    private void handleConsoleOutput(String clientId, JSONObject message) throws JSONException {
        if (message.has("output")) {
            System.out.println("Client " + clientId + " console: " + message.get("output"));
        }
    }

    private void handleStopExecution(String clientId, WebSocket conn, JSONObject message) throws JSONException {
        System.out.println("Client " + clientId + " stopped execution");

        // Create a response to confirm the stop
        JSONObject response = new JSONObject();
        response.put("type", "execution_stopped");

        // Include the original requestId if it exists
        if (message.has("requestId")) {
            response.put("requestId", message.getString("requestId"));
        }

        response.put("status", "success");
        response.put("timestamp", System.currentTimeMillis());

        // Send the confirmation back to the client
        conn.send(response.toString());

        System.out.println("Sent execution_stopped confirmation to client " + clientId);
    }

    private void broadcastGameState(String sourceClientId, Object gameState) {
        clients.forEach((clientId, client) -> {
            if (!clientId.equals(sourceClientId)) {
                try {
                    JSONObject message = new JSONObject();
                    message.put("type", "game_state_update");
                    message.put("sourceClientId", sourceClientId);
                    message.put("state", gameState);

                    client.getConnection().send(message.toString());
                } catch (JSONException e) {
                    System.err.println("Error broadcasting game state: " + e.getMessage());
                }
            }
        });
    }

    private String getClientIdByConnection(WebSocket conn) {
        for (Map.Entry<String, ClientConnection> entry : clients.entrySet()) {
            if (entry.getValue().getConnection() == conn) {
                return entry.getKey();
            }
        }
        return null;
    }

    private String generateClientId() {
        return "client_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Class to hold information about a client connection
     */
    private static class ClientConnection {
        private final WebSocket connection;
        private Object gameState;

        public ClientConnection(WebSocket connection) {
            this.connection = connection;
        }

        public WebSocket getConnection() {
            return connection;
        }

        public Object getGameState() {
            return gameState;
        }

        public void setGameState(Object gameState) {
            this.gameState = gameState;
        }
    }
}