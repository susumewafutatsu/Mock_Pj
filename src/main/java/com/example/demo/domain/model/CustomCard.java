package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** Thẻ do người học tự soạn. */
@Entity
@Table(name = "CustomCards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "CardID")
    private Integer cardId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "OwnerID", nullable = false)
    private User owner;

    @Column(name = "Front", nullable = false, length = 200)
    private String front;

    @Column(name = "Reading", length = 200)
    private String reading;

    @Column(name = "Back", nullable = false, length = 500)
    private String back;

    @Column(name = "Example", length = 500)
    private String example;

    @Column(name = "ExampleMeaning", length = 500)
    private String exampleMeaning;

    @Column(name = "Note", length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "CreatedAt", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "UpdatedAt")
    private LocalDateTime updatedAt;
}
