/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
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
package com.gemstone.gemfire.internal.concurrent;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

import com.gemstone.gnu.trove.HashingStats;
import com.gemstone.gnu.trove.THash;

/**
 * A {@link ConcurrentMap} adapter for {@link ConcurrentTHashSet}.
 */
@SuppressWarnings({"NullableProblems", "WeakerAccess"})
public class ConcurrentTHashSetMapAdapter<K, V> implements ConcurrentMap<K, V> {

  public static final class MapEntry<K, V>
      implements Map.Entry<K, V>, Serializable {

    private final K key;
    private final V value;

    public MapEntry(K key, V value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public K getKey() {
      return this.key;
    }

    @Override
    public V getValue() {
      return this.value;
    }

    @Override
    public V setValue(V value) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int hashCode() {
      return this.key.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o == this) return true;
      if (o instanceof MapEntry<?, ?>) {
        return this.key.equals(((MapEntry<?, ?>)o).key);
      } else {
        return this.key.equals(o);
      }
    }
  }

  private final ConcurrentTHashSet<MapEntry<K, V>> set;

  public ConcurrentTHashSetMapAdapter() {
    this(THash.DEFAULT_INITIAL_CAPACITY);
  }

  public ConcurrentTHashSetMapAdapter(int initialCapacity) {
    this(initialCapacity, THash.DEFAULT_LOAD_FACTOR,
        ConcurrentTHashSet.DEFAULT_CONCURRENCY);
  }

  public ConcurrentTHashSetMapAdapter(int initialCapacity,
      float loadFactor, int concurrencyLevel) {
    this(initialCapacity, loadFactor, concurrencyLevel, null);
  }

  public ConcurrentTHashSetMapAdapter(int initialCapacity,
      float loadFactor, int concurrencyLevel, HashingStats stats) {
    this.set = new ConcurrentTHashSet<>(concurrencyLevel, initialCapacity,
        loadFactor, null, stats);
  }

  @Override
  public int size() {
    return this.set.size();
  }

  @Override
  public boolean isEmpty() {
    return this.set.isEmpty();
  }

  @Override
  public boolean containsKey(Object key) {
    // noinspection SuspiciousMethodCalls
    return this.set.contains(key);
  }

  @Override
  public boolean containsValue(Object value) {
    for (MapEntry<K, V> e : this.set) {
      if (e.value.equals(value)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public V get(Object key) {
    final MapEntry<K, V> entry = this.set.get(key);
    if (entry != null) {
      return entry.value;
    } else {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public V put(K key, V value) {
    return (V)this.set.put(new MapEntry<>(key, value));
  }

  @Override
  public V remove(Object key) {
    final MapEntry<K, V> entry = this.set.removeKey(key);
    if (entry != null) {
      return entry.value;
    } else {
      return null;
    }
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    final ConcurrentTHashSet<MapEntry<K, V>> set = this.set;
    for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
      set.put(new MapEntry<>(e.getKey(), e.getValue()));
    }
  }

  @Override
  public void clear() {
    this.set.clear();
  }

  @Override
  public Set<K> keySet() {
    return new AbstractSet<K>() {
      @Override
      public boolean contains(Object key) {
        return set.contains(key);
      }

      @Override
      public boolean remove(Object key) {
        return set.remove(key);
      }

      @Override
      public int size() {
        return set.size();
      }

      @Override
      public void clear() {
        set.clear();
      }

      @Override
      public Iterator<K> iterator() {
        return new Iterator<K>() {
          private final Iterator<MapEntry<K, V>> itr = set.iterator();

          @Override
          public boolean hasNext() {
            return itr.hasNext();
          }

          @Override
          public K next() {
            return itr.next().key;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException("remove");
          }
        };
      }
    };
  }

  @Override
  public Collection<V> values() {
    return new AbstractCollection<V>() {
      @Override
      public int size() {
        return set.size();
      }

      @Override
      public void clear() {
        set.clear();
      }

      @Override
      public Iterator<V> iterator() {
        return new Iterator<V>() {
          private final Iterator<MapEntry<K, V>> itr = set.iterator();

          @Override
          public boolean hasNext() {
            return itr.hasNext();
          }

          @Override
          public V next() {
            return itr.next().value;
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException("remove");
          }
        };
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  public Set<Map.Entry<K, V>> entrySet() {
    return (Set)this.set;
  }

  @SuppressWarnings("unchecked")
  @Override
  public V putIfAbsent(K key, V value) {
    final Object old = this.set.addKey(new MapEntry<>(key, value));
    if (old == null) {
      return null;
    } else {
      return ((MapEntry<K, V>)old).value;
    }
  }

  @Override
  public boolean remove(Object key, Object value) {
    return false;
  }

  @Override
  public boolean replace(K key, V oldValue, V newValue) {
    return false;
  }

  @Override
  public V replace(K key, V value) {
    return null;
  }
}
