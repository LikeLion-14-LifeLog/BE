package com.lifelog.domain.specprofile.entity;

import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "spec_profile_analyzation_event_log", uniqueConstraints =
        @UniqueConstraint(name = "uq_analyzation_event_order", columnNames = {"spec_profile_analyzation_run_id", "sort_order"}))
public class SpecProfileAnalyzationEventLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_log_id")
    private Long id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SpecProfileAnalyzationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_profile_analyzation_run_id", nullable = false)
    private SpecProfileAnalyzationRun run;
}
