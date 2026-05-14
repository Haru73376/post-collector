package com.github.haru73376.post_collector.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "users")
@NoArgsConstructor
@Getter
public class User {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Setter
    private String username;

    @Setter
    private String email;

    @Setter
    private String passwordHash;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
