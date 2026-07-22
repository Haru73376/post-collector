package com.github.haru73376.post_collector.post;

import com.github.haru73376.post_collector.category.Category;
import com.github.haru73376.post_collector.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "saved_posts")
@SQLRestriction("deleted_at IS NULL")
@Getter
public class SavedPost {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @Setter
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    @Setter
    private Category category;

    @Setter
    private String url;

    @Setter
    private String title;

    @Setter
    private String memo;

    @Setter
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Setter
    private Platform platform;

    @Setter
    private boolean isFavorite;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Setter
    private LocalDateTime deletedAt;
}
