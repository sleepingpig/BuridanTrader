package com.buridantrader;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.Semaphore;

@ThreadSafe
public class TradingPlanner {
    // TODO Make it configurable
    private static final long COOL_DOWN_MS = 1000 * 60 * 10;
    private final PlanProducer planProducer;
    private final System system;
    private Semaphore semaphore = new Semaphore(1);
    private Instant lastPlanTime = null;

    public TradingPlanner(
            @Nonnull PlanProducer planProducer,
            @Nonnull System system) {
        this.planProducer = planProducer;
        this.system = system;
    }

    @Nonnull
    public TradingPlan nextPlan() throws IOException, InterruptedException {
        semaphore.acquire();
        try {
            waitUntilReadyToMakePlan();
            TradingPlan plan = planProducer.get();
            lastPlanTime = Instant.ofEpochMilli(system.currentTimeMillis());
            return plan;
        } finally {
            semaphore.release();
        }
    }

    private void waitUntilReadyToMakePlan() throws InterruptedException {
        long remainingMs = remainingMsToMakePlan();
        if (remainingMs > 0) {
            system.sleep(remainingMs);
        }
    }

    @Nonnegative
    private long remainingMsToMakePlan() {
        if (lastPlanTime == null) {
            return 0;
        }
        return Math.max(system.currentTimeMillis() - lastPlanTime.toEpochMilli() + COOL_DOWN_MS, 0);
    }
}
