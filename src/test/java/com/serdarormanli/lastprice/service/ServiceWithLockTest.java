package com.serdarormanli.lastprice.service;

final class ServiceWithLockTest extends ServiceBaseTest {

    @Override
    protected Service createInstance() {
        return new ServiceWithLock();
    }
}