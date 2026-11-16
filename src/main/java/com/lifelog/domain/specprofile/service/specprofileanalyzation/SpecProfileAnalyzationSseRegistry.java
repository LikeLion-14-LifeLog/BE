package com.lifelog.domain.specprofile.service.specprofileanalyzation;

import com.lifelog.domain.specprofile.config.SpecProfileAnalyzationSseConfig;
import com.lifelog.domain.specprofile.dto.SpecProfileAnalyzationEventResDto;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStep;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class SpecProfileAnalyzationSseRegistry {
    private static final int MAX_PENDING_EVENTS = 64;
    private final SpecProfileAnalyzationProgressService progressService;
    private final SpecProfileAnalyzationProgressQueryService queryService;
    private final SpecProfileAnalyzationRunStateService stateService;
    private final Executor executor;
    private final long timeout;
    private final Map<Long, Map<UUID, Subscription>> subscriptions = new ConcurrentHashMap<>();

    /** 상태 저장 서비스 및 전송 전용 실행 풀을 주입한다. @param progressService 단계 저장 @param queryService 상태 조회 @param stateService 실행 전환 @param executor 전송 실행 풀 @param timeout 연결 제한 시간 */
    public SpecProfileAnalyzationSseRegistry(SpecProfileAnalyzationProgressService progressService,
            SpecProfileAnalyzationProgressQueryService queryService, SpecProfileAnalyzationRunStateService stateService,
            @Qualifier(SpecProfileAnalyzationSseConfig.EXECUTOR_NAME) Executor executor,
            @Value("${spec-profile.analyzation.sse.timeout-ms:1800000}") long timeout) {
        this.progressService = progressService;
        this.queryService = queryService;
        this.stateService = stateService;
        this.executor = executor;
        this.timeout = timeout;
    }

    /** 본인 실행에 연결을 등록하고 초기화 중의 변경을 보관한 뒤 최신 snapshot부터 전달한다. @param memberId 인증 회원 @param runId 실행 ID @param lastEventId 재접속 커서 @return 구독 연결 */
    public SseEmitter subscribe(Long memberId, Long runId, String lastEventId) {
        log.info("[SpecProfileAnalyzationSseRegistry] 구독 | subscribe() - START | memberId: {}, runId: {}", memberId, runId);
        queryService.getOwnedSnapshot(memberId, runId, lastEventId);
        Subscription subscription = new Subscription(runId);
        subscriptions.compute(runId, (key, group) -> {
            Map<UUID, Subscription> result = group == null ? new ConcurrentHashMap<>() : group;
            result.put(subscription.id, subscription);
            return result;
        });
        try {
            subscription.initialize(queryService.getOwnedSnapshot(memberId, runId, null));
        } catch (RuntimeException exception) {
            subscription.close(true);
            throw exception;
        }
        log.info("[SpecProfileAnalyzationSseRegistry] 구독 | subscribe() - END | runId: {}, subscriptionId: {}", runId, subscription.id);
        return subscription.emitter;
    }

    /** analyze가 명시한 단계의 완료를 커밋한 뒤 progress를 전달한다. @param runId 실행 ID @param step 완료한 ENUM 단계 @return 진행을 계속할 수 있는지 여부 */
    public boolean progress(Long runId, SpecProfileAnalyzationStep step) {
        log.info("[SpecProfileAnalyzationSseRegistry] 진행 통지 | progress() - START | runId: {}, step: {}", runId, step);
        Optional<SpecProfileAnalyzationEventResDto> progress = progressService.completeStep(runId, step);
        progress.ifPresent(data -> broadcast("progress", data));
        log.info("[SpecProfileAnalyzationSseRegistry] 진행 통지 | progress() - END | runId: {}, continued: {}", runId, progress.isPresent());
        return progress.isPresent();
    }

    /** 결과와 완료 상태가 커밋된 실행의 완료 이벤트를 전송한다. @param runId 실행 ID */
    public void completed(Long runId) {
        log.info("[SpecProfileAnalyzationSseRegistry] 완료 통지 | completed() - START | runId: {}", runId);
        sendTerminal(runId, SpecProfileAnalyzationStatus.COMPLETED);
        log.info("[SpecProfileAnalyzationSseRegistry] 완료 통지 | completed() - END | runId: {}", runId);
    }

    /** 실행 실패와 해당 단계의 실패를 독립 커밋하고 failed를 전송한다. @param runId 실행 ID */
    public void failed(Long runId) {
        log.info("[SpecProfileAnalyzationSseRegistry] 실패 통지 | failed() - START | runId: {}", runId);
        stateService.failIfRunning(runId);
        SpecProfileAnalyzationEventResDto data = queryService.getSnapshot(runId);
        if (data.status() == SpecProfileAnalyzationStatus.FAILED) {
            broadcast("failed", data);
        } else if (data.status() == SpecProfileAnalyzationStatus.CANCELED) {
            canceled(runId);
        }
        log.info("[SpecProfileAnalyzationSseRegistry] 실패 통지 | failed() - END | runId: {}", runId);
    }

    /** 확정된 취소의 단계 이력을 보완하고 canceled를 전송한다. 연결 해제에서는 호출하지 않는다. @param runId 실행 ID */
    public void canceled(Long runId) {
        log.info("[SpecProfileAnalyzationSseRegistry] 취소 통지 | canceled() - START | runId: {}", runId);
        stateService.recordCanceled(runId);
        sendTerminal(runId, SpecProfileAnalyzationStatus.CANCELED);
        log.info("[SpecProfileAnalyzationSseRegistry] 취소 통지 | canceled() - END | runId: {}", runId);
    }

    /** 등록된 연결에 주석 heartbeat를 예약한다. 분석 스레드에서는 네트워크 쓰기를 수행하지 않는다. */
    @Scheduled(fixedDelayString = "${spec-profile.analyzation.sse.heartbeat-interval-ms:15000}")
    public void heartbeat() {
        log.debug("[SpecProfileAnalyzationSseRegistry] heartbeat() - START");
        subscriptions.values().forEach(group -> group.values().forEach(Subscription::heartbeat));
        log.debug("[SpecProfileAnalyzationSseRegistry] heartbeat() - END");
    }

    /** 서버 종료 시 모든 연결과 보관 이벤트를 정리한다. */
    @PreDestroy
    public void shutdown() {
        log.info("[SpecProfileAnalyzationSseRegistry] shutdown() - START");
        subscriptions.values().forEach(group -> group.values().forEach(subscription -> subscription.close(true)));
        log.info("[SpecProfileAnalyzationSseRegistry] shutdown() - END");
    }

    /** 커밋된 실제 상태가 요청한 종료 종류와 일치할 때만 전송한다. @param runId 실행 ID @param expected 기대 종료 상태 */
    private void sendTerminal(Long runId, SpecProfileAnalyzationStatus expected) {
        log.debug("[SpecProfileAnalyzationSseRegistry] sendTerminal() - START | runId: {}", runId);
        SpecProfileAnalyzationEventResDto data = queryService.getSnapshot(runId);
        if (data.status() == expected) broadcast(terminalName(expected), data);
        log.debug("[SpecProfileAnalyzationSseRegistry] sendTerminal() - END | runId: {}", runId);
    }

    /** 실행의 모든 구독에 불변 데이터 전송을 예약한다. @param event SSE 이벤트 이름 @param data 커밋된 상태 */
    private void broadcast(String event, SpecProfileAnalyzationEventResDto data) {
        log.debug("[SpecProfileAnalyzationSseRegistry] broadcast() - START | runId: {}, event: {}", data.runId(), event);
        Map<UUID, Subscription> group = subscriptions.get(data.runId());
        if (group != null) group.values().forEach(subscription -> subscription.offer(new Delivery(event, data)));
        log.debug("[SpecProfileAnalyzationSseRegistry] broadcast() - END | runId: {}", data.runId());
    }

    /** 실행 종료 상태의 SSE 이름을 반환한다. @param status 실행 상태 @return 종료 이름 또는 실행 중이면 null */
    private static String terminalName(SpecProfileAnalyzationStatus status) {
        return switch (status) {
            case COMPLETED -> "completed";
            case FAILED -> "failed";
            case CANCELED -> "canceled";
            default -> null;
        };
    }

    private record Delivery(String event, SpecProfileAnalyzationEventResDto data) {
        /** 종료 이벤트인지 반환한다. @return 종료 여부 */
        boolean terminal() { return !event.equals("snapshot") && data != null && terminalName(data.status()) != null; }
    }

    private final class Subscription {
        private final UUID id = UUID.randomUUID();
        private final Long runId;
        private final SseEmitter emitter = new SseEmitter(timeout);
        private final ArrayDeque<Delivery> queue = new ArrayDeque<>();
        private final TreeMap<Long, Delivery> initializing = new TreeMap<>();
        private boolean ready;
        private boolean draining;
        private boolean closing;
        private boolean closed;
        private long lastEventId;

        /** 연결 종료 콜백을 개별 구독에 등록한다. @param runId 실행 ID */
        private Subscription(Long runId) {
            this.runId = runId;
            emitter.onCompletion(() -> close(false));
            emitter.onTimeout(() -> close(true));
            emitter.onError(exception -> close(false));
        }

        /** snapshot을 먼저 배치하고 초기화 중의 이후 이벤트를 순서대로 연결한다. @param snapshot 최신 전체 상태 */
        private void initialize(SpecProfileAnalyzationEventResDto snapshot) {
            synchronized (this) {
                if (closed) return;
                ready = true;
                lastEventId = snapshot.eventLogId() == null ? 0 : snapshot.eventLogId();
                queue.add(new Delivery("snapshot", snapshot));
                String terminal = terminalName(snapshot.status());
                if (terminal != null) {
                    queue.add(new Delivery(terminal, snapshot));
                    closing = true;
                } else {
                    for (Delivery delivery : initializing.values()) enqueue(delivery);
                }
                initializing.clear();
            }
            scheduleDrain();
        }

        /** 데이터 이벤트를 중복 없이 예약하며 초기화 전에는 임시 보관한다. @param delivery 전송 내용 */
        private void offer(Delivery delivery) {
            synchronized (this) {
                if (closed || closing) return;
                if (!ready) {
                    if (delivery.data().eventLogId() != null) initializing.put(delivery.data().eventLogId(), delivery);
                    if (initializing.size() > MAX_PENDING_EVENTS) close(true);
                    return;
                }
                enqueue(delivery);
            }
            scheduleDrain();
        }

        /**
         * 연결별 전송 큐에 이벤트를 등록하며 중복·역순 이벤트를 제외한다.
         * 이벤트를 재정렬하지 않고, 마지막으로 큐에 등록한 PK 이하의 이벤트를 버린다.
         * 실제 SSE 전송은 큐를 처리하는 drain()에서 수행한다.
         * @param delivery 전송할 이벤트와 종료 여부
         */
        private void enqueue(Delivery delivery) {
            // (1) 종료 이벤트가 이미 등록된 연결에는 추가 이벤트를 받지 않는다.
            if (closing) return;

            /*
             * (2) 중복·역순 PK를 제외하고 마지막 등록 PK를 갱신한다.
             * lastEventId는 전송 완료 기준이 아닌 큐 등록 기준이며, null PK는 검사를 생략한다.
             */
            Long eventId = delivery.data().eventLogId();
            if (eventId != null && eventId <= lastEventId) return;
            if (eventId != null) lastEventId = eventId;

            /*
             * (3) 이벤트를 큐에 넣고, 종료 이벤트라면 이후 등록을 차단한다.
             * 등록된 종료 이벤트는 drain()에서 전송한 뒤 연결을 종료한다.
             */
            queue.add(delivery);
            closing = delivery.terminal();

            // (4) 전송 대기가 한도를 초과하면 연결을 종료해 큐의 누적을 제한한다.
            if (queue.size() > MAX_PENDING_EVENTS) close(true);
        }

        /** 연결 유지 주석을 데이터와 같은 전송 큐에 추가한다. */
        private void heartbeat() {
            synchronized (this) {
                if (!ready || closed || closing) return;
                queue.add(new Delivery("heartbeat", null));
                if (queue.size() > MAX_PENDING_EVENTS) close(true);
            }
            scheduleDrain();
        }

        /** 연결당 하나의 전송 작업만 실행하며 풀 포화는 해당 연결 종료로 처리한다. */
        private void scheduleDrain() {
            synchronized (this) {
                if (closed || draining || queue.isEmpty()) return;
                draining = true;
            }
            try {
                executor.execute(this::drain);
            } catch (RuntimeException exception) {
                log.warn("[SpecProfileAnalyzationSseRegistry] SSE 전송 예약 실패 | runId: {}", runId);
                close(true);
            }
        }

        /** 네트워크 쓰기를 전용 실행 풀에서 순서대로 수행한다. */
        private void drain() {
            while (true) {
                Delivery delivery;
                synchronized (this) {
                    delivery = closed ? null : queue.poll();
                    if (delivery == null) { draining = false; return; }
                }
                try {
                    if (delivery.data() == null) {
                        emitter.send(SseEmitter.event().comment("heartbeat"));
                    } else {
                        SseEmitter.SseEventBuilder event = SseEmitter.event().name(delivery.event())
                                .data(delivery.data(), MediaType.APPLICATION_JSON);
                        if (delivery.data().eventLogId() != null) event.id(delivery.data().eventLogId().toString());
                        emitter.send(event);
                    }
                    if (delivery.terminal()) { close(true); return; }
                } catch (IOException exception) {
                    close(false);
                    return;
                } catch (RuntimeException exception) {
                    log.debug("[SpecProfileAnalyzationSseRegistry] SSE 전송 종료 | runId: {}", runId, exception);
                    close(true);
                    return;
                }
            }
        }

        /** 현재 구독만 제거하고 분석 실행 상태는 변경하지 않는다. @param complete emitter 완료 여부 */
        private void close(boolean complete) {
            synchronized (this) {
                if (closed) return;
                closed = true;
                queue.clear();
                initializing.clear();
            }
            subscriptions.computeIfPresent(runId, (key, group) -> {
                group.remove(id);
                return group.isEmpty() ? null : group;
            });
            if (complete) {
                try {
                    emitter.complete();
                } catch (RuntimeException exception) {
                    log.debug("[SpecProfileAnalyzationSseRegistry] 종료된 연결 정리 | runId: {}", runId, exception);
                }
            }
        }
    }
}
