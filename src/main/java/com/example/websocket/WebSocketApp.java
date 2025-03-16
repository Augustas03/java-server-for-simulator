package com.example.websocket;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.glassfish.tyrus.server.Server;

public class WebSocketApp {

    public static void main(String[] args) {
        // Create the server
        Server server = new Server("localhost", 8025, "/websocket", null, CounterWebSocket.class);

        try {
            // Start the server
            server.start();
            System.out.println("WebSocket server started on ws://localhost:8025/websocket/counter");
            System.out.println("Press enter to stop the server...");

            // Keep the server running until the user presses enter
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();
            scanner.close();
        } catch (Exception e) {
            System.err.println("Error starting the WebSocket server: " + e.getMessage());
            e.printStackTrace();
        } finally {
            server.stop();
            System.out.println("WebSocket server stopped");
        }
    }
}