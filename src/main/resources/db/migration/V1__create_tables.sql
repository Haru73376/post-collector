CREATE TABLE users
(
    id            BINARY(16)   NOT NULL COMMENT 'UUID v7',
    username      VARCHAR(50)  NOT NULL COMMENT 'Display name (unique)',
    email         VARCHAR(255) NOT NULL COMMENT 'Login email address (unique)',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt hashed password',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_users_username (username),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='User accounts';

CREATE TABLE refresh_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BINARY(16)   NOT NULL COMMENT 'Token owner',
    token_hash VARCHAR(255) NOT NULL COMMENT 'SHA-256 hashed refresh token',
    expires_at TIMESTAMP    NOT NULL COMMENT 'Token expiry (7 days after issuance)',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_refresh_tokens_token_hash (token_hash),
    INDEX      idx_refresh_tokens_user_id (user_id),

    CONSTRAINT fk_refresh_tokens_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='JWT refresh token management. Stores SHA-256 hashed tokens only';

CREATE TABLE categories
(
    id         BINARY(16)   NOT NULL COMMENT 'UUID v7',
    user_id    BINARY(16)   NOT NULL COMMENT 'Category owner',
    parent_id  BINARY(16)   NULL     COMMENT 'Parent category ID. NULL means root',
    name       VARCHAR(100) NOT NULL COMMENT 'Category name',
    sort_order INT          NOT NULL DEFAULT 0 COMMENT 'Display order within the same level',
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_categories_user_parent_name (user_id, parent_id, name),
    INDEX      idx_categories_user_id (user_id),
    INDEX      idx_categories_parent_id (parent_id),

    CONSTRAINT fk_categories_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_categories_parent_id
        FOREIGN KEY (parent_id) REFERENCES categories (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Hierarchical categories (up to 3 levels). NULL parent_id indicates root';

CREATE TABLE saved_posts
(
    id            BINARY(16)    NOT NULL COMMENT 'UUID v7',
    user_id       BINARY(16)    NOT NULL COMMENT 'Post owner',
    category_id   BINARY(16)    NOT NULL COMMENT 'Belonging category',
    url           VARCHAR(2048) NOT NULL COMMENT 'SNS post URL (HTTPS required)',
    title         VARCHAR(255)  NOT NULL COMMENT 'Post title',
    memo          TEXT NULL     COMMENT 'Free-form user memo',
    thumbnail_url VARCHAR(2048) NULL     COMMENT 'Thumbnail image URL (HTTPS required)',
    platform      VARCHAR(20)   NOT NULL COMMENT 'INSTAGRAM/YOUTUBE/PINTEREST/TIKTOK/X/OTHER',
    is_favorite   BOOLEAN       NOT NULL DEFAULT FALSE COMMENT 'Favorite flag',
    created_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP NULL     COMMENT 'Soft delete. NULL=active, non-NULL=deleted',

    PRIMARY KEY (id),
    INDEX         idx_saved_posts_user_id (user_id),
    INDEX         idx_saved_posts_category_id (category_id),
    INDEX         idx_saved_posts_platform (platform),
    INDEX         idx_saved_posts_deleted_at (deleted_at),

    CONSTRAINT fk_saved_posts_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_saved_posts_category_id
        FOREIGN KEY (category_id) REFERENCES categories (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Saved social media posts with soft delete support';

CREATE TABLE tags
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    user_id    BINARY(16)  NOT NULL COMMENT 'Tag owner',
    name       VARCHAR(50) NOT NULL COMMENT 'Tag name (unique per user)',
    created_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_tags_user_name (user_id, name),
    INDEX      idx_tags_user_id (user_id),

    CONSTRAINT fk_tags_user_id
        FOREIGN KEY (user_id) REFERENCES users (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='User-defined flat tags for cross-category organization';

CREATE TABLE post_tags
(
    post_id BINARY(16) NOT NULL COMMENT 'Saved post ID',
    tag_id  BIGINT NOT NULL COMMENT 'Tag ID',

    PRIMARY KEY (post_id, tag_id),
    INDEX   idx_post_tags_tag_id (tag_id),

    CONSTRAINT fk_post_tags_post_id
        FOREIGN KEY (post_id) REFERENCES saved_posts (id)
            ON DELETE CASCADE,
    CONSTRAINT fk_post_tags_tag_id
        FOREIGN KEY (tag_id) REFERENCES tags (id)
            ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Many-to-many junction table between saved_posts and tags';