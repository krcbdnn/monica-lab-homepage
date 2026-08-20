package com.monicalab.board.repository;

import com.monicalab.board.entity.Board;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoardRepository extends JpaRepository<Board, Long>, BoardRepositoryCustom {

    Optional<Board> findByIdAndIsPublicTrue(Long id);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Board b SET b.viewCount = b.viewCount + 1 WHERE b.id = :id")
    int increaseViewCount(@Param("id") Long id);
}
