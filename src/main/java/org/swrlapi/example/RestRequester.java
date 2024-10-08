package org.swrlapi.example;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static org.swrlapi.example.JsonRequester.debugLog;

public class RestRequester {
    public static void main(String[] args) throws IOException {
        JsonRequester requester = new JsonRequester();
        int serverPort = 8000;
        HttpServer server = HttpServer.create(new InetSocketAddress(serverPort), 0);

        server.createContext("/api/check", (exchange -> {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = exchange.getRequestBody().read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            String request = result.toString("UTF-8");
            String response = "";
            try {
                 response = requester.response(request);
            } catch (Throwable e){
                debugLog("Got exception below. Returning empty string");
                e.printStackTrace();
            }
            Headers headers = new Headers();
            headers.set("Content-Type", "application/json");
            exchange.getResponseHeaders().putAll(headers);
            exchange.sendResponseHeaders(200, 0);;

            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.flush();
            exchange.close();
        }));

        server.setExecutor(null); // creates a default executor
        server.start();
        System.out.println("Started");
    }
}
