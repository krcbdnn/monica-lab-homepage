package com.monicalab.banner.repository;

import com.monicalab.banner.entity.Banner;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BannerRepository extends JpaRepository<Banner, Long> {

    List<Banner> findByIsVisibleTrue(Sort sort);
}
