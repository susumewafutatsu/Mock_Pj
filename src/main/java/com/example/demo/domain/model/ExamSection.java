package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Một KHỐI THỜI GIAN của đề thi — thứ thí sinh thật sự trải nghiệm. */
@Entity
@Table(name = "ExamSections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamSection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SectionID")
    private Integer sectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ExamID", nullable = false)
    private Exam exam;

    @Column(name = "Name", nullable = false, length = 120)
    private String name;

    @Column(name = "DurationMinutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "OrderNo", nullable = false)
    @Builder.Default
    private Integer orderNo = 1;

    @Column(name = "CreatedAt", updatable = false, insertable = false)
    private LocalDateTime createdAt;
}
