package com.serdarormanli.sandp.service;

final class ServiceWithoutLockTest extends ServiceBaseTest {

    @Override
    protected Service createInstance() {
        return new ServiceWithoutLock();
    }
}