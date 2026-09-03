package com.monicalab.menu.entity;

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
@Table(name = "menu")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Menu extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "label", nullable = false, length = 50)
    private String label;

    // ERD.md 원칙(Entity 간 FK를 두지 않음)에 따라 @ManyToOne 관계가 아니라 순수 값 컬럼으로만 부모를
    // 참조한다. 유효성(자기참조/미존재/GROUP만 부모 가능 등)은 MenuService에서 애플리케이션 레벨로 검증한다.
    @Column(name = "parent_id")
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private MenuTargetType targetType;

    @Column(name = "target_value", length = 255)
    private String targetValue;

    // P13-T30D(Task C): targetType=BOARD_LIST && targetValue='REVIEW'일 때만 의미를 갖는 보조 값
    // (COURSE/SPECIAL로 수강 후기/특강 후기를 구분). "REVIEW:COURSE" 같은 복합 문자열을 targetValue에
    // 욱여넣지 않고 별도 컬럼으로 분리했다. 그 외 모든 조합에서는 항상 NULL이어야 하며, 이는
    // MenuService.validateTarget()이 강제한다.
    @Column(name = "target_subvalue", length = 50)
    private String targetSubvalue;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible;

    @Column(name = "open_in_new_tab", nullable = false)
    private boolean openInNewTab;

    @Builder
    private Menu(String label, Long parentId, MenuTargetType targetType, String targetValue, String targetSubvalue,
            int sortOrder, boolean isVisible, boolean openInNewTab) {
        this.label = label;
        this.parentId = parentId;
        this.targetType = targetType;
        this.targetValue = targetValue;
        this.targetSubvalue = targetSubvalue;
        this.sortOrder = sortOrder;
        this.isVisible = isVisible;
        this.openInNewTab = openInNewTab;
    }

    public void update(String label, Long parentId, MenuTargetType targetType, String targetValue,
            String targetSubvalue, int sortOrder, boolean isVisible, boolean openInNewTab) {
        this.label = label;
        this.parentId = parentId;
        this.targetType = targetType;
        this.targetValue = targetValue;
        this.targetSubvalue = targetSubvalue;
        this.sortOrder = sortOrder;
        this.isVisible = isVisible;
        this.openInNewTab = openInNewTab;
    }

    public void updateVisibility(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public void updateOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
