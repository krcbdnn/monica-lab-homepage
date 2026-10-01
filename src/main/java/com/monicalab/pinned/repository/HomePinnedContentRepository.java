package com.monicalab.pinned.repository;

import com.monicalab.pinned.entity.HomePinnedContent;
import com.monicalab.pinned.entity.HomeTargetType;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomePinnedContentRepository extends JpaRepository<HomePinnedContent, Long> {

    boolean existsByTargetTypeAndTargetId(HomeTargetType targetType, Long targetId);

    List<HomePinnedContent> findByIsVisibleTrue(Sort sort);
}
