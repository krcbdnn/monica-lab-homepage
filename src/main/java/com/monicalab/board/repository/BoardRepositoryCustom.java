package com.monicalab.board.repository;

import com.monicalab.board.dto.BoardSearchCondition;
import com.monicalab.board.entity.Board;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface BoardRepositoryCustom {

    Page<Board> search(BoardSearchCondition condition, Pageable pageable);
}
