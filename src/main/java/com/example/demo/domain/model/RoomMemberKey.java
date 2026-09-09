package com.example.demo.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

/** Khoá của {@link RoomMember}: một người chỉ có một tư cách trong một phòng. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RoomMemberKey implements Serializable {

    @Column(name = "RoomID")
    private Integer roomId;

    @Column(name = "UserID", length = 50)
    private String userId;
}
