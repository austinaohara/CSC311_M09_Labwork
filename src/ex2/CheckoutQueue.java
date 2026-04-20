package ex2;

import java.util.concurrent.ArrayBlockingQueue;

public class CheckoutQueue {
    private final ArrayBlockingQueue<Integer> queue;

    public CheckoutQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Queue capacity must be positive.");
        }

        queue = new ArrayBlockingQueue<>(capacity);
    }

    public void enterQueue(int customerId) throws InterruptedException {
        queue.put(customerId);
    }

    public int nextCustomer() throws InterruptedException {
        return queue.take();
    }

    public int size() {
        return queue.size();
    }

    public int capacity() {
        return queue.remainingCapacity() + queue.size();
    }
}
