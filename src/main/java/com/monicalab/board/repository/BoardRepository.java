package com.monicalab.board.repository;

import com.monicalab.board.entity.Board;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, Long> {

    Page<Board> findByIsPublicTrue(Pageable pageable);

    Optional<Board> findByIdAndIsPublicTrue(Long id);
}
