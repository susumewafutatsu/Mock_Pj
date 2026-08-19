package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "SubjectLevels", uniqueConstraints = {
    @UniqueConstraint(name = "UC_Subject_Level", columnNames = {"SubjectID", "LevelName"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "LevelID")
    private Integer levelId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "SubjectID", nullable = false)
    private Subject subject;

    @Column(name = "LevelName", nullable = false, length = 50)
    private String levelName;

    @Column(name = "DisplayOrder")
    @Builder.Default
    private Integer displayOrder = 1;
}