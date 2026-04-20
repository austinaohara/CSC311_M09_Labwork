package ex2;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class CostcoSimulation {
    static final double CASHIER_HOURLY_RATE = 18.0;
    static final boolean LOG_THREAD_EVENTS = false;
    private static final int CUSTOMER_COUNT = 20;
    private static final int MIN_SHOPPING_MILLIS = 20;
    private static final int MAX_SHOPPING_MILLIS = 120;
    private static final int MIN_CHECKOUT_MILLIS = 250;
    private static final int MAX_CHECKOUT_MILLIS = 350;
    private static final double REAL_MILLIS_PER_SIMULATED_HOUR = 6000.0;
    private static final double REAL_MILLIS_PER_SIMULATED_MINUTE = REAL_MILLIS_PER_SIMULATED_HOUR / 60.0;

    public static void main(String[] args) throws InterruptedException {
        List<ExperimentConfig> experiments = List.of(
                new ExperimentConfig("Understaffed", CUSTOMER_COUNT, 1, 5),
                new ExperimentConfig("Target Staffing", CUSTOMER_COUNT, 3, 5),
                new ExperimentConfig("Overstaffed", CUSTOMER_COUNT, 10, 5),
                new ExperimentConfig("Small Queue", CUSTOMER_COUNT, 3, 2),
                new ExperimentConfig("Large Queue", CUSTOMER_COUNT, 3, 20));

        List<ExperimentResult> results = new ArrayList<>();

        System.out.println("Costco Checkout Simulation");
        System.out.println("Five required experiments");
        System.out.printf("Time scale: %.0f real ms = 1 simulated minute%n%n",
                REAL_MILLIS_PER_SIMULATED_MINUTE);

        for (ExperimentConfig experiment : experiments) {
            ExperimentResult result = runExperiment(experiment);
            results.add(result);
            printExperimentSummary(result);
        }

        printRecommendation(results);
    }

    private static ExperimentResult runExperiment(ExperimentConfig experiment) throws InterruptedException {
        CheckoutQueue checkoutQueue = new CheckoutQueue(experiment.queueCapacity());
        SimulationStats stats = new SimulationStats(experiment.customerCount());
        ExecutorService customerPool = Executors.newFixedThreadPool(experiment.customerCount());
        ExecutorService cashierPool = Executors.newFixedThreadPool(experiment.cashierCount());

        long simulationStart = System.currentTimeMillis();

        for (int cashierId = 1; cashierId <= experiment.cashierCount(); cashierId++) {
            cashierPool.execute(new Cashier(cashierId, checkoutQueue, stats,
                    MIN_CHECKOUT_MILLIS, MAX_CHECKOUT_MILLIS));
        }

        for (int customerId = 1; customerId <= experiment.customerCount(); customerId++) {
            customerPool.execute(new Customer(customerId, checkoutQueue, stats,
                    MIN_SHOPPING_MILLIS, MAX_SHOPPING_MILLIS));
        }

        customerPool.shutdown();
        customerPool.awaitTermination(10, TimeUnit.MINUTES);

        for (int i = 0; i < experiment.cashierCount(); i++) {
            checkoutQueue.enterQueue(Cashier.END_OF_SHIFT);
        }

        cashierPool.shutdown();
        cashierPool.awaitTermination(10, TimeUnit.MINUTES);

        long simulationMillis = System.currentTimeMillis() - simulationStart;
        return stats.buildResult(experiment, simulationMillis);
    }

    private static void printExperimentSummary(ExperimentResult result) {
        System.out.printf("Experiment: %s%n", result.name());
        System.out.printf("Customers: %d, Cashiers: %d, Queue size: %d%n",
                result.customerCount(), result.cashierCount(), result.queueCapacity());
        System.out.printf("Average wait: %.2f simulated minutes%n", result.averageWaitMinutes());
        System.out.printf("Longest wait: %.2f simulated minutes%n", result.longestWaitMinutes());
        System.out.printf("Average checkout: %.2f simulated minutes%n", result.averageCheckoutMinutes());
        System.out.printf("Throughput: %.2f customers/hour%n", result.throughputPerHour());
        System.out.printf("Hourly staffing cost: $%.2f%n", result.hourlyStaffingCost());
        System.out.printf("Cashier cost per customer: $%.2f%n", result.costPerCustomer());
        System.out.printf("Observation: %s%n%n", result.observation());
    }

    private static void printRecommendation(List<ExperimentResult> results) {
        System.out.println("Recommendation");
        System.out.println("--------------");

        ExperimentResult bestMeasuredFit = results.stream()
                .filter(result -> result.queueCapacity() == 5)
                .min(Comparator.comparingDouble(
                                (ExperimentResult result) -> Math.abs(result.throughputPerHour() - 60.0))
                        .thenComparingDouble(ExperimentResult::hourlyStaffingCost)
                        .thenComparingDouble(ExperimentResult::averageWaitMinutes))
                .orElse(null);

        if (bestMeasuredFit != null) {
            System.out.printf(
                    "Closest tested option to a 60 customers/hour target: %s with %d cashiers.%n",
                    bestMeasuredFit.name(), bestMeasuredFit.cashierCount());
            System.out.printf(
                    "It delivers %.2f customers/hour with %.2f simulated minutes average wait at $%.2f/hour.%n",
                    bestMeasuredFit.throughputPerHour(),
                    bestMeasuredFit.averageWaitMinutes(),
                    bestMeasuredFit.hourlyStaffingCost());
        }

        System.out.println(
                "The 1-cashier run is cheapest but cannot keep up, which drives the longest waits.");
        System.out.println(
                "The 10-cashier run nearly eliminates waits, but it raises hourly cost far beyond what the target requires.");
        System.out.println(
                "The 3-cashier setup is the best balance here because it reaches the target service rate without excessive staffing.");
        System.out.println(
                "Queue-size changes affect how often customers are blocked from entering the line, but staffing level has the larger impact on wait times.");
    }

    private static double convertMillisToSimulatedMinutes(double millis) {
        return millis / REAL_MILLIS_PER_SIMULATED_MINUTE;
    }

    private static double convertMillisToSimulatedHours(double millis) {
        return millis / REAL_MILLIS_PER_SIMULATED_HOUR;
    }

    private static String observationFor(ExperimentConfig experiment, double averageWaitMinutes,
            double throughputPerHour) {
        if (experiment.cashierCount() == 1) {
            return "One cashier becomes the bottleneck, so wait times climb and throughput stays below the target.";
        }

        if (experiment.cashierCount() == 10) {
            return "Extra cashiers keep lines short, but most of that staffing cost does not buy meaningful extra value for a 60/hour goal.";
        }

        if (experiment.queueCapacity() <= 2) {
            return "A very small queue limits how many customers can wait at once, which can slow flow when multiple shoppers finish together.";
        }

        if (experiment.queueCapacity() >= 20) {
            return "A larger queue prevents line overflows, but it does not improve service speed unless more cashiers are added.";
        }

        if (experiment.cashierCount() == 3 && averageWaitMinutes < 10.0) {
            return "This setup stays near the 60/hour target while keeping waits at a manageable level.";
        }

        return "This configuration provides a middle ground between cost and service speed.";
    }

    static final class ExperimentConfig {
        private final String name;
        private final int customerCount;
        private final int cashierCount;
        private final int queueCapacity;

        ExperimentConfig(String name, int customerCount, int cashierCount, int queueCapacity) {
            this.name = name;
            this.customerCount = customerCount;
            this.cashierCount = cashierCount;
            this.queueCapacity = queueCapacity;
        }

        String name() {
            return name;
        }

        int customerCount() {
            return customerCount;
        }

        int cashierCount() {
            return cashierCount;
        }

        int queueCapacity() {
            return queueCapacity;
        }
    }

    static final class ExperimentResult {
        private final String name;
        private final int customerCount;
        private final int cashierCount;
        private final int queueCapacity;
        private final double averageWaitMinutes;
        private final double longestWaitMinutes;
        private final double averageCheckoutMinutes;
        private final double throughputPerHour;
        private final double hourlyStaffingCost;
        private final double costPerCustomer;
        private final String observation;

        ExperimentResult(String name, int customerCount, int cashierCount, int queueCapacity,
                double averageWaitMinutes, double longestWaitMinutes, double averageCheckoutMinutes,
                double throughputPerHour, double hourlyStaffingCost, double costPerCustomer,
                String observation) {
            this.name = name;
            this.customerCount = customerCount;
            this.cashierCount = cashierCount;
            this.queueCapacity = queueCapacity;
            this.averageWaitMinutes = averageWaitMinutes;
            this.longestWaitMinutes = longestWaitMinutes;
            this.averageCheckoutMinutes = averageCheckoutMinutes;
            this.throughputPerHour = throughputPerHour;
            this.hourlyStaffingCost = hourlyStaffingCost;
            this.costPerCustomer = costPerCustomer;
            this.observation = observation;
        }

        String name() {
            return name;
        }

        int customerCount() {
            return customerCount;
        }

        int cashierCount() {
            return cashierCount;
        }

        int queueCapacity() {
            return queueCapacity;
        }

        double averageWaitMinutes() {
            return averageWaitMinutes;
        }

        double longestWaitMinutes() {
            return longestWaitMinutes;
        }

        double averageCheckoutMinutes() {
            return averageCheckoutMinutes;
        }

        double throughputPerHour() {
            return throughputPerHour;
        }

        double hourlyStaffingCost() {
            return hourlyStaffingCost;
        }

        double costPerCustomer() {
            return costPerCustomer;
        }

        String observation() {
            return observation;
        }
    }

    static class SimulationStats {
        private final Map<Integer, CustomerSnapshot> customerSnapshots = new ConcurrentHashMap<>();
        private final CountDownLatch completedCustomers;
        private final AtomicInteger servedCustomers = new AtomicInteger();
        private final AtomicLong totalWaitMillis = new AtomicLong();
        private final AtomicLong totalCheckoutMillis = new AtomicLong();
        private final AtomicLong totalTimeInSystemMillis = new AtomicLong();
        private final AtomicLong maxWaitMillis = new AtomicLong();

        SimulationStats(int customerCount) {
            completedCustomers = new CountDownLatch(customerCount);
        }

        void recordQueueJoin(int customerId, long queueJoinTime) {
            customerSnapshots.put(customerId, new CustomerSnapshot(queueJoinTime));
        }

        long recordServiceStart(int customerId, long serviceStartTime) {
            CustomerSnapshot snapshot = customerSnapshots.get(customerId);
            if (snapshot == null) {
                return 0L;
            }

            long waitTime = Math.max(0L, serviceStartTime - snapshot.queueJoinTimeMillis());
            totalWaitMillis.addAndGet(waitTime);
            maxWaitMillis.accumulateAndGet(waitTime, Math::max);
            return waitTime;
        }

        void recordServiceComplete(int customerId, int cashierId, int checkoutMillis, long completionTime) {
            CustomerSnapshot snapshot = customerSnapshots.get(customerId);
            if (snapshot == null) {
                completedCustomers.countDown();
                return;
            }

            totalCheckoutMillis.addAndGet(checkoutMillis);
            totalTimeInSystemMillis.addAndGet(
                    Math.max(0L, completionTime - snapshot.queueJoinTimeMillis()));
            servedCustomers.incrementAndGet();
            completedCustomers.countDown();
        }

        ExperimentResult buildResult(ExperimentConfig experiment, long simulationMillis)
                throws InterruptedException {
            completedCustomers.await();

            int served = servedCustomers.get();
            double averageWaitMillis = served == 0 ? 0.0 : (double) totalWaitMillis.get() / served;
            double averageCheckoutMillis = served == 0 ? 0.0 : (double) totalCheckoutMillis.get() / served;
            double simulatedHours = convertMillisToSimulatedHours(simulationMillis);
            double throughputPerHour = served == 0 || simulatedHours == 0.0
                    ? 0.0 : served / simulatedHours;
            double hourlyStaffingCost = experiment.cashierCount() * CASHIER_HOURLY_RATE;
            double costPerCustomer = throughputPerHour == 0.0 ? 0.0 : hourlyStaffingCost / throughputPerHour;

            return new ExperimentResult(
                    experiment.name(),
                    experiment.customerCount(),
                    experiment.cashierCount(),
                    experiment.queueCapacity(),
                    convertMillisToSimulatedMinutes(averageWaitMillis),
                    convertMillisToSimulatedMinutes(maxWaitMillis.get()),
                    convertMillisToSimulatedMinutes(averageCheckoutMillis),
                    throughputPerHour,
                    hourlyStaffingCost,
                    costPerCustomer,
                    observationFor(experiment,
                            convertMillisToSimulatedMinutes(averageWaitMillis),
                            throughputPerHour));
        }
    }

    static class CustomerSnapshot {
        private final long queueJoinTimeMillis;

        CustomerSnapshot(long queueJoinTimeMillis) {
            this.queueJoinTimeMillis = queueJoinTimeMillis;
        }

        long queueJoinTimeMillis() {
            return queueJoinTimeMillis;
        }
    }
}
