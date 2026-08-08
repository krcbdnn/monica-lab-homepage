CREATE TABLE admin (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    login_id VARCHAR(50) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_admin_login_id UNIQUE (login_id)
);

CREATE TABLE program (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    program_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NULL,
    thumbnail VARCHAR(255) NULL,
    attachment VARCHAR(255) NULL,
    google_form_url VARCHAR(500) NULL,
    recruit_status VARCHAR(20) NOT NULL,
    is_public BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE board (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    board_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NULL,
    thumbnail VARCHAR(255) NULL,
    attachment VARCHAR(255) NULL,
    view_count INT NOT NULL,
    is_public BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE banner (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    image VARCHAR(255) NOT NULL,
    link_url VARCHAR(500) NULL,
    sort_order INT NOT NULL,
    is_visible BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE popup (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    content LONGTEXT NULL,
    start_date DATETIME NOT NULL,
    end_date DATETIME NOT NULL,
    is_visible BOOLEAN NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE page (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    page_type VARCHAR(20) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content LONGTEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_page_page_type UNIQUE (page_type)
);

CREATE TABLE file (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size BIGINT NOT NULL,
    file_type VARCHAR(20) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
