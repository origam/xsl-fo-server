/*
Copyright 2005 - 2026 Advantage Solutions, s. r. o.

This file is part of ORIGAM (http://www.origam.org).

ORIGAM is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

ORIGAM is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with ORIGAM. If not, see <http://www.gnu.org/licenses/>.
*/

package com.origam.xslfo;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

final class RenderHandler implements HttpHandler {
    private static final Logger LOGGER = Logger.getLogger(RenderHandler.class.getName());

    private final XslFoRenderer renderer;
    private final ExecutorService renderExecutor;
    private final AppConfig config;

    RenderHandler(XslFoRenderer renderer, ExecutorService renderExecutor, AppConfig config) {
        this.renderer = renderer;
        this.renderExecutor = renderExecutor;
        this.config = config;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        exchange.getResponseHeaders().set("X-Request-Id", requestId);

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            sendError(exchange, 405, "method_not_allowed", "Use POST /render with XSL-FO XML.");
            return;
        }

        byte[] xslFo;
        try (InputStream body = exchange.getRequestBody()) {
            xslFo = readLimited(body, config.maxRequestBytes());
        } catch (PayloadTooLargeException exception) {
            sendError(exchange, 413, "payload_too_large", exception.getMessage());
            return;
        }

        if (xslFo.length == 0) {
            sendError(exchange, 400, "empty_body", "The request body must contain XSL-FO XML.");
            return;
        }

        Future<RenderResult> future = renderExecutor.submit(() -> renderer.render(xslFo));
        try {
            RenderResult result = future.get(
                    config.renderTimeout().toMillis(),
                    TimeUnit.MILLISECONDS);
            sendPdf(exchange, result);
        } catch (TimeoutException exception) {
            future.cancel(true);
            sendError(exchange, 504, "render_timeout",
                    "Rendering exceeded " + config.renderTimeout().toSeconds() + " seconds.");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            sendError(exchange, 503, "interrupted", "Rendering was interrupted.");
        } catch (ExecutionException exception) {
            handleRenderFailure(exchange, requestId, exception.getCause());
        }
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (total + read > maxBytes) {
                throw new PayloadTooLargeException(
                        "Request body is larger than the configured "
                                + maxBytes + " byte limit.");
            }
            output.write(buffer, 0, read);
            total += read;
        }
        return output.toByteArray();
    }

    private void handleRenderFailure(HttpExchange exchange, String requestId, Throwable cause)
            throws IOException {
        if (cause instanceof RenderException) {
            sendError(exchange, 400, "render_failed", cause.getMessage());
            return;
        }
        LOGGER.log(Level.SEVERE, "Unexpected render failure for request " + requestId, cause);
        sendError(exchange, 500, "internal_error", "Unexpected render failure.");
    }

    private static void sendPdf(HttpExchange exchange, RenderResult result) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/pdf");
        headers.set("Content-Disposition", "inline; filename=\"report.pdf\"");
        headers.set("X-Render-Pages", Integer.toString(result.pageCount()));
        byte[] pdf = result.pdf();
        exchange.sendResponseHeaders(200, pdf.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(pdf);
        }
    }

    private static void sendError(
            HttpExchange exchange,
            int status,
            String code,
            String message) throws IOException {
        String json = "{\"error\":\"" + escapeJson(code)
                + "\",\"message\":\"" + escapeJson(message) + "\"}\n";
        byte[] response = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream body = exchange.getResponseBody()) {
            body.write(response);
        }
    }

    private static String escapeJson(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (character < 0x20) {
                        builder.append(String.format("\\u%04x", (int) character));
                    } else {
                        builder.append(character);
                    }
                }
            }
        }
        return builder.toString();
    }
}
