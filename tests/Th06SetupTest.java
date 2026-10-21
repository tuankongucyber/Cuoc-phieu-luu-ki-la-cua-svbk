import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Offline HTTP fixtures for first-run downloads: no internet or game-data dependency. */
public final class Th06SetupTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    static void rejects(IoAction action) throws Exception {
        try {
            action.run();
            throw new AssertionError("Expected download failure");
        } catch (IOException expected) {
        }
    }

    @FunctionalInterface
    interface IoAction {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("svbk-download-test-");
        byte[] payload =
                "SVBK data download fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        HttpServer server =
                HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext(
                "/pack",
                exchange -> {
                    exchange.sendResponseHeaders(200, payload.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(payload);
                    }
                });
        server.createContext(
                "/redirect",
                exchange -> {
                    exchange.getResponseHeaders().set("Location", "/pack");
                    exchange.sendResponseHeaders(302, -1);
                    exchange.close();
                });
        server.createContext(
                "/missing",
                exchange -> {
                    exchange.sendResponseHeaders(404, -1);
                    exchange.close();
                });
        server.createContext(
                "/large",
                exchange -> {
                    exchange.sendResponseHeaders(200, Th06Setup.MAX_BYTES + 1);
                    exchange.close();
                });
        server.start();
        try {
            String host = server.getAddress().getAddress().getHostAddress();
            if (host.contains(":")) host = "[" + host + "]";
            URI base = URI.create("http://" + host + ":" + server.getAddress().getPort());
            Path target = dir.resolve("reference-data.zip");
            List<Integer> progress = new ArrayList<>();
            Th06Setup.download(base.resolve("/redirect"), target, hash, progress::add);
            check(
                    Arrays.equals(Files.readAllBytes(target), payload),
                    "redirected download publishes exact data");
            check(!progress.isEmpty() && progress.get(0) == 0, "download reports progress");
            rejects(
                    () ->
                            Th06Setup.download(
                                    base.resolve("/pack"), target, "0".repeat(64), p -> {}));
            rejects(() -> Th06Setup.download(base.resolve("/missing"), target, hash, p -> {}));
            rejects(() -> Th06Setup.download(base.resolve("/large"), target, hash, p -> {}));
            check(
                    Arrays.equals(Files.readAllBytes(target), payload),
                    "failed download preserves previous valid cache");
            try (var files = Files.list(dir)) {
                check(
                        files.noneMatch(p -> p.toString().endsWith(".part")),
                        "failed downloads leave no partial file");
            }
            String previous = System.getProperty("svbk.th06.data");
            String previousBundle = System.getProperty("svbk.th06.bundle");
            try {
                System.setProperty("svbk.th06.bundle", "missing-bundle-for-test.zip");
                System.setProperty("svbk.th06.data", dir.toString());
                Files.delete(target);
                Files.createFile(dir.resolve("ecldata7.ecl"));
                check(
                        !Th06Data.available() && !Th06Data.ready(2, 6),
                        "unrelated or empty stage 7 does not mark a run ready");
                rejects(() -> Th06Setup.importPack(dir.resolve("ecldata7.ecl"), p -> {}));
                for (String name :
                        List.of(
                                "etama3.anm",
                                "etama4.anm",
                                "ecldata2.ecl",
                                "stg2enm.anm",
                                "stg2enm2.anm",
                                "ecldata6.ecl",
                                "stg6enm.anm",
                                "stg6enm2.anm")) Files.write(dir.resolve(name), new byte[] {1});
                check(
                        Th06Data.ready(2, 6) && Th06Data.available(),
                        "normal-run readiness checks stages 2 and 6, independent of stage 7");
                Files.delete(dir.resolve("etama4.anm"));
                check(!Th06Data.ready(2, 6), "missing shared metadata prevents premature startup");
            } finally {
                if (previous == null) System.clearProperty("svbk.th06.data");
                else System.setProperty("svbk.th06.data", previous);
                if (previousBundle == null) System.clearProperty("svbk.th06.bundle");
                else System.setProperty("svbk.th06.bundle", previousBundle);
            }
        } finally {
            server.stop(0);
            try (var files = Files.list(dir)) {
                for (Path file : files.toList()) Files.deleteIfExists(file);
            }
            Files.delete(dir);
        }
        System.out.println(
                "PASS Th06SetupTest: redirects, progress, SHA-256, HTTP errors, size cap, atomic"
                    + " cache and readiness");
    }
}
