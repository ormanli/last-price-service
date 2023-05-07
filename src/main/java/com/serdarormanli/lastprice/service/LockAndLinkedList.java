package com.serdarormanli.sandp.service;

import com.serdarormanli.sandp.model.PriceData;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

record LockAndLinkedList(ReentrantLock lock, LinkedList<PriceData> list) {
}
