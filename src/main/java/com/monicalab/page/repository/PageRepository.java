package com.monicalab.page.repository;

import com.monicalab.page.entity.CmsPage;
import com.monicalab.page.entity.PageType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PageRepository extends JpaRepository<CmsPage, Long> {

    boolean existsByPageType(PageType pageType);

    Optional<CmsPage> findByPageType(PageType pageType);
}
