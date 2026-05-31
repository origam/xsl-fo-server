package com.origam.xslfo;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;

public record AppConfig(
        String host,
        int port,
        int workerThreads,
        int maxRequestBytes,
        Duration renderTimeout,
        URI baseUri,
        Path fopConfigFile,
        boolean warmupEnabled) {

    public AppConfig {
        if (isBlank(host)) {
            throw new IllegalArgumentException("host must not be blank.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535.");
        }
        if (workerThreads < 1) {
            throw new IllegalArgumentException("workerThreads must be at least 1.");
        }
        if (maxRequestBytes < 1) {
            throw new IllegalArgumentException("maxRequestBytes must be at least 1.");
        }
        if (renderTimeout == null || renderTimeout.isZero() || renderTimeout.isNegative()) {
            throw new IllegalArgumentException("renderTimeout must be positive.");
        }
        if (baseUri == null) {
            throw new IllegalArgumentException("baseUri must not be null.");
        }
    }

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_MAX_REQUEST_BYTES = 20 * 1024 * 1024;
    private static final int DEFAULT_RENDER_TIMEOUT_SECONDS = 60;

    public static AppConfig fromEnv() {
        String host = env("APP_HOST", "0.0.0.0");
        int port = portFromEnv();
        int workerThreads = intEnv(
                "WORKER_THREADS",
                Math.max(2, Runtime.getRuntime().availableProcessors()));
        int maxRequestBytes = intEnv("MAX_REQUEST_BYTES", DEFAULT_MAX_REQUEST_BYTES);
        Duration renderTimeout = Duration.ofSeconds(
                longEnv("RENDER_TIMEOUT_SECONDS", DEFAULT_RENDER_TIMEOUT_SECONDS));
        URI baseUri = baseUriFromEnv();
        Path fopConfigFile = pathEnv("FOP_CONFIG_FILE");
        boolean warmupEnabled = booleanEnv("WARMUP_ENABLED", true);

        return new AppConfig(
                host,
                port,
                workerThreads,
                maxRequestBytes,
                renderTimeout,
                baseUri,
                fopConfigFile,
                warmupEnabled);
    }

    private static int portFromEnv() {
        String appPort = System.getenv("APP_PORT");
        if (!isBlank(appPort)) {
            return parseInt("APP_PORT", appPort);
        }
        return intEnv("PORT", DEFAULT_PORT);
    }

    private static URI baseUriFromEnv() {
        String baseUri = System.getenv("FOP_BASE_URI");
        if (!isBlank(baseUri)) {
            return URI.create(baseUri);
        }
        String basePath = env("FOP_BASE_PATH", ".");
        return Path.of(basePath).toAbsolutePath().normalize().toUri();
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return isBlank(value) ? fallback : value;
    }

    private static int intEnv(String name, int fallback) {
        String value = System.getenv(name);
        return isBlank(value) ? fallback : parseInt(name, value);
    }

    private static long longEnv(String name, long fallback) {
        String value = System.getenv(name);
        if (isBlank(value)) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a whole number.", exception);
        }
    }

    private static int parseInt(String name, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(name + " must be a whole number.", exception);
        }
    }

    private static boolean booleanEnv(String name, boolean fallback) {
        String value = System.getenv(name);
        if (isBlank(value)) {
            return fallback;
        }
        return switch (value.trim().toLowerCase()) {
            case "1", "true", "yes", "on" -> true;
            case "0", "false", "no", "off" -> false;
            default -> throw new IllegalArgumentException(
                    name + " must be one of true/false, yes/no, on/off, or 1/0.");
        };
    }

    private static Path pathEnv(String name) {
        String value = System.getenv(name);
        return isBlank(value) ? null : Path.of(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
