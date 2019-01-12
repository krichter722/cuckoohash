package com.github.mfondo;

import com.google.common.collect.AbstractIterator;

import java.lang.reflect.Array;
import java.util.AbstractSet;
import java.util.Iterator;

/**
 * http://en.wikipedia.org/wiki/Cuckoo_hashing
 * Implementation of http://www.cs.tau.ac.il/~shanir/advanced-seminar-data-structures-2007/bib/pagh01cuckoo.pdf
 */
public class CuckooHashSet<T> extends AbstractSet<T> {

    private static final int DEFAULT_INITIAL_SIZE = 16;

    private static final int UPPER_BITS_MASK = 0xFFFF0000;
    private static final int UPPER_BITS_SHIFT = 16;
    private static final int LOWER_BITS_MASK = 0xFFFF;

    public static final int MAX_INSERT_LOOPS_DEFAULT = 100;
    public static final float LOAD_FACTOR_DEFAULT = 0.9f;

    private final Class<T> valueClazz;
    private final HashFunction<T> hashFunction1;
    private final HashFunction<T> hashFunction2;
    private final int maxInsertLoops;
    private final float loadFactor;

    private T[] values;
    private int size = 0;

    public CuckooHashSet(Class<T> valueClazz) {
        this(valueClazz, MAX_INSERT_LOOPS_DEFAULT, LOAD_FACTOR_DEFAULT);
    }

    /**
     * Uses {@link Object#hashCode()} as the hash function
     * @param valueClazz type of elements stored in this set
     * @param maxInsertLoops maximum number of loops when inserting an element before resizing
     * @param loadFactor how close to being full before the table is resized
     */
    public CuckooHashSet(Class<T> valueClazz, int maxInsertLoops, float loadFactor) {
        this(valueClazz, maxInsertLoops, loadFactor, new HashFunction<T>() {
            @Override
            public int hash(T t) {
                return t.hashCode();
            }
        });
    }

    /**
     * Like the constructor below, except splits the upper/lower bits of hashFunction1's output to be used for the two hashes
     * @param valueClazz type of elements stored in this set
     * @param maxInsertLoops maximum number of loops when inserting an element before resizing
     * @param loadFactor how close to being full before the table is resized
     * @param hashFunction1 hash function whose result will be split into upper/lower halves to serves as two hash outputs
     */
    public CuckooHashSet(Class<T> valueClazz, int maxInsertLoops, float loadFactor, HashFunction<T> hashFunction1) {
        this(valueClazz, maxInsertLoops, loadFactor, hashFunction1, null);
    }

    /**
     * @param valueClazz type of elements stored in this set
     * @param maxInsertLoops maximum number of loops when inserting an element before resizing
     * @param loadFactor how close to being full before the table is resized
     * @param hashFunction1 first hash function
     * @param hashFunction2 second hash function - must be independent of first hash function
     */
    public CuckooHashSet(Class<T> valueClazz, int maxInsertLoops, float loadFactor, HashFunction<T> hashFunction1, HashFunction<T> hashFunction2) {
        if(valueClazz == null || maxInsertLoops < 1 || loadFactor <= 0 || Float.isNaN(loadFactor) || hashFunction1 == null) {
            throw new IllegalArgumentException();
        }
        this.valueClazz = valueClazz;
        this.maxInsertLoops = maxInsertLoops;
        this.loadFactor = loadFactor;
        this.hashFunction1 = hashFunction1;
        this.hashFunction2 = hashFunction2;
        values = (T[])Array.newInstance(valueClazz, DEFAULT_INITIAL_SIZE);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size() < 1;
    }

    @Override
    public boolean contains(Object o) {
        return getPosition((T)o) != null;
    }

