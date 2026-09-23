package org.example;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;

public class Server {

    private static final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> expiry = new ConcurrentHashMap<>();
    private static List<String> followerPorts = new ArrayList<>();
    private static String role;


    public static void main (String[] args) throws IOException {
        int port;

        if (args.length > 1) {
            role = args[1];
        } else {
            role = "LONE";
        }

        for (int i = 2; i < args.length; i++) {
             followerPorts.add(args[i]);
        }


        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        } else {
            port = 6379;
        }

        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("Server listening on port: " + port);

        // Background thread: cleans expired keys once every second
        Thread.ofVirtual().start(() -> {
            while (true) {
                List<String> expiredKeys = new ArrayList<>();

                for (String key : expiry.keySet()) {
                    if (expiry.get(key) < System.currentTimeMillis()) {
                        expiredKeys.add(key);
                    }
                }

                for (String key : expiredKeys) {
                    store.remove(key);
                    expiry.remove(key);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {

                }
            }
        });

        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());

            Thread.ofVirtual().start(() -> {
                    try {
                        handleClient(clientSocket);
                    } catch (IOException e) {
                        System.out.println("Error handling client: " + e.getMessage());
                    }
            });
        }
    }

    private static void forwardToFollowers(String line) {
        for (String followerPort : followerPorts) {
            try {
                Socket socket = new Socket("localhost", Integer.parseInt(followerPort));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                out.println(line);
                socket.close();
            } catch (IOException e) {
                System.out.println("Failed to forward to follower " + followerPort + ": " + e.getMessage());
            }

        }
    }

    private static void handleClient(Socket clientSocket) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

        String line;
        while ((line = in.readLine()) != null) {
            System.out.println("Received: " + line);
            String response = handleCommand(line);
            out.println(response);
        }
        System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        clientSocket.close();
    }

    public static String handleCommand(String line) {
        String[] parts = line.split("\\s+");
        String command = parts[0].toUpperCase();

        if (parts[0].isEmpty()) {
            return "This is an empty command";
        }

        if (command.equals("SET") && parts.length >= 3) {
            store.put(parts[1], parts[2]);

            if (role.equalsIgnoreCase("LEADER")){
                forwardToFollowers(line);
            }

            return "OK";

        } else if (command.equals("GET") && parts.length >= 2) {
            String key = parts[1];

            // Passive expiration: check on read since key may have expired since last GET
            if (expiry.containsKey(key) && expiry.get(key) < System.currentTimeMillis()) {
                store.remove(key);
                expiry.remove(key);
                return "NULL";

            }

            String value = store.get(key);
            if (value == null) {
                return "NULL";
            } else {
                return value;
            }

        } else if (command.equals("DEL") && parts.length >= 2) {
            store.remove(parts[1]);

            if (role.equalsIgnoreCase("LEADER")){
                forwardToFollowers(line);
            }

            return "OK";

        } else if (command.equals("EXPIRE") && parts.length >= 3){
            String key = parts[1];

            if (!store.containsKey(key)) {
                return "ERROR: key does not exist";
            }

            int timer = Integer.parseInt(parts[2]);
            long expiryTime = System.currentTimeMillis() + (timer * 1000);

            expiry.put(key, expiryTime);

            if (role.equalsIgnoreCase("LEADER")){
                forwardToFollowers(line);
            }

            return "OK";

        } else {
            return "ERROR: unknown command";
        }





    }
}
