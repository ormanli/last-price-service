package com.serdarormanli.lastprice.service;

final class ServiceWithoutLockTest extends ServiceBaseTest {

    @Override
    protected Service createInstance() {
        return new ServiceWithoutLock();
    }
}