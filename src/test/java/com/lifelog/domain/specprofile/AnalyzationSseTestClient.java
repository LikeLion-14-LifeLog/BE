package com.lifelog.domain.specprofile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

final class AnalyzationSseTestClient implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService reader = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();
    private final CompletableFuture<Void> ended = new CompletableFuture<>();
    private final InputStream body;

    AnalyzationSseTestClient(int port, Long runId, String token, String cursor) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                + "/api/spec-profile/analyzation/runs/" + runId + "/events"))
                .timeout(Duration.ofSeconds(10)).header("Accept", "text/event-stream")
                .header("Authorization", "Bearer " + token).GET();
        if (cursor != null) request.header("Last-Event-ID", cursor);
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        body = response.body();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).startsWith("text/event-stream");
        assertThat(response.headers().firstValue("cache-control")).contains("no-cache");
        assertThat(response.headers().firstValue("x-accel-buffering")).contains("no");
        reader.execute(this::read);
    }

    Event next() throws Exception {
        Event event = events.poll(10, TimeUnit.SECONDS);
        assertThat(event).as("10초 내 SSE 이벤트 수신").isNotNull();
        return event;
    }

    Event nextData() throws Exception {
        Event event = next();
        while (event.name().equals("heartbeat")) event = next();
        return event;
    }

    void awaitClosed() throws Exception { ended.get(10, TimeUnit.SECONDS); }

    private void read() {
        try (BufferedReader lines = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String id = null;
            String name = null;
            StringBuilder data = new StringBuilder();
            String line;
            while ((line = lines.readLine()) != null) {
                if (line.startsWith("id:")) id = line.substring(3).strip();
                else if (line.startsWith("event:")) name = line.substring(6).strip();
                else if (line.startsWith("data:")) data.append(line.substring(5).strip());
                else if (line.startsWith(":")) name = "heartbeat";
                else if (line.isEmpty()) {
                    if (name != null) events.add(new Event(id, name, data.toString()));
                    id = null;
                    name = null;
                    data.setLength(0);
                }
            }
            ended.complete(null);
        } catch (Exception exception) {
            ended.completeExceptionally(exception);
        }
    }

    @Override
    public void close() throws Exception {
        body.close();
        reader.shutdownNow();
        client.shutdownNow();
    }

    record Event(String id, String name, String data) {}
}
