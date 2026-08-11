package com.monicalab.banner.entity;

import com.monicalab.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "banner")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Banner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "image", nullable = false, length = 255)
    private String image;

    @Column(name = "link_url", length = 500)
    private String linkUrl;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "is_visible", nullable = false)
    private boolean isVisible;

    @Builder
    private Banner(String title, String image, String linkUrl, int sortOrder, boolean isVisible) {
        this.title = title;
        this.image = image;
        this.linkUrl = linkUrl;
        this.sortOrder = sortOrder;
        this.isVisible = isVisible;
    }

    public void update(String title, String image, String linkUrl, int sortOrder, boolean isVisible) {
        this.title = title;
        this.image = image;
        this.linkUrl = linkUrl;
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
