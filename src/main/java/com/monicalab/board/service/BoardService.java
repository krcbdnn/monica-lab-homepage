package com.monicalab.board.service;

import com.monicalab.board.dto.BoardRequest;
import com.monicalab.board.dto.BoardResponse;
import com.monicalab.board.entity.Board;
import com.monicalab.board.repository.BoardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BoardService {

    private final BoardRepository boardRepository;

    @Transactional
    public BoardResponse create(BoardRequest request) {
        Board board = Board.builder()
                .boardType(request.boardType())
                .title(request.title())
                .content(request.content())
                .thumbnail(request.thumbnail())
                .attachment(request.attachment())
                .viewCount(0)
                .isPublic(request.isPublic() != null ? request.isPublic() : false)
                .build();

        return BoardResponse.from(boardRepository.save(board));
    }
}
