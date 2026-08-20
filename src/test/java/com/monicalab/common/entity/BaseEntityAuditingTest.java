package com.monicalab.common.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BaseEntityAuditingTest extends AbstractIntegrationTest {

    @Autowired
    private CommonTestEntityRepository commonTestEntityRepository;

    @Test
    void createdAtAndUpdatedAtAreSetOnSave() {
        CommonTestEntity entity = CommonTestEntity.builder()
                .name("base-entity-auditing-check")
                .build();

        CommonTestEntity saved = commonTestEntityRepository.saveAndFlush(entity);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
