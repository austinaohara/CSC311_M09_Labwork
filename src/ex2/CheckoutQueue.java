package ex2;

import java.util.concurrent.ArrayBlockingQueue;

public class CheckoutQueue {
    // Shared buffer representing the checkout line
    private final ArrayBlockingQueue<Integer> queue;

    public CheckoutQueue(int capacity) {
        queue = new ArrayBlockingQueue<Integer>(capacity);
    }

    // Customer joins the queue
    public void enqueueCustomer(int customerId) throws InterruptedException {
        queue.put(customerId);
        System.out.printf("Customer %2d joins the line.\tCustomers in queue: %d%n",
                customerId, queue.size());
    }

    // Cashier serves the customer
    public int serveCustomer() throws InterruptedException {
        int customerId = queue.take();
        System.out.printf("Cashier serves Customer %2d.\tCustomers in queue: %d%n",
                customerId, queue.size());
        return customerId;
    }
}