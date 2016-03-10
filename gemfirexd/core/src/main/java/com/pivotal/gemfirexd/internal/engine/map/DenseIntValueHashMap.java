/*
 * Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package com.pivotal.gemfirexd.internal.engine.map;

import com.pivotal.gemfirexd.internal.engine.store.RowFormatter;

/**
 * A compact hash map with any key object but only with int value.
 * This avoids Integer object creation and key object deserialization
 * depends on {@link DenseHashMapSerializer} implementation. The interface offers
 * opportunity to avoid full key deserialization during hashCode computation, equal comparison
 * and rehashing.
 *
 * Per Entry overhead is 4 bytes (pointer to byte[]). Its by experiment found
 * the best put/get performance and memory usage tradeoff is achieved with simple key and fixed
 * width value serialization. A base64 encoded key (assuming ascii only) or multiple entries packed
 * together into a single entry (a.k.a separate chaining) did not yield much memory benefit whereas
 * performance was significantly effected. In fact, for separate chaining memory consumption increased due to
 * every entry length written consuming 4 more bytes.
 *
 * Concurrency is for the time being at Segment level. Number of segments by default is twice the number of
 * cpu cores and should be sufficient for parallelism. Per entry locking if required can
 * be done using a separate array with bit indicator for read/write (0 - Read, 1 - Write)
 * operation.
 *
 * This is based on open addressing and suffers usual primary clustering problem. This might show up
 * even more because of LoadFactor being 0.85 by default unlike 0.50 but compensates for it during expansion
 * which brings down the LoadFactor to 0.65. Due to sparsity of the byte[][] total memory consumed for 1 million
 * entries shows 12 bytes per entry overhead when measured using runtime memory usage.
 *
 * TODO:
 * a) Add segment level read/write lock.
 * b) Add #remove method.
 *
 */
public class DenseIntValueHashMap<K> {

  private static final byte[] TOMBSTONE = new byte[0];

  private static final int numSegments = Integer.getInteger("com.pivotal.gemfirexd.internal.engine.map.DenseIntValueHashMap.NUM_SEGMENTS", Runtime.getRuntime().availableProcessors() * 8);

  protected final DenseHashMapSerializer<K> serializer;

  private int size = 0;

  private double loadFactor = 0.85f;

  private DenseIntValueHashMap.Segment[] segments = new DenseIntValueHashMap.Segment[numSegments];

  public DenseIntValueHashMap(DenseHashMapSerializer serializer,
      int initialCapacity) {

    this.serializer = serializer;

    int capacity = 1;
    // make capacity power of 2 instead of just taking a rounded off number.
    // a rounded off number isn't good for probing.
    while(capacity < initialCapacity)
      capacity *= 2;

    for (int i = 0; i < segments.length; i++) {
      segments[i] = new DenseIntValueHashMap.Segment(capacity);
    }
  }

  public int size() {
    return size;
  }

  public int put(K key, int value) {
    int hash = serializer.getHashCode(key);
    segments[hash & (segments.length - 1)].putEntry(key, hash, value);
    size++;
    return value;
  }

  public int get(Object key) {
    int hash = serializer.getHashCode((K)key);
    return segments[hash & (segments.length - 1)].getEntry(key, hash);
  }

  public final static int readInt(byte[] kv, int offset, int numBytes) {
    return RowFormatter.readInt(kv, offset, numBytes);
  }

  public final static void writeInt(byte[] kv, int val, int offset, int numBytes) {
    assert val < 0xffff || numBytes != 2;
    RowFormatter.writeInt(kv, val, offset, numBytes);
  }

  private final int probeLinear(final K key,
      int keyHash, final byte[][] table,
      final byte[] entry1,
      final int numBits) {

    assert key != null || entry1 != null : "key not supplied... ";

    final boolean useIndexInTable = entry1 != null;

    int maxOffset = table.length - 1;

    final int hashCode = useIndexInTable ?
        serializer.getHashCode(entry1) :
        keyHash;

    int index = hashCode & maxOffset;

    byte[] entry2 = table[index];

    if (entry2 == null) {
      return index;
    } else if (entry2 == TOMBSTONE) {
      return index;
    } else if (useIndexInTable) {
      if (serializer.equals(entry1, entry2)) {
        return index;
      }
    } else if (serializer.equals(key, entry2)) {
      return index;
    }

    int stepValue = (hashCode >>> numBits) & maxOffset;
    stepValue = stepValue > 0 ? stepValue : 1;
    index = (index + stepValue) & maxOffset;

    int beginIdx = index;
    for (; ; ) {

      entry2 = table[index];

      if (entry2 == null) {
        return index;
      } else if (entry2 == TOMBSTONE) {
        return index;
      } else if (useIndexInTable) {
        if (serializer.equals(entry1, entry2)) {
          return index;
        }
      } else if (serializer.equals(key, entry2)) {
        return index;
      }

      index = index + 1 & maxOffset;

      assert (beginIdx != index) : "exhausted all the slots. This should have never happened.";
    }
  }

  private final class Segment {

    private byte[][] table; // a pair of key/value held here.
    private int size = 0;
    private int numBits;

    protected Segment(int initCapacity) {
      table = new byte[initCapacity][];
      this.numBits = Integer.bitCount(initCapacity);
    }

    private void putEntry(K key, final int keyHash, int value) {

      int index = probeLinear(key, keyHash, this.table, null, this.numBits);
      table[index] = serializer.serialize(key, value);
      size++;

      ensureCapacity();
    }

    private void ensureCapacity() {
      if ((double)size / table.length > loadFactor) {
        int newCapacity = table.length;
        while ((double)size / newCapacity > (loadFactor-0.2))
          newCapacity *= 2;

        final byte[][] newTable = new byte[newCapacity][];
        final int newNumBits = Integer.bitCount(newCapacity);

        for (byte[] entry : table) {
          if (entry == null) {
            continue;
          }
          int index = probeLinear(null, -1, newTable, entry, newNumBits);
          newTable[index] = entry;
        }

        this.table = newTable;
        this.numBits = newNumBits;
      }
    }

    private int getEntry(K key, int keyHash) {

      final byte[][] tab = this.table;
      final int numBits = this.numBits;

      final int index = probeLinear(key, keyHash, tab, null, numBits);
      return serializer.deserializeValue(tab[index]);
    }

  }


}
