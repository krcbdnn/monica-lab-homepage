CREATE TABLE home_pinned_content (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,
    target_id BIGINT NOT NULL,
    sort_order INT NOT NULL,
    is_visible BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_home_pinned_content_target UNIQUE (target_type, target_id)
);

CREATE INDEX idx_home_pinned_content_sort_order ON home_pinned_content (sort_order);
