package com.monicalab.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

class CommonConfigTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void jpaAuditingIsRegisteredExactlyOnce() {
        assertThat(applicationContext.getBeansOfType(AuditingHandler.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(CommonConfig.class)).hasSize(1);
    }

    @Test
    void methodValidationPostProcessorIsRegistered() {
        assertThat(applicationContext.getBeansOfType(MethodValidationPostProcessor.class)).hasSize(1);
    }
}