    private Integer getPosition(T val) {
        final int hashFunc1Pos = hashFunction1.hash(val);
        int pos;
        if(hashFunction2 == null) {
            pos = (hashFunc1Pos & UPPER_BITS_MASK) >> UPPER_BITS_SHIFT;
        } else {
            pos = hashFunc1Pos;
        }
        final int halfValuesLength = values.length / 2;
        pos %= halfValuesLength;
        T t = values[pos];
        if(t == null || !t.equals(val)) {
            if(hashFunction2 == null) {
                pos = hashFunc1Pos & LOWER_BITS_MASK;
            } else {
                pos = hashFunction2.hash(val);
            }
            pos %= halfValuesLength;
            pos += halfValuesLength;
            t = values[pos];
        }
        Integer ret;
        if(t != null) {
            ret = t.equals(val) ? pos : null;
        } else {
            ret = null;
        }
        return ret;
    }

    @Override
    public boolean add(T t) {
        if(contains(t)) {
            return false;
        }
        if(((float)size) > ((values.length / 2) * loadFactor)) {
            resize();
        }
        add(values, t);
        size++;
        return true;
    }

    /**
     * @return true if resize actually occurred
     */
    private boolean resize() {
        boolean didResize;
        int currentSize = values.length / 2;
        int newSize = size < 1 ? DEFAULT_INITIAL_SIZE : currentSize * 2;
        if(newSize == currentSize) {
            didResize = false;
        } else {
            T[] tmp = (T[]) Array.newInstance(valueClazz, newSize * 2);
            addValues(values, tmp);
            values = tmp;
            didResize = true;
        }
        return didResize;
    }

    private void addValues(T[] from, T[] tmp) {
        if(from != null) {
            for(T t : from) {
                if(t != null) {
                    add(tmp, t);
                }
            }
        }
    }

    private T add(T[] values, T t) {
        T ret = null;
        int pos;
        int hash;
        int loops;
        int halfValuesLength;
        outer:
        while(true) {
            for(loops = 0; loops < maxInsertLoops; loops++) {
                halfValuesLength = values.length / 2;
                hash = hashFunction1.hash(t);
                if(hashFunction2 == null) {
                    pos = (hash & UPPER_BITS_MASK) >> UPPER_BITS_SHIFT;
                } else {
                    pos = hash;
                }
                pos %= values.length / 2;
                ret = values[pos];
                if(ret == null) {
                    values[pos] = t;
                    break;
                }
                values[pos] = t;
                t = ret;
                if(hashFunction2 == null) {
                    pos = hashFunction1.hash(t) & LOWER_BITS_MASK;
                } else {
                    pos = hashFunction2.hash(t);
                }
                pos %= halfValuesLength;
                pos += halfValuesLength;
                ret = values[pos];
                if(ret == null) {
                    values[pos] = t;
                    break;
                }
                values[pos] = t;
                t = ret;
            }
            if(loops >= maxInsertLoops) {
                if(!resize()) {
                    throw new IllegalStateException("maxInsertLoops exceeded and resize did not occur");
                }
            } else {
                break outer;
            }
        }
        return ret;
    }

    @Override
    public Iterator<T> iterator() {
        return new AbstractIterator<T>() {

            private int currentPos = 0;

            @Override
            protected T computeNext() {
                T ret = null;
                T t;
                for(; currentPos < values.length; currentPos++) {
                    t = values[currentPos];
                    if(t != null) {
                        ret = t;
                        break;
                    }
                }
                currentPos++;
                if(ret == null) {
                    ret = endOfData();
                }
                return ret;
            }
        };
    }

    @Override
    public boolean remove(Object o) {
        if(size < 1) {
            return false;
        }
        Integer pos = getPosition((T)o);
        boolean ret;
        if(pos != null) {
            values[pos] = null;
            ret = true;
        } else {
            ret = false;
        }
        size--;
        return ret;
    }

    @Override
    public void clear() {
        values = (T[])Array.newInstance(valueClazz, DEFAULT_INITIAL_SIZE);
        size = 0;
    }

    public static interface HashFunction<K> {
        int hash(K k);
    }
}
