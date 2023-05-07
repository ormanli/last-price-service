package com.serdarormanli.lastprice.service;

import com.serdarormanli.lastprice.model.PriceData;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

record LockAndLinkedList(ReentrantLock lock, LinkedList<PriceData> list) {
}
