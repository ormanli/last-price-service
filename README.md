# Last value price service #

### Requirements ###

* Java 17+
* Maven 3

## Usage/Examples

### How to initialize

```java
Service service = new ServiceWithLock();
Service service = new ServiceWithoutLock();
```

### How to get

```java
Optional<Object> data = service.getData("A");
```

### How to start batch

```java
Integer batchID = service.startBatch();
```

### How to add price data to batch

```java
PriceData pricingData = new PriceData("A",Instant.now(),100);
service.addToBatch(batchID,List.of(pricingData));
```

### How to complete or cancel batch

```java
service.completeBatch(batchID);
service.cancelBatch(batchID);
```

## Info

* There are two implementations. `ServiceWithLock` and `ServiceWithoutLock`. I am running the same test suite on them to
  verify that they have the same behaviour.
* `ServiceWithLock` is using two different locks to make sure that batch and complete are synchronized. First lock is
  commit lock that ensures only one batch is completed at any time. Second lock is for batch that ensures only one
  producer can add data to a specific batch.
* `ServiceWithoutLock` is using `AtomicReference` and `ConcurrentLinkedQueue`. In `completeBatch`, it is using
  stamp to update the main data. If `completeBatch` method called for two different batch, `AtomicReference` will
  ensure main data will be updated without overriding since stamp will be updated. `ConcurrentLinkedQueue` is used to
  allow different producers to add data to the same batch without locking.