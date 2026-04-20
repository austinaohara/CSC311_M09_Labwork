package ex2;

import java.util.concurrent.ThreadLocalRandom;

public class Cashier implements Runnable {
    public static final int END_OF_SHIFT = -1;

    private final int cashierId;
    private final CheckoutQueue checkoutQueue;
    private final CostcoSimulation.SimulationStats stats;
    private final int minCheckoutMillis;
    private final int maxCheckoutMillis;

    public Cashier(int cashierId, CheckoutQueue checkoutQueue, CostcoSimulation.SimulationStats stats,
            int minCheckoutMillis, int maxCheckoutMillis) {
        this.cashierId = cashierId;
        this.checkoutQueue = checkoutQueue;
        this.stats = stats;
        this.minCheckoutMillis = minCheckoutMillis;
        this.maxCheckoutMillis = maxCheckoutMillis;
    }

    @Override
    public void run() {
        try {
            while (true) {
                int customerId = checkoutQueue.nextCustomer();

                if (customerId == END_OF_SHIFT) {
                    if (CostcoSimulation.LOG_THREAD_EVENTS) {
                        System.out.printf("Cashier %d is closing their lane.%n", cashierId);
                    }
                    return;
                }

                long serviceStart = System.currentTimeMillis();
                long waitTime = stats.recordServiceStart(customerId, serviceStart);
                int checkoutTime = ThreadLocalRandom.current()
                        .nextInt(minCheckoutMillis, maxCheckoutMillis + 1);

                if (CostcoSimulation.LOG_THREAD_EVENTS) {
                    System.out.printf("Cashier %d started serving customer %d after %d ms waiting.%n",
                            cashierId, customerId, waitTime);
                }

                Thread.sleep(checkoutTime);

                stats.recordServiceComplete(customerId, cashierId, checkoutTime, System.currentTimeMillis());
                if (CostcoSimulation.LOG_THREAD_EVENTS) {
                    System.out.printf("Cashier %d finished customer %d in %d ms.%n",
                            cashierId, customerId, checkoutTime);
                }
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
