package org.example;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/* Simple load test
*      1. Opens many concurrent connections to the server
*      2. Fills it with SET/GET commands, then reports total throughput.
*
*  Usage:  java -cp target\classes org.example.LoadTest <port> <numClients> <commandsPerClient>
*  Example: java -cp target\classes org.example.LoadTest 6379 100 50java -cp target\classes org.example.LoadTest 6379 100 50
*          -> 100 concurrent clients, each sending 50 commands = 5000 total commands
*/
public class LoadTest {
    public static void main (String[] args) throws InterruptedException {

        int port = args.length > 0 ? Integer.parseInt(args[0]) : 6379;
        int numClients = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        int commandsPerClient = args.length > 2 ? Integer.parseInt(args[2]) : 50;

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        System.out.println("Starting load test: " + numClients + " concurrent clients, "
                + commandsPerClient + " commands each (" + (numClients * commandsPerClient) + " total)");

        long startTime = System.currentTimeMillis();

        Thread[] clientThreads = new Thread[numClients];

        for (int i = 0; i < numClients; i++) {
            final int clientId = i;
            clientThreads[i] = Thread.ofVirtual().unstarted(() -> {
                try {
                    Socket socket = new Socket("localhost", port);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

                    for (int j = 0; j < commandsPerClient; j++) {
                        String key = "key" + clientId + "_" + j;
                        out.println("SET " + key + " value" + j);
                        String response = in.readLine();

                        if ("OK".equals(response)) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    }

                    socket.close();
                } catch (IOException e) {
                    failureCount.addAndGet(commandsPerClient);
                }
            });
        }

        for (Thread t : clientThreads) t.start();
        for (Thread t : clientThreads) t.join();

        long endTime = System.currentTimeMillis();
        long durationMs = endTime - startTime;
        double durationSec = durationMs / 1000.0;
        int totalCommands = successCount.get() + failureCount.get();
        double opsPerSecond = totalCommands / durationSec;

        System.out.println();
        System.out.println("=== Results ===");
        System.out.println("Total commands sent: " + totalCommands);
        System.out.println("Successful: " + successCount.get());
        System.out.println("Failed: " + failureCount.get());
        System.out.println("Total time: " + durationMs + " ms");
        System.out.printf("Throughput: %.1f ops/sec%n", opsPerSecond);
    }
}
