package com.lifelog.domain.specprofile.entity;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "spec_profile_analyzation_run")
public class SpecProfileAnalyzationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "delete_at")
    private LocalDateTime deleteAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SpecProfileAnalyzationStatus status;

    @Column(name = "canceled_at")
    private LocalDateTime canceledAt;

    @Column(name = "spec_profile_snapshot_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String specProfileSnapshotJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "spec_profile_id", nullable = false)
    private SpecProfile specProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;
}
