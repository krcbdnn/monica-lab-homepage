package com.monicalab.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MariaDBContainer;

@ActiveProfiles("test")
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MariaDBContainer<?> MARIADB_CONTAINER = new MariaDBContainer<>("mariadb:11.4.12");

    static {
        MARIADB_CONTAINER.start();
    }
}
