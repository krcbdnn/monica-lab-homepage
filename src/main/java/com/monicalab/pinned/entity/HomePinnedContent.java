package com.monicalab.pinned.entity;

import com.monicalab.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "home_pinned_content")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomePinnedContent extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ERD.md 원칙(Entity 간 FK를 두지 않음)에 따라 @ManyToOne이 아니라 순수 값 컬럼으로만 Board/Program을
    // 참조한다. 실존/공개 여부 검증은 HomePinnedContentService가 애플리케이션 레벨로 수행한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private HomeTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible;

    @Builder
    private HomePinnedContent(HomeTargetType targetType, Long targetId, int sortOrder, boolean isVisible) {
        this.targetType = targetType;
        this.targetId = targetId;
        this.sortOrder = sortOrder;
        this.isVisible = isVisible;
    }

    public void updateVisibility(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public void updateOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
