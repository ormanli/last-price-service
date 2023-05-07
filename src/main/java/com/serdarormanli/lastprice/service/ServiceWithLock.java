package com.serdarormanli.lastprice.service;

import com.serdarormanli.lastprice.model.PriceData;
import lombok.NonNull;
import org.jctools.maps.NonBlockingHashMap;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public final class ServiceWithLock implements Service {
    private final AtomicInteger lastBatchID = new AtomicInteger(1);
    private final ReentrantLock commitLock = new ReentrantLock();
    private final AtomicReference<HashMap<String, DataHolder>> dataStorage = new AtomicReference<>(new HashMap<>());
    private final NonBlockingHashMap<Integer, LockAndLinkedList> batchStorage = new NonBlockingHashMap<>();

    @Override
    public Optional<Object> getData(@NonNull String id) {
        return Optional.ofNullable(this.dataStorage.get().get(id)).map(DataHolder::data);
    }

    @Override
    public Integer startBatch() {
        var newBatchID = this.lastBatchID.getAndIncrement();

        this.batchStorage.put(newBatchID, new LockAndLinkedList(new ReentrantLock(), new LinkedList<>()));

        return newBatchID;
    }

    @Override
    public boolean addToBatch(@NonNull Integer batchID, @NonNull List<PriceData> prices) {
        if (prices.size() > BATCH_SIZE) {
            throw new IllegalArgumentException("Batch is larger than allowed size %d".formatted(BATCH_SIZE));
        }

        var lockAndLinkedList = this.batchStorage.get(batchID);
        if (lockAndLinkedList == null) {
            return false;
        }

        lockAndLinkedList.lock().lock();
        lockAndLinkedList.list().addAll(prices);
        lockAndLinkedList.lock().unlock();

        return true;
    }

    @Override
    public boolean completeBatch(@NonNull Integer batchID) {
        var lockAndLinkedList = this.batchStorage.remove(batchID);
        if (lockAndLinkedList == null) {
            return false;
        }

        try {
            lockAndLinkedList.lock().lock();
            try {
                this.commitLock.lock();

                var currentData = this.dataStorage.get();
                var newData = new HashMap<>(currentData);

                lockAndLinkedList.list().forEach((priceDataWithID) -> {
                    newData.merge(priceDataWithID.id(),
                            new DataHolder(priceDataWithID.asOf(), priceDataWithID.data()),
                            (d1, d2) -> {
                                if (d1.asOf().isAfter(d2.asOf())) {
                                    return d1;
                                } else {
                                    return d2;
                                }
                            });
                });

                this.dataStorage.compareAndSet(currentData, newData);

                return true;
            } finally {
                this.commitLock.unlock();
            }
        } finally {
            lockAndLinkedList.lock().unlock();
        }
    }

    @Override
    public boolean cancelBatch(@NonNull Integer batchID) {
        return this.batchStorage.remove(batchID) != null;
    }
}
