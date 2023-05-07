package com.serdarormanli.sandp.model;

import java.time.Instant;

public record PriceData(String id, Instant asOf, Object data) {
}
