package network;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Script kiểm thử tải (Stress Test) cho BidHub.
 * Giả lập nhiều người dùng đặt giá đồng thời vào cùng một phiên đấu giá.
 */
public class StressTest {
    private static final String SERVER_IP = "206.189.37.5";
    private static final int SERVER_PORT = 1234;
    private static final int NUM_CLIENTS = 20; // Số lượng người dùng giả lập
    private static final String TARGET_AUCTION_ID = "AUCTION_001"; // ID phiên đấu giá thử nghiệm
    private static final String START_USER_ID = "u-001"; // ID bắt đầu (Giả sử có u-001 đến u-020 trong DB)

    public static void main(String[] args) throws InterruptedException {
        System.out.println(">>> Bắt đầu Stress Test (SPAM MODE) với " + NUM_CLIENTS + " clients...");
        
        ExecutorService executor = Executors.newFixedThreadPool(NUM_CLIENTS);
        CountDownLatch latch = new CountDownLatch(NUM_CLIENTS);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        int bidsPerClient = 10;

        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= NUM_CLIENTS; i++) {
            final String userId = String.format("u-%03d", i);
            executor.submit(() -> {
                try (Socket socket = new Socket(SERVER_IP, SERVER_PORT);
                     ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                     ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                    
                    out.writeObject("IDENTIFY:" + userId);
                    out.flush();

                    for (int j = 0; j < bidsPerClient; j++) {
                        double bidAmount = 1000000 + (Math.random() * 5000000);
                        String bidCmd = String.format("BID:%s:%.0f:%s", TARGET_AUCTION_ID, bidAmount, userId);
                        
                        out.writeObject(bidCmd);
                        out.flush();

                        Object response = in.readObject();
                        if (response != null) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                        Thread.sleep(50); // Nghỉ 50ms giữa mỗi lần bid
                    }

                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("\n--- KẾT QUẢ STRESS TEST ---");
        System.out.println("Thời gian thực hiện: " + duration + " ms");
        System.out.println("Số yêu cầu thành công: " + successCount.get());
        System.out.println("Số yêu cầu thất bại: " + failCount.get());
        System.out.println("Tốc độ xử lý trung bình: " + (successCount.get() * 1000.0 / duration) + " req/s");
    }
}
