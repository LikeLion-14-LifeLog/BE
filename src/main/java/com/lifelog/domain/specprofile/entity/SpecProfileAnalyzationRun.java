package com.lifelog.domain.specprofile.entity;

import com.lifelog.domain.job.entity.Job;
import com.lifelog.domain.specprofile.enums.SpecProfileAnalyzationStatus;
import com.lifelog.domain.specprofile.exception.SpecProfileAnalyzationErrorCode;
import com.lifelog.global.exception.CustomException;
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

    /**
     * 결과 저장을 마친 진행 중 실행을 완료 상태로 변경한다.
     */
    public void completeAnnualSalaryAnalyzation() {
        if (status != SpecProfileAnalyzationStatus.IN_PROGRESS) {
            throw new CustomException(SpecProfileAnalyzationErrorCode.RESULT_SAVE_FAILED);
        }
        status = SpecProfileAnalyzationStatus.COMPLETED;
    }
}
