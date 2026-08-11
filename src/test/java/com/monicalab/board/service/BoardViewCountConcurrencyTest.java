package com.monicalab.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.monicalab.board.entity.Board;
import com.monicalab.board.entity.BoardType;
import com.monicalab.board.repository.BoardRepository;
import com.monicalab.support.AbstractIntegrationTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class BoardViewCountConcurrencyTest extends AbstractIntegrationTest {

    private static final int REQUEST_COUNT = 100;
    private static final long AWAIT_TIMEOUT_SECONDS = 30;

    @Autowired
    private BoardService boardService;

    @Autowired
    private BoardRepository boardRepository;

    @Test
    void concurrentPublicDetailRequestsIncreaseViewCountExactlyByRequestCount() throws InterruptedException {
        Board board = boardRepository.saveAndFlush(Board.builder()
                .boardType(BoardType.NOTICE)
                .title("동시성 조회수 테스트")
                .content("내용")
                .viewCount(0)
                .isPublic(true)
                .build());
        Long boardId = board.getId();

        ExecutorService executor = Executors.newFixedThreadPool(REQUEST_COUNT);
        CountDownLatch readyLatch = new CountDownLatch(REQUEST_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(REQUEST_COUNT);

        try {
            for (int i = 0; i < REQUEST_COUNT; i++) {
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        boolean started = startLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        if (started) {
                            boardService.getPublicById(boardId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            boolean allReady = readyLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(allReady).as("모든 요청 스레드가 제한 시간 내에 준비되지 못했습니다").isTrue();

            startLatch.countDown();

            boolean allDone = doneLatch.await(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertThat(allDone).as("모든 요청이 제한 시간 내에 끝나지 못했습니다").isTrue();
        } finally {
            executor.shutdownNow();
        }

        Board result = boardRepository.findById(boardId).orElseThrow();
        assertThat(result.getViewCount()).isEqualTo(REQUEST_COUNT);
    }
}
