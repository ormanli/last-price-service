package com.serdarormanli.sandp.service;

import com.serdarormanli.sandp.model.PriceData;

import java.util.List;
import java.util.Optional;

public interface Service {
    int BATCH_SIZE = 1_000;

    Optional<Object> getData(String id);

    Integer startBatch();

    boolean addToBatch(Integer batchID, List<PriceData> prices);

    boolean completeBatch(Integer batchID);

    boolean cancelBatch(Integer batchID);
}
