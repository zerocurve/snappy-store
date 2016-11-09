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
package com.gemstone.gemfire.internal.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import com.gemstone.gemfire.CancelException;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.internal.cache.locks.NonReentrantReadWriteLock;
import com.gemstone.gemfire.internal.concurrent.ConcurrentTHashSegment;
import com.gemstone.gemfire.internal.concurrent.ConcurrentTHashSet;
import com.gemstone.gemfire.internal.concurrent.CustomEntryConcurrentHashMap;
import com.gemstone.gemfire.internal.concurrent.MapCallback;
import com.gemstone.gemfire.internal.shared.FinalizeHolder;
import com.gemstone.gemfire.internal.shared.FinalizeObject;
import com.gemstone.gnu.trove.HashingStats;
import com.gemstone.gnu.trove.TObjectHashingStrategy;

@SuppressWarnings({"unused", "Convert2Lambda", "NullableProblems", "ForLoopReplaceableByForEach"})
final class ConcurrentRegionEntryHashSet
    extends ConcurrentTHashSet<AbstractRegionEntry> {

  private static final float DEFAULT_LOAD_FACTOR = 0.6f;
  public static final int DEFAULT_INITIAL_CAPACITY = 1000;

  private static final int MAX_CLEAN_PERCENT = 10;
  private static final int CLEAN_BATCHSIZE = 20;

  private AbstractRegionEntry head;
  private final Object headLock = new Object();
  private final AtomicLong removedCount = new AtomicLong(0);
  private final NonReentrantReadWriteLock cleanerLock =
      new NonReentrantReadWriteLock(
          InternalDistributedSystem.getConnectedInstance(), null);

  private final Runnable cleanerTask = new Runnable() {
    @Override
    public synchronized void run() {
      AbstractRegionEntry current;
      synchronized (headLock) {
        current = head; // snapshot head
      }
      // acquire write lock
      cleanerLock.attemptWriteLock(-1);
      int batchCount = 1;
      try {
        while (current != null) {
          AbstractRegionEntry next = current.getNextEntry();
          if (next != null && next.isDestroyedOrRemovedButNotTombstone()) {
            // clear next from list
            next = next.getNextEntry();
            current.setNextEntry(next);
          }
          if (++batchCount > CLEAN_BATCHSIZE) {
            // release the lock
            cleanerLock.releaseWriteLock();
            batchCount = 0;
            // reacquire the lock
            cleanerLock.attemptWriteLock(-1);
            batchCount = 1;
          }
          current = next;
        }
      } finally {
        if (batchCount > 0) {
          cleanerLock.releaseWriteLock();
        }
      }
    }
  };

  ConcurrentRegionEntryHashSet() {
    this(DEFAULT_CONCURRENCY, DEFAULT_INITIAL_CAPACITY,
        DEFAULT_LOAD_FACTOR, null, null);
  }

  ConcurrentRegionEntryHashSet(TObjectHashingStrategy strategy) {
    this(DEFAULT_CONCURRENCY, DEFAULT_INITIAL_CAPACITY,
        DEFAULT_LOAD_FACTOR, strategy, null);
  }

  ConcurrentRegionEntryHashSet(int concurrency) {
    this(concurrency, DEFAULT_INITIAL_CAPACITY,
        DEFAULT_LOAD_FACTOR, null, null);
  }

  ConcurrentRegionEntryHashSet(TObjectHashingStrategy strategy,
      HashingStats stats) {
    this(DEFAULT_CONCURRENCY, DEFAULT_INITIAL_CAPACITY,
        DEFAULT_LOAD_FACTOR, strategy, stats);
  }

  ConcurrentRegionEntryHashSet(int concurrency, int initialCapacity,
      float loadFactor, TObjectHashingStrategy strategy, HashingStats stats) {
    super(concurrency, initialCapacity, loadFactor, strategy, stats);
  }

  private void addToList(final AbstractRegionEntry re) {
    synchronized (this.headLock) {
      // lock is for reading head while next does not need sync on reads
      re.setNextEntry(this.head);
      this.head = re;
    }
  }

  private void removeFromList(final AbstractRegionEntry re) {
    synchronized (this.headLock) {
      // noinspection NumberEquality
      if (re != this.head) {
        // increment count but if its large then reset and submit clean task
        while (true) {
          long numRemoved = removedCount.get();
          if (numRemoved >= longSize() * MAX_CLEAN_PERCENT / 100) {
            if (removedCount.compareAndSet(numRemoved, 0)) {
              submitRunnable(cleanerTask);
              break;
            }
          } else if (removedCount.compareAndSet(numRemoved, numRemoved + 1)) {
            break;
          }
        }
      } else {
        // can remove right away
        this.head = re.getNextEntry();
      }
    }
  }

  @Override
  public boolean add(AbstractRegionEntry re) {
    if (super.add(re)) {
      addToList(re);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public Object addKey(AbstractRegionEntry re) {
    Object current = super.addKey(re);
    if (current == null) {
      addToList(re);
      return null;
    } else {
      return current;
    }
  }

  @Override
  public Object put(AbstractRegionEntry re) {
    Object current = super.put(re);
    // multiple threads trying to put on same entry will block in addToList
    addToList(re);
    return current;
  }

  @Override
  public boolean remove(Object o) {
    if (super.remove(o)) {
      removeFromList((AbstractRegionEntry)o);
      return true;
    } else {
      return false;
    }
  }

  public AbstractRegionEntry removeKey(Object o) {
    AbstractRegionEntry current = super.removeKey(o);
    if (current != null) {
      removeFromList(current);
      return current;
    } else {
      return null;
    }
  }

  public boolean replace(Object o, AbstractRegionEntry re) {
    // not used in region operations
    throw new UnsupportedOperationException();
  }

  public <K, C, P> AbstractRegionEntry create(final K key,
      final MapCallback<K, AbstractRegionEntry, C, P> valueCreator,
      final C context, final P createParams) {
    // not used in region operations
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    // avoid unnecessary complication with this method which is not used
    throw new UnsupportedOperationException();
  }

  @Override
  public ListItr iterator() {
    // iterate using next pointer rather than hash set based to get much
    // better performance (CPU pre-fetch works better upto 4-7X faster)
    return new ListItr();
  }

  @Override
  public void clear() {
    ArrayList<AbstractRegionEntry> entries = null;
    final BucketRegionIndexCleaner cleaner = BucketRegion.getIndexCleaner();
    final boolean isOffHeapEnabled = LocalRegion.getAndClearOffHeapEnabled();
    if (cleaner != null || isOffHeapEnabled) {
      entries = new ArrayList<>();
    }
    final CacheObserver observer = CacheObserverHolder.getInstance();
    try {
      for (ConcurrentTHashSegment<AbstractRegionEntry> seg : this.segments) {
        entries = CustomEntryConcurrentHashMap.clearTHashSegment(seg, entries);
      }
    } finally {
      if (entries != null) {
        final ArrayList<AbstractRegionEntry> clearedEntries = entries;
        final Runnable cleanTask = new Runnable() {
          public void run() {
            ArrayList<RegionEntry> regionEntries =
                cleaner != null ? new ArrayList<RegionEntry>() : null;
            for (AbstractRegionEntry e : clearedEntries) {
              // noinspection SynchronizationOnLocalVariableOrMethodParameter
              synchronized (e) {
                if (cleaner != null) {
                  regionEntries.add(e);
                } else {
                  e.release();
                }
              }
            }
            if (cleaner != null) {
              cleaner.clearEntries(regionEntries);
            }
            if (LocalRegion.ISSUE_CALLBACKS_TO_CACHE_OBSERVER &&
                observer != null && isOffHeapEnabled) {
              observer.afterRegionConcurrentHashMapClear();
            }
          }
        };
        submitRunnable(cleanTask);
      } else {
        if (LocalRegion.ISSUE_CALLBACKS_TO_CACHE_OBSERVER &&
            observer != null && isOffHeapEnabled) {
          observer.afterRegionConcurrentHashMapClear();
        }
      }
    }
  }

  private void submitRunnable(final Runnable task) {
    boolean submitted = false;
    InternalDistributedSystem ids = InternalDistributedSystem
        .getConnectedInstance();
    if (ids != null && !ids.isLoner()) {
      try {
        ids.getDistributionManager().getWaitingThreadPool().submit(task);
        submitted = true;
      } catch (RejectedExecutionException | NullPointerException |
          CancelException e) {
        // fall through with submitted false
      }
    }
    if (!submitted) {
      String name = this.getClass().getSimpleName() + "@" +
          this.hashCode() + " Clear Thread";
      Thread thread = new Thread(task, name);
      thread.setDaemon(true);
      thread.start();
    }
  }

  private final class ListItr implements Iterator<AbstractRegionEntry> {

    private FinalizeIterator finalizer;
    private AbstractRegionEntry current;
    private int batchCount;

    ListItr() {
      synchronized (headLock) {
        // take a snapshot of head
        this.current = head;
      }
      // read lock the cleaner list
      cleanerLock.attemptReadLock(-1);
      batchCount = 1; // indicate read lock acquired
      // add finalizer to release lock if not done via hasNext == false
      this.finalizer = new FinalizeIterator(this, cleanerLock);
    }

    @Override
    public boolean hasNext() {
      if (this.current != null) {
        return true;
      } else {
        // release lock if acquired
        if (batchCount > 0) {
          cleanerLock.releaseReadLock();
          batchCount = 0;
        }
        // clear finalizer
        final FinalizeIterator finalizer = this.finalizer;
        if (finalizer != null) {
          finalizer.clearAll();
          this.finalizer = null;
        }
        return false;
      }
    }

    @Override
    public AbstractRegionEntry next() {
      final AbstractRegionEntry entry = this.current;
      if (entry != null) {
        this.current = entry.getNextEntry();
        if (++batchCount > CLEAN_BATCHSIZE) {
          // release the lock
          cleanerLock.releaseReadLock();
          batchCount = 0;
          final FinalizeIterator finalizer = this.finalizer;
          if (finalizer != null) {
            finalizer.isLocked = false;
          }
          // reacquire the lock
          cleanerLock.attemptReadLock(-1);
          batchCount = 1;
          if (finalizer != null) {
            finalizer.isLocked = true;
          }
        }
        return entry;
      } else {
        throw new NoSuchElementException();
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private static final class FinalizeIterator extends FinalizeObject {

    private final NonReentrantReadWriteLock lock;
    private boolean isLocked;

    FinalizeIterator(ListItr itr, NonReentrantReadWriteLock lock) {
      super(itr, true);
      this.lock = lock;
      this.isLocked = true;
    }

    @Override
    protected FinalizeHolder getHolder() {
      return getServerHolder();
    }

    @Override
    protected void clearThis() {
      this.isLocked = false;
    }

    @Override
    protected boolean doFinalize() throws Exception {
      if (isLocked) {
        lock.releaseReadLock();
        isLocked = false;
      }
      return true;
    }
  }
}
