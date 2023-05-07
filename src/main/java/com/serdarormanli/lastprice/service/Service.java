package com.serdarormanli.lastprice.service;

import com.serdarormanli.lastprice.model.PriceData;

import java.util.List;
import java.util.Optional;

public sealed interface Service permits ServiceWithLock, ServiceWithoutLock {
    int BATCH_SIZE = 1_000;

    Optional<Object> getData(String id);

    Integer startBatch();

    boolean addToBatch(Integer batchID, List<PriceData> prices);

    boolean completeBatch(Integer batchID);

    boolean cancelBatch(Integer batchID);
}
