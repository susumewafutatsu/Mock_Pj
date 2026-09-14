package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;

/** Bộ thẻ một người đã thêm vào lịch học. */
@Entity
@Table(name = "DeckEnrollments")
@IdClass(DeckEnrollment.Key.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeckEnrollment {

    @Id
    @Column(name = "UserID", length = 50)
    private String userId;

    @Id
    @Column(name = "DeckID")
    private Integer deckId;

    @Column(name = "EnrolledAt")
    private LocalDateTime enrolledAt;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private String userId;
        private Integer deckId;
    }
}
