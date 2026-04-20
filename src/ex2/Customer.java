package ex2;

import java.util.concurrent.ThreadLocalRandom;

public class Customer implements Runnable {
    private final int customerId;
    private final CheckoutQueue checkoutQueue;
    private final CostcoSimulation.SimulationStats stats;
    private final int minShoppingMillis;
    private final int maxShoppingMillis;

    public Customer(int customerId, CheckoutQueue checkoutQueue, CostcoSimulation.SimulationStats stats,
                    int minShoppingMillis, int maxShoppingMillis) {
        this.customerId = customerId;
        this.checkoutQueue = checkoutQueue;
        this.stats = stats;
        this.minShoppingMillis = minShoppingMillis;
        this.maxShoppingMillis = maxShoppingMillis;
    }

    @Override
    public void run() {
        try {
            int shoppingTime = ThreadLocalRandom.current()
                    .nextInt(minShoppingMillis, maxShoppingMillis + 1);
            Thread.sleep(shoppingTime);

            long queueJoinTime = System.currentTimeMillis();
            stats.recordQueueJoin(customerId, queueJoinTime);
            checkoutQueue.enterQueue(customerId);

            if (CostcoSimulation.LOG_THREAD_EVENTS) {
                System.out.printf("Customer %d finished shopping in %d ms and joined the line. Queue size: %d%n",
                        customerId, shoppingTime, checkoutQueue.size());
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}