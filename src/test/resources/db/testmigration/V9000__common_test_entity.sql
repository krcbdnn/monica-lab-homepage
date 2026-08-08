-- Test-only schema, applied via the additional `classpath:db/testmigration` Flyway
-- location configured in application-test.yml. Never included in the production
-- artifact (src/test/resources is excluded from bootJar). Versions in this location
-- are reserved starting at V9000 to avoid colliding with src/main/resources/db/migration.
CREATE TABLE common_test_entity (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
