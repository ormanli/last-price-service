package com.serdarormanli.lastprice.service;

import com.serdarormanli.lastprice.model.PriceData;
import lombok.Cleanup;
import lombok.SneakyThrows;
import net.jodah.concurrentunit.Waiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract sealed class ServiceBaseTest permits ServiceWithLockTest, ServiceWithoutLockTest {

    private Service service;

    protected abstract Service createInstance();

    @BeforeEach
    void setUp() {
        this.service = this.createInstance();
    }

    @Test
    void getDataNotExisting() {
        assertThat(this.service.getData("a")).isEmpty();
    }

    @SneakyThrows
    @Test
    void insertTwoBatchAndComplete() {
        @Cleanup("shutdown") var executorService = Executors.newFixedThreadPool(2);
        var countDownLatch = new CountDownLatch(2);

        var firstBatchID = this.service.startBatch();
        var secondBatchID = this.service.startBatch();

        var numOfRecordsPerAdd = 1_000;
        var numOfIterations = 1_000;

        BiFunction<Integer, Duration, Runnable> task = (batchID, amountToAdd) -> () -> {
            for (var i = 0; i < numOfIterations; i++) {
                var finalI = i;
                var batch = IntStream.range(0, numOfRecordsPerAdd)
                        .mapToObj(value -> new PriceData("%d_%d".formatted(finalI, value), Instant.now().plus(amountToAdd), "%d_%d_%d".formatted(batchID, finalI, value)))
                        .toList();
                this.service.addToBatch(batchID, batch);
            }
            countDownLatch.countDown();
        };

        executorService.submit(task.apply(firstBatchID, Duration.ofSeconds(10)));
        executorService.submit(task.apply(secondBatchID, Duration.ZERO));
        countDownLatch.await();

        var waiter = new Waiter();

        var finishTimes = new ConcurrentHashMap<Integer, Integer>();
        var finishTime = new AtomicInteger();

        executorService.submit(() -> {
            waiter.assertTrue(this.service.completeBatch(firstBatchID));
            finishTimes.put(firstBatchID, finishTime.getAndIncrement());
            waiter.resume();
        });
        executorService.submit(() -> {
            this.wait100Millisecond();
            waiter.assertTrue(this.service.completeBatch(secondBatchID));
            finishTimes.put(secondBatchID, finishTime.getAndIncrement());
            waiter.resume();
        });

        waiter.await(0, 2);

        for (var i = 0; i < numOfIterations; i++) {
            for (var j = 0; j < numOfRecordsPerAdd; j++) {
                assertThat(this.service.getData("%d_%d".formatted(i, j))).isNotEmpty().hasValue("%d_%d_%d".formatted(firstBatchID, i, j));
            }
        }

        assertThat(finishTimes.get(firstBatchID)).isLessThan(finishTimes.get(secondBatchID));
    }

    @SneakyThrows
    private void wait100Millisecond() {
        Thread.sleep(100);
    }

    private List<PriceData> createRecords(int numberOfRecords, int prefix, Duration duration) {
        return IntStream.range(0, numberOfRecords)
                .mapToObj(value -> new PriceData(Integer.toString(value), Instant.now().plus(duration), "%d_%d".formatted(prefix, value)))
                .toList();
    }

    @Test
    void moreThan1000Records() {
        assertThatThrownBy(() -> this.service.addToBatch(0, this.createRecords(1_001, 0, Duration.ZERO)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Batch is larger than allowed size 1000");
    }

    @Test
    void insertOneBatchTwoRunAndComplete() {
        var batchID = this.service.startBatch();

        var batch = this.createRecords(1_000, 0, Duration.ofSeconds(10));
        this.service.addToBatch(batchID, batch);
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }

        batch = this.createRecords(1_000, 1, Duration.ZERO);
        this.service.addToBatch(batchID, batch);
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }

        assertThat(this.service.completeBatch(batchID)).isTrue();
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isNotEmpty().hasValue("0_%d".formatted(i));
        }
    }

    @Test
    void insertOneBatchAndCancel() {
        var batchID = this.service.startBatch();

        var batch = this.createRecords(1_000, 0, Duration.ofSeconds(10));
        this.service.addToBatch(batchID, batch);
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }

        this.service.cancelBatch(batchID);
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }
    }

    @Test
    void cancelNonExistingBatch() {
        assertThat(this.service.cancelBatch(1_000_000)).isFalse();
    }

    @Test
    void completeNonExistingBatch() {
        assertThat(this.service.completeBatch(1_000_000)).isFalse();
    }

    @SneakyThrows
    @Test
    void insertOneBatchOneCancelOneComplete() {
        var batchID = this.service.startBatch();

        var batch = this.createRecords(1_000, 0, Duration.ofSeconds(10));
        this.service.addToBatch(batchID, batch);
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }

        @Cleanup("shutdown") var executorService = Executors.newFixedThreadPool(2);

        var waiter = new Waiter();

        executorService.submit(() -> {
            waiter.assertTrue(this.service.cancelBatch(batchID));
            waiter.resume();
        });
        executorService.submit(() -> {
            waiter.assertFalse(this.service.completeBatch(batchID));
            waiter.resume();
        });

        waiter.await(0, 2);

        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }
    }

    @SneakyThrows
    @Test
    void insertOneBatchOneCompleteOneCancel() {
        var batchID = this.service.startBatch();

        var batch = this.createRecords(1_000, 0, Duration.ZERO);
        this.service.addToBatch(batchID, batch);
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }

        @Cleanup("shutdown") var executorService = Executors.newFixedThreadPool(2);

        var waiter = new Waiter();

        executorService.submit(() -> {
            waiter.assertTrue(this.service.completeBatch(batchID));
            waiter.resume();
        });
        executorService.submit(() -> {
            waiter.assertFalse(this.service.cancelBatch(batchID));
            waiter.resume();
        });

        waiter.await(0, 2);

        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isNotEmpty().hasValue("0_%d".formatted(i));
        }
    }

    @SneakyThrows
    @Test
    void insertOneBatchTwoRunSimultaneouslyAndComplete() {
        var batchID = this.service.startBatch();

        var run1 = this.createRecords(1_000, 0, Duration.ofSeconds(10));
        var run2 = this.createRecords(1_000, 1, Duration.ZERO);

        @Cleanup("shutdown") var executorService = Executors.newFixedThreadPool(2);

        var waiter = new Waiter();

        executorService.submit(() -> {
            waiter.assertTrue(this.service.addToBatch(batchID, run1));
            waiter.resume();
        });
        executorService.submit(() -> {
            waiter.assertTrue(this.service.addToBatch(batchID, run2));
            waiter.resume();
        });

        waiter.await(0, 2);

        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isEmpty();
        }

        assertThat(this.service.completeBatch(batchID)).isTrue();
        for (var i = 0; i < 1_000; i++) {
            assertThat(this.service.getData(Integer.toString(i))).isNotEmpty().hasValue("0_%d".formatted(i));
        }
    }
}
