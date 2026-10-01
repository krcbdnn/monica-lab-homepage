package com.monicalab.pinned.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.monicalab.pinned.entity.HomePinnedContent;
import com.monicalab.pinned.entity.HomeTargetType;
import com.monicalab.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class HomePinnedContentRepositoryTest extends AbstractIntegrationTest {

    @Autowired
    private HomePinnedContentRepository homePinnedContentRepository;

    @BeforeEach
    void setUp() {
        homePinnedContentRepository.deleteAll();
    }

    @Test
    void savingDuplicateTargetThrowsDataIntegrityViolationException() {
        homePinnedContentRepository.saveAndFlush(HomePinnedContent.builder()
                .targetType(HomeTargetType.BOARD)
                .targetId(1L)
                .sortOrder(0)
                .isVisible(true)
                .build());

        HomePinnedContent duplicate = HomePinnedContent.builder()
                .targetType(HomeTargetType.BOARD)
                .targetId(1L)
                .sortOrder(1)
                .isVisible(true)
                .build();

        assertThatThrownBy(() -> homePinnedContentRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTargetIdWithDifferentTargetTypeDoesNotViolateUniqueConstraint() {
        homePinnedContentRepository.saveAndFlush(HomePinnedContent.builder()
                .targetType(HomeTargetType.BOARD)
                .targetId(1L)
                .sortOrder(0)
                .isVisible(true)
                .build());

        HomePinnedContent programWithSameId = HomePinnedContent.builder()
                .targetType(HomeTargetType.PROGRAM)
                .targetId(1L)
                .sortOrder(1)
                .isVisible(true)
                .build();

        homePinnedContentRepository.saveAndFlush(programWithSameId);

        assertThat(homePinnedContentRepository.findAll()).hasSize(2);
    }
}
