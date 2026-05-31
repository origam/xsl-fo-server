package com.origam.xslfo;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public final class Application {
    private static final Logger LOGGER = Logger.getLogger(Application.class.getName());

    private Application() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        AppConfig config = AppConfig.fromEnv();
        XslFoRenderer renderer = XslFoRenderer.create(config);
        if (config.warmupEnabled()) {
            renderer.warmUp();
        }

        AtomicBoolean ready = new AtomicBoolean(true);
        ExecutorService httpExecutor = Executors.newFixedThreadPool(
                config.workerThreads(),
                namedThreadFactory("xslfo-http"));
        ExecutorService renderExecutor = Executors.newFixedThreadPool(
                config.workerThreads(),
                namedThreadFactory("xslfo-render"));

        HttpServer server = HttpServer.create(
                new InetSocketAddress(config.host(), config.port()),
                0);
        server.createContext("/health", exchange ->
                sendJson(exchange, 200, "{\"status\":\"ok\"}\n"));
        server.createContext("/ready", exchange -> {
            boolean isReady = ready.get();
            sendJson(exchange, isReady ? 200 : 503, "{\"ready\":" + isReady + "}\n");
        });
        server.createContext("/render", new RenderHandler(renderer, renderExecutor, config));
        server.setExecutor(httpExecutor);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ready.set(false);
            server.stop(3);
            httpExecutor.shutdownNow();
            renderExecutor.shutdownNow();
        }, "xslfo-shutdown"));

        server.start();
        LOGGER.info(() -> "ORIGAM XSL-FO server listening on "
                + config.host() + ":" + config.port());
    }

    private static ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger nextId = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + nextId.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
    }

    private static void sendJson(
            com.sun.net.httpserver.HttpExchange exchange,
            int status,
            String json) throws IOException {
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(response);
        }
    }
}
