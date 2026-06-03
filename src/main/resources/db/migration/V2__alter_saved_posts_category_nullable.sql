-- Make category_id nullable (NULL = uncategorized) and switch FK to ON DELETE SET NULL.
-- Previously category_id was NOT NULL with ON DELETE CASCADE.
-- New design: "save first, categorize later" — posts can exist without a category.
-- When a category is deleted, its posts become uncategorized (category_id → NULL) automatically.

ALTER TABLE saved_posts
    MODIFY COLUMN category_id BINARY(16) NULL COMMENT 'Belonging category (NULL = uncategorized)';

ALTER TABLE saved_posts
    DROP FOREIGN KEY fk_saved_posts_category_id;

ALTER TABLE saved_posts
    ADD CONSTRAINT fk_saved_posts_category_id
        FOREIGN KEY (category_id) REFERENCES categories (id)
            ON DELETE SET NULL;