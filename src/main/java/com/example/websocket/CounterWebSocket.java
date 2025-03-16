package com.example.websocket;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/counter")
public class CounterWebSocket {

    private static int counter = 0;
    private static Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());

    @OnOpen
    public void onOpen(Session session) {
        System.out.println("WebSocket opened: " + session.getId());
        sessions.add(session);

        // Send the current counter value to the newly connected client
        try {
            session.getBasicRemote().sendText(String.valueOf(counter));
        } catch (IOException e) {
            System.err.println("Error sending initial counter value: " + e.getMessage());
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        System.out.println("Message received: " + message + " from " + session.getId());

        if ("increment".equals(message)) {
            counter++;
            System.out.println("Counter incremented to: " + counter);

            // Broadcast the new counter value to all connected clients
            broadcastCounter();
        }
    }

    @OnClose
    public void onClose(Session session) {
        System.out.println("WebSocket closed: " + session.getId());
        sessions.remove(session);
    }

    @OnError
    public void onError(Throwable error) {
        System.err.println("WebSocket error: " + error.getMessage());
    }

    private void broadcastCounter() {
        for (Session session : sessions) {
            try {
                session.getBasicRemote().sendText(String.valueOf(counter));
            } catch (IOException e) {
                System.err.println("Error broadcasting counter: " + e.getMessage());
            }
        }
    }
}