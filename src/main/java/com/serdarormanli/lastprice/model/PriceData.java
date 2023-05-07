package com.serdarormanli.lastprice.model;

import java.time.Instant;

public record PriceData(String id, Instant asOf, Object data) {
}
