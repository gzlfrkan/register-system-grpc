package com.example.family;

import java.io.*;
import java.net.*;

/**
 * TCP Server - Lider üye için.
 * Port 6666'da dinler, SET/GET komutlarını işler.
 */
public class TcpServer {

    private final int port;
    private final NodeMain node;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public TcpServer(int port, NodeMain node) {
        this.port = port;
        this.node = node;
    }

    public void start() {
        running = true;
        new Thread(this::run, "TCP-Server").start();
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            // ignore
        }
    }

    private void run() {
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("TCP Server dinleniyor: 127.0.0.1:" + port);

            while (running) {
                try {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client), "TCP-Client").start();
                } catch (IOException e) {
                    if (running) {
                        System.err.println("TCP accept hatasi: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("TCP Server baslatilamadi: " + e.getMessage());
        }
    }

    private void handleClient(Socket client) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                PrintWriter out = new PrintWriter(client.getOutputStream(), true)) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = processCommand(line);
                out.println(response);
            }
        } catch (IOException e) {
            // Client disconnected
        } finally {
            try {
                client.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private String processCommand(String line) {
        CommandParser.Command cmd = CommandParser.parse(line);

        switch (cmd.type) {
            case SET:
                return node.handleSet(cmd.id, cmd.message);
            case GET:
                return node.handleGet(cmd.id);
            case STATS:
                return node.handleStats();
            default:
                return "ERROR: Gecersiz komut. Kullanim: SET <id> <mesaj> | GET <id> | STATS";
        }
    }
}
