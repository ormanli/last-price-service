package com.serdarormanli.sandp.service;

final class ServiceWithLockTest extends ServiceBaseTest {

    @Override
    protected Service createInstance() {
        return new ServiceWithLock();
    }
}