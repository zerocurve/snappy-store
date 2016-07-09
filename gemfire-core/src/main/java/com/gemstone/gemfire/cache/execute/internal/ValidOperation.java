package com.gemstone.gemfire.cache.execute.internal;

import com.gemstone.gemfire.cache.EntryEvent;

/**
 * This API is an internal mechanism to determine if the operation in question is still valid.
 * This can be used to recheck the validity of assumptions made during an operation.
 */
public interface ValidOperation<K, V> {
  public boolean isValid(EntryEvent<K, V> event);
}
