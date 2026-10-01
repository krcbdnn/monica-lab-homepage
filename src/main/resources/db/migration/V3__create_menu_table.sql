CREATE TABLE menu (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    label VARCHAR(50) NOT NULL,
    parent_id BIGINT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_value VARCHAR(255) NULL,
    sort_order INT NOT NULL,
    is_visible BOOLEAN NOT NULL,
    open_in_new_tab BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
