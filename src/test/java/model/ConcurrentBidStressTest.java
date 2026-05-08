package model;

import model.auction.Auction;
import model.auction.BidResult;
import model.auction.BidTransaction;
import model.item.Electronics;
import model.manager.AuctionManager;
import model.manager.ConcurrentBidManager;
import model.user.Bidder;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * STRESS TEST cho Concurrent Bidding.
 */
@DisplayName("ConcurrentBidManager – Stress test đa luồng")
class ConcurrentBidStressTest {

    private static final double STARTING_PRICE = 100.0;
    private Auction auction;

    @BeforeEach
    void setUp() {
        // Reset Singleton state giữa các test (tránh pollution)
        AuctionManager.getInstance().clearAll();
        ConcurrentBidManager.getInstance().resetMetrics();

        auction = new Auction(
                "AUC_STRESS_" + System.nanoTime(),
                new Electronics("ITM_1", "Laptop", "Test", STARTING_PRICE, "Brand"),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );
        auction.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction);
    }

    @AfterEach
    void tearDown() {
        AuctionManager.getInstance().clearAll();
        ConcurrentBidManager.getInstance().releaseLock(auction.getAuctionId());
    }

    // ===================================================================
    // TEST 1 — TIE-AMOUNT RACE
    // ===================================================================
    @Test
    @DisplayName("Tie race: 50 thread bid cùng giá $200 → đúng 1 SUCCESS, 49 OUTBID")
    void tieAmountRace_exactlyOneWinner() throws InterruptedException {
        int N = 50;
        double tieAmount = 200.0;

        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);
        ConcurrentLinkedQueue<BidResult> results = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < N; i++) {
            final Bidder b = new Bidder("U" + i, "user" + i, "u" + i + "@t", "p");
            pool.submit(() -> {
                try {
                    start.await();
                    BidResult r = ConcurrentBidManager.getInstance()
                            .processBid(auction.getAuctionId(), tieAmount, b);
                    results.add(r);
                } catch (Exception e) {
                    fail("Unexpected: " + e);
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS), "Test timeout");
        pool.shutdown();

        long successes = results.stream().filter(BidResult::isSuccess).count();
        long outbids   = results.stream().filter(BidResult::isOutbid).count();

        assertEquals(1, successes,  "Phải có ĐÚNG 1 người thắng khi tie-amount");
        assertEquals(N - 1, outbids, "Tất cả thread còn lại phải nhận OUTBID");
        assertEquals(tieAmount, auction.getCurrentPrice(), 0.001);
    }

    // ===================================================================
    // TEST 2 — STRICT MONOTONIC (dãy giá trong history tăng nghiêm ngặt)
    // ===================================================================
    @Test
    @DisplayName("Strict monotonic: bidHistory phải tăng nghiêm ngặt sau N bid đa luồng")
    void bidHistory_strictlyIncreasing() throws InterruptedException {
        int N = 30;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);

        for (int i = 0; i < N; i++) {
            final double amount = STARTING_PRICE + 1 + Math.random() * 1000;
            final Bidder b = new Bidder("U" + i, "user" + i, "u" + i + "@t", "p");
            pool.submit(() -> {
                try {
                    start.await();
                    ConcurrentBidManager.getInstance()
                            .processBid(auction.getAuctionId(), amount, b);
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        List<BidTransaction> history = auction.getBidHistory();
        for (int i = 1; i < history.size(); i++) {
            double prev = history.get(i - 1).getBidAmount();
            double curr = history.get(i).getBidAmount();
            assertTrue(curr > prev,
                    String.format("Giá phải tăng nghiêm ngặt: history[%d]=%.2f >= history[%d]=%.2f",
                            i - 1, prev, i, curr));
        }

        if (!history.isEmpty()) {
            assertEquals(history.get(history.size() - 1).getBidAmount(),
                    auction.getCurrentPrice(), 0.001);
        }
    }

    // ===================================================================
    // TEST 3 — SUSTAINED LOAD: N bidder × M vòng bid
    // ===================================================================
    @Test
    @DisplayName("Sustained: 10 bidder × 20 vòng = 200 bid → giá cuối = max của các SUCCESS")
    void sustainedLoad_finalPriceEqualsMax() throws InterruptedException {
        int BIDDERS = 10;
        int ROUNDS  = 20;

        ExecutorService pool = Executors.newFixedThreadPool(BIDDERS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(BIDDERS);
        ConcurrentLinkedQueue<Double> successAmounts = new ConcurrentLinkedQueue<>();

        for (int b = 0; b < BIDDERS; b++) {
            final Bidder bidder = new Bidder("U" + b, "user" + b, "u" + b + "@t", "p");
            pool.submit(() -> {
                try {
                    start.await();
                    for (int r = 0; r < ROUNDS; r++) {
                        double amount = STARTING_PRICE + 1 + Math.random() * 5000;
                        BidResult res = ConcurrentBidManager.getInstance()
                                .processBid(auction.getAuctionId(), amount, bidder);
                        if (res.isSuccess()) successAmounts.add(amount);
                    }
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS));
        pool.shutdown();

        double maxSuccess = successAmounts.stream().mapToDouble(Double::doubleValue)
                .max().orElse(STARTING_PRICE);
        assertEquals(maxSuccess, auction.getCurrentPrice(), 0.001,
                "Giá cuối phải = giá MAX trong các SUCCESS bid");

        assertEquals(successAmounts.size(),
                ConcurrentBidManager.getInstance().getSuccessCount());
    }

    // ===================================================================
    // TEST 4 — PER-AUCTION ISOLATION: 2 phiên không block lẫn nhau
    // ===================================================================
    @Test
    @DisplayName("Per-auction isolation: bid trên 2 phiên khác nhau chạy song song")
    void perAuctionLock_twoAuctionsDoNotBlock() throws InterruptedException {
        Auction auction2 = new Auction(
                "AUC_STRESS_2_" + System.nanoTime(),
                new Electronics("ITM_2", "Phone", "Test", STARTING_PRICE, "Brand"),
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusHours(1)
        );
        auction2.setStatus("RUNNING");
        AuctionManager.getInstance().addAuction(auction2);

        int N = 20;
        ExecutorService pool = Executors.newFixedThreadPool(N * 2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N * 2);

        for (int i = 0; i < N; i++) {
            final double a1 = STARTING_PRICE + 1 + Math.random() * 100;
            final double a2 = STARTING_PRICE + 1 + Math.random() * 100;
            final Bidder b1 = new Bidder("A" + i, "u_a" + i, "a" + i + "@t", "p");
            final Bidder b2 = new Bidder("B" + i, "u_b" + i, "b" + i + "@t", "p");

            pool.submit(() -> {
                try { start.await();
                    ConcurrentBidManager.getInstance()
                            .processBid(auction.getAuctionId(), a1, b1);
                } catch (Exception ignored) { } finally { done.countDown(); }
            });
            pool.submit(() -> {
                try { start.await();
                    ConcurrentBidManager.getInstance()
                            .processBid(auction2.getAuctionId(), a2, b2);
                } catch (Exception ignored) { } finally { done.countDown(); }
            });
        }

        long t0 = System.nanoTime();
        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();

        System.out.println(">>> [TEST] 40 bid trên 2 phiên xong trong " + elapsedMs + "ms");

        assertTrue(auction.getCurrentPrice()  > STARTING_PRICE);
        assertTrue(auction2.getCurrentPrice() > STARTING_PRICE);

        ConcurrentBidManager.getInstance().releaseLock(auction2.getAuctionId());
    }

    // ===================================================================
    // TEST 5 — NO LOST UPDATE
    // ===================================================================
    @Test
    @DisplayName("No lost update: SUCCESS + OUTBID + FAILURE = tổng số request")
    void noLostUpdate_metricsConsistent() throws InterruptedException {
        int N = 100;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);
        AtomicInteger totalReturned = new AtomicInteger(0);

        for (int i = 0; i < N; i++) {
            final double amount = STARTING_PRICE + Math.random() * 200;
            final Bidder b = new Bidder("U" + i, "user" + i, "u" + i + "@t", "p");
            pool.submit(() -> {
                try {
                    start.await();
                    BidResult r = ConcurrentBidManager.getInstance()
                            .processBid(auction.getAuctionId(), amount, b);
                    if (r != null) totalReturned.incrementAndGet();
                } catch (Exception ignored) {
                } finally { done.countDown(); }
            });
        }

        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();

        ConcurrentBidManager mgr = ConcurrentBidManager.getInstance();
        long total = mgr.getSuccessCount() + mgr.getOutbidCount() + mgr.getFailureCount();

        assertEquals(N, totalReturned.get(),  "Mọi request phải có response");
        assertEquals(N, total, "Tổng metrics phải = N (không lost update)");
        System.out.println(">>> [TEST] " + mgr.metricsSummary());
    }

    // ===================================================================
    // TEST 6 — DEMO: log thread interleaving
    // ===================================================================
    @Test
    @DisplayName("Demo: 5 thread bid + log timestamp để chứng minh chạy song song")
    void demo_visibleInterleaving() throws InterruptedException {
        int N = 5;
        ExecutorService pool = Executors.newFixedThreadPool(N);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);
        ConcurrentLinkedQueue<String> log = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < N; i++) {
            final double amount = 100 + (i + 1) * 50; // 150, 200, 250, 300, 350
            final Bidder b = new Bidder("U" + i, "bidder" + i, "u" + i + "@t", "p");
            pool.submit(() -> {
                try {
                    start.await();
                    long t = System.nanoTime();
                    BidResult r = ConcurrentBidManager.getInstance()
                            .processBid(auction.getAuctionId(), amount, b);
                    log.add(String.format("[%s] t=%d %s $%.2f → %s",
                            Thread.currentThread().getName(), t,
                            b.getUsername(), amount, r.getStatus()));
                } catch (Exception e) { fail(e); } finally { done.countDown(); }
            });
        }

        start.countDown();
        assertTrue(done.await(10, TimeUnit.SECONDS));
        pool.shutdown();

        System.out.println(">>> ===== INTERLEAVING LOG (sorted by timestamp) =====");
        log.stream().sorted().forEach(System.out::println);
        System.out.println(">>> Final price = $" + auction.getCurrentPrice());
        System.out.println(">>> History size = " + auction.getBidHistory().size());
    }
}
