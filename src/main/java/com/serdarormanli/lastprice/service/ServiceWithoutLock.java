package com.serdarormanli.lastprice.service;

import com.serdarormanli.lastprice.model.PriceData;
import lombok.NonNull;
import org.jctools.maps.NonBlockingHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class ServiceWithoutLock implements Service {
    private final AtomicInteger lastBatchID = new AtomicInteger(1);
    private final AtomicReference<HashMap<String, DataHolder>> dataStorage = new AtomicReference<>(new HashMap<>());
    private final NonBlockingHashMap<Integer, ConcurrentLinkedQueue<PriceData>> batchStorage = new NonBlockingHashMap<>();

    @Override
    public Optional<Object> getData(@NonNull String id) {
        return Optional.ofNullable(this.dataStorage.get().get(id)).map(DataHolder::data);
    }

    @Override
    public Integer startBatch() {
        var newBatchID = this.lastBatchID.getAndIncrement();

        this.batchStorage.put(newBatchID, new ConcurrentLinkedQueue<>());

        return newBatchID;
    }

    @Override
    public boolean addToBatch(@NonNull Integer batchID, @NonNull List<PriceData> prices) {
        if (prices.size() > BATCH_SIZE) {
            throw new IllegalArgumentException("Batch is larger than allowed size %d".formatted(BATCH_SIZE));
        }

        return this.batchStorage.computeIfPresent(batchID, (key, batch) -> {
            batch.addAll(prices);

            return batch;
        }) != null;
    }

    @Override
    public boolean completeBatch(@NonNull Integer batchID) {
        var batch = this.batchStorage.remove(batchID);
        if (batch == null) {
            return false;
        }

        HashMap<String, DataHolder> currentData, newData;
        do {
            currentData = this.dataStorage.get();
            newData = new HashMap<>(currentData);

            var finalNewData = newData;
            batch.forEach((priceDataWithID) -> {
                finalNewData.merge(priceDataWithID.id(),
                        new DataHolder(priceDataWithID.asOf(), priceDataWithID.data()),
                        (d1, d2) -> {
                            if (d1.asOf().isAfter(d2.asOf())) {
                                return d1;
                            } else {
                                return d2;
                            }
                        });
            });
        } while (!this.dataStorage.compareAndSet(currentData, newData));

        return true;
    }

    @Override
    public boolean cancelBatch(@NonNull Integer batchID) {
        return this.batchStorage.remove(batchID) != null;
    }
}
