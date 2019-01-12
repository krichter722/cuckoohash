package com.github.mfondo;

import static org.junit.Assert.*;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class CuckooHashSetTest {

    private long cuckooHashAddTime = 0L;
    private long hashAddTime = 0L;
    private long cuckooHashRemoveTime = 0L;
    private long hashRemoveTime = 0L;

    @Test
    public void test1() {

        final CuckooHashSet.HashFunction<Integer> intHashFunction = new CuckooHashSet.HashFunction<Integer>() {
            @Override
            public int hash(Integer integer) {
                //from http://stackoverflow.com/questions/664014/what-integer-hash-function-are-good-that-accepts-an-integer-hash-key
                integer = ((integer >> 16) ^ integer) * 0x45d9f3b;
                integer = ((integer >> 16) ^ integer) * 0x45d9f3b;
                integer = ((integer >> 16) ^ integer);
                return integer;
            }
        };

        CuckooHashSet<Integer> cuckooSet = new CuckooHashSet<Integer>(Integer.class, 100, 0.9f, new CuckooHashSet.HashFunction<Integer>() {
            @Override
            public int hash(Integer integer) {
                return integer;
            }
        }, intHashFunction);
        runTests(cuckooSet);

        cuckooSet = new CuckooHashSet<Integer>(Integer.class, 100, 0.9f, intHashFunction);
        runTests(cuckooSet);
    }

    private void runTests(CuckooHashSet<Integer> cuckooSet) {
        final HashSet<Integer> hashSet = new HashSet<Integer>();

        assertAdd(cuckooSet, hashSet, 1);
        assertTrue(cuckooSet.contains(1));
        assertFalse(cuckooSet.contains(2));

        assertAdd(cuckooSet, hashSet, 2);
        assertTrue(cuckooSet.contains(1));
        assertTrue(cuckooSet.contains(2));
        assertFalse(cuckooSet.contains(3));

        assertAdd(cuckooSet, hashSet, 1);
        assertRemove(cuckooSet, hashSet, 1);

        assertClear(cuckooSet, hashSet);

        final int iterations = 1000;

        for(int i = 0; i < iterations; i++) {
            assertAdd(cuckooSet, hashSet, i);
        }
        for(int i = 0; i < iterations; i++) {
            assertRemove(cuckooSet, hashSet, i);
        }

        //above times are not very realistic
        clearTimes();

        for(int i = 0; i < iterations; i++) {
            if(Math.random() > 0.25f) {
                int rand = (int) Math.random() * Integer.MAX_VALUE;
                assertAdd(cuckooSet, hashSet, rand);
            } else {
                int rand;
                if(hashSet.isEmpty()) {
                    rand = (int) Math.random() * Integer.MAX_VALUE;
                } else {
                    rand = hashSet.iterator().next();
                }
                assertRemove(cuckooSet, hashSet, rand);
            }
        }

        for(int i = 0; i < iterations; i++) {
            int rand = (int) Math.random() * Integer.MAX_VALUE;
            assertAdd(cuckooSet, hashSet, rand);
        }
        while(!hashSet.isEmpty()) {
            int rand = hashSet.iterator().next();
            assertRemove(cuckooSet, hashSet, rand);
        }

        //just for info purposes - compare performance of HashSet vs CuckooHashSet
        System.out.println("Cuckoo Add Nanos:\t\t" + cuckooHashAddTime);
        System.out.println("HashSet Add Nanos:\t\t" + hashAddTime);
        System.out.println("Cuckoo Remove Nanos:\t" + cuckooHashRemoveTime);
        System.out.println("HashSet Remove Nanos:\t" + hashRemoveTime);
        System.out.println();
    }

    private void clearTimes() {
        cuckooHashAddTime = 0;
        hashAddTime = 0;
        cuckooHashRemoveTime = 0;
        hashRemoveTime = 0;
    }

    private void assertAdd(CuckooHashSet<Integer> cuckooSet, HashSet<Integer> hashSet, int i) {
        long start = System.nanoTime();
        cuckooSet.add(i);
        cuckooHashAddTime += System.nanoTime() - start;
        start = System.nanoTime();
        hashSet.add(i);
        hashAddTime += System.nanoTime() - start;
        assertEquals(cuckooSet, hashSet);
    }

    private void assertRemove(CuckooHashSet<Integer> cuckooSet, HashSet<Integer> hashSet, int i) {
        long start = System.nanoTime();
        cuckooSet.remove(i);
        cuckooHashRemoveTime += System.nanoTime() - start;
        start = System.nanoTime();
        hashSet.remove(i);
        hashRemoveTime += System.nanoTime() - start;
        assertEquals(cuckooSet, hashSet);
    }

    private void assertClear(Set<Integer> cuckooSet, Set<Integer> hashSet) {
        cuckooSet.clear();
        hashSet.clear();
        assertEquals(cuckooSet, hashSet);
    }
}
