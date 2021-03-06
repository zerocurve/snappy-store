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

/**
 * Do not modify this class. It was generated.
 * Instead modify LeafRegionEntry.cpp and then run
 * bin/generateRegionEntryClasses.sh from the directory
 * that contains your build.xml.
 */
package com.pivotal.gemfirexd.internal.engine.store.entry;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
import com.gemstone.gemfire.internal.cache.Token;
import com.gemstone.gemfire.internal.concurrent.AtomicUpdaterFactory;
import com.gemstone.gemfire.internal.offheap.OffHeapRegionEntryHelper;
import com.gemstone.gemfire.internal.offheap.annotations.Released;
import com.gemstone.gemfire.internal.offheap.annotations.Retained;
import com.gemstone.gemfire.internal.offheap.annotations.Unretained;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.internal.cache.lru.EnableLRU;
import com.gemstone.gemfire.internal.cache.persistence.DiskRecoveryStore;
import com.gemstone.gemfire.internal.InternalStatisticsDisabledException;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.cache.versions.VersionSource;
import com.gemstone.gemfire.internal.cache.versions.VersionStamp;
import com.gemstone.gemfire.internal.cache.versions.VersionTag;
import com.gemstone.gemfire.internal.concurrent.CustomEntryConcurrentHashMap.HashEntry;
import java.io.DataOutput;
import java.io.IOException;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.RegionEntry;
import com.gemstone.gemfire.internal.cache.RegionEntryContext;
import com.gemstone.gemfire.internal.cache.RegionEntryFactory;
import com.gemstone.gemfire.internal.shared.Version;
import com.gemstone.gemfire.internal.util.ArrayUtils;
import com.pivotal.gemfirexd.internal.engine.sql.catalog.ExtraTableInfo;
import com.pivotal.gemfirexd.internal.engine.store.CompactCompositeKey;
import com.pivotal.gemfirexd.internal.engine.store.GemFireContainer;
import com.pivotal.gemfirexd.internal.engine.store.RegionEntryUtils;
import com.pivotal.gemfirexd.internal.engine.store.RowFormatter;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.services.cache.ClassSize;
import com.pivotal.gemfirexd.internal.iapi.services.io.ArrayInputStream;
import com.pivotal.gemfirexd.internal.iapi.sql.execute.ExecRow;
import com.pivotal.gemfirexd.internal.iapi.types.BooleanDataValue;
import com.pivotal.gemfirexd.internal.iapi.types.DataTypeDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueFactory;
import com.pivotal.gemfirexd.internal.iapi.types.RowLocation;
import com.pivotal.gemfirexd.internal.shared.common.StoredFormatIds;
import com.gemstone.gemfire.cache.CacheWriterException;
import com.gemstone.gemfire.cache.EntryNotFoundException;
import com.gemstone.gemfire.cache.TimeoutException;
import com.gemstone.gemfire.internal.cache.CachedDeserializable;
import com.gemstone.gemfire.internal.cache.EntryEventImpl;
import com.gemstone.gemfire.internal.cache.RegionClearedException;
import com.gemstone.gemfire.internal.cache.Token;
import com.gemstone.gemfire.internal.cache.OffHeapRegionEntry;
import com.pivotal.gemfirexd.internal.engine.store.CompactCompositeRegionKey;
import com.pivotal.gemfirexd.internal.engine.store.offheap.OffHeapRegionEntryUtils;
import com.gemstone.gemfire.internal.cache.DiskId;
import com.gemstone.gemfire.internal.cache.DiskStoreImpl;
import com.gemstone.gemfire.internal.cache.AbstractDiskRegionEntry;
public class VersionedBucketRowLocationStatsDiskRegionEntryOffHeap extends RowLocationStatsDiskRegionEntry
    implements OffHeapRegionEntry, VersionStamp
{
  public VersionedBucketRowLocationStatsDiskRegionEntryOffHeap (RegionEntryContext context, Object key,
    @Retained
    Object value
      ) {
    super(context,
          (value instanceof RecoveredEntry ? null : value)
        );
    initialize(context, value);
    this.tableInfo = RegionEntryUtils.entryGetTableInfo(context, key, value);
    this.bucketId = RegionEntryUtils.getBucketId(context);
    this.key = RegionEntryUtils.entryGetRegionKey(key, value);
  }
  protected int hash;
  private HashEntry<Object, Object> next;
  @SuppressWarnings("unused")
  private volatile long lastModified;
  private static final AtomicLongFieldUpdater<VersionedBucketRowLocationStatsDiskRegionEntryOffHeap> lastModifiedUpdater
    = AtomicUpdaterFactory.newLongFieldUpdater(VersionedBucketRowLocationStatsDiskRegionEntryOffHeap.class, "lastModified");
  protected long getlastModifiedField() {
    return lastModifiedUpdater.get(this);
  }
  protected boolean compareAndSetLastModifiedField(long expectedValue, long newValue) {
    return lastModifiedUpdater.compareAndSet(this, expectedValue, newValue);
  }
  @Override
  public final int getEntryHash() {
    return this.hash;
  }
  @Override
  protected void setEntryHash(int v) {
    this.hash = v;
  }
  @Override
  public final HashEntry<Object, Object> getNextEntry() {
    return this.next;
  }
  @Override
  public final void setNextEntry(final HashEntry<Object, Object> n) {
    this.next = n;
  }
  protected void initialize(RegionEntryContext context, Object value) {
    diskInitialize(context, value);
  }
  @Override
  public int updateAsyncEntrySize(EnableLRU capacityController) {
    throw new IllegalStateException("should never be called");
  }
  private void diskInitialize(RegionEntryContext context, Object value) {
    DiskRecoveryStore drs = (DiskRecoveryStore)context;
    DiskStoreImpl ds = drs.getDiskStore();
    long maxOplogSize = ds.getMaxOplogSize();
    this.id = DiskId.createDiskId(maxOplogSize, true , ds.needsLinkedList());
    Helper.initialize(this, drs, value);
  }
  protected DiskId id;
  public DiskId getDiskId() {
    return this.id;
  }
  @Override
  public void setDiskId(RegionEntry old) {
    this.id = ((AbstractDiskRegionEntry)old).getDiskId();
  }
  @Override
  public final void updateStatsForGet(boolean hit, long time)
  {
    setLastAccessed(time);
    if (hit) {
      incrementHitCount();
    } else {
      incrementMissCount();
    }
  }
  @Override
  public final void setLastModified(long lastModified) {
    _setLastModified(lastModified);
    if (!DISABLE_ACCESS_TIME_UPDATE_ON_PUT) {
      setLastAccessed(lastModified);
    }
  }
  private volatile long lastAccessed;
  private volatile int hitCount;
  private volatile int missCount;
  private static final AtomicIntegerFieldUpdater<VersionedBucketRowLocationStatsDiskRegionEntryOffHeap> hitCountUpdater
    = AtomicUpdaterFactory.newIntegerFieldUpdater(VersionedBucketRowLocationStatsDiskRegionEntryOffHeap.class, "hitCount");
  private static final AtomicIntegerFieldUpdater<VersionedBucketRowLocationStatsDiskRegionEntryOffHeap> missCountUpdater
    = AtomicUpdaterFactory.newIntegerFieldUpdater(VersionedBucketRowLocationStatsDiskRegionEntryOffHeap.class, "missCount");
  @Override
  public final long getLastAccessed() throws InternalStatisticsDisabledException {
    return this.lastAccessed;
  }
  private void setLastAccessed(long lastAccessed) {
    this.lastAccessed = lastAccessed;
  }
  @Override
  public final long getHitCount() throws InternalStatisticsDisabledException {
    return this.hitCount & 0xFFFFFFFFL;
  }
  @Override
  public final long getMissCount() throws InternalStatisticsDisabledException {
    return this.missCount & 0xFFFFFFFFL;
  }
  private void incrementHitCount() {
    hitCountUpdater.incrementAndGet(this);
  }
  private void incrementMissCount() {
    missCountUpdater.incrementAndGet(this);
  }
  @Override
  public final void resetCounts() throws InternalStatisticsDisabledException {
    hitCountUpdater.set(this,0);
    missCountUpdater.set(this,0);
  }
  @Override
  public final void txDidDestroy(long currTime) {
    setLastModified(currTime);
    setLastAccessed(currTime);
    this.hitCount = 0;
    this.missCount = 0;
  }
  @Override
  public boolean hasStats() {
    return true;
  }
  private VersionSource memberID;
  private short entryVersionLowBytes;
  private short regionVersionHighBytes;
  private int regionVersionLowBytes;
  private byte entryVersionHighByte;
  private byte distributedSystemId;
  public int getEntryVersion() {
    return ((entryVersionHighByte << 16) & 0xFF0000) | (entryVersionLowBytes & 0xFFFF);
  }
  public long getRegionVersion() {
    return (((long)regionVersionHighBytes) << 32) | (regionVersionLowBytes & 0x00000000FFFFFFFFL);
  }
  public long getVersionTimeStamp() {
    return getLastModified();
  }
  public void setVersionTimeStamp(long time) {
    setLastModified(time);
  }
  public VersionSource getMemberID() {
    return this.memberID;
  }
  public int getDistributedSystemId() {
    return this.distributedSystemId;
  }
  public void setVersions(VersionTag tag) {
    this.memberID = tag.getMemberID();
    int eVersion = tag.getEntryVersion();
    this.entryVersionLowBytes = (short)(eVersion & 0xffff);
    this.entryVersionHighByte = (byte)((eVersion & 0xff0000) >> 16);
    this.regionVersionHighBytes = tag.getRegionVersionHighBytes();
    this.regionVersionLowBytes = tag.getRegionVersionLowBytes();
    if (!(tag.isGatewayTag()) && this.distributedSystemId == tag.getDistributedSystemId()) {
      if (getVersionTimeStamp() <= tag.getVersionTimeStamp()) {
        setVersionTimeStamp(tag.getVersionTimeStamp());
      } else {
        tag.setVersionTimeStamp(getVersionTimeStamp());
      }
    } else {
      setVersionTimeStamp(tag.getVersionTimeStamp());
    }
    this.distributedSystemId = (byte)(tag.getDistributedSystemId() & 0xff);
  }
  public void setMemberID(VersionSource memberID) {
    this.memberID = memberID;
  }
  @Override
  public VersionStamp getVersionStamp() {
    return this;
  }
  public VersionTag asVersionTag() {
    VersionTag tag = VersionTag.create(memberID);
    tag.setEntryVersion(getEntryVersion());
    tag.setRegionVersion(this.regionVersionHighBytes, this.regionVersionLowBytes);
    tag.setVersionTimeStamp(getVersionTimeStamp());
    tag.setDistributedSystemId(this.distributedSystemId);
    return tag;
  }
  public void processVersionTag(LocalRegion r, VersionTag tag,
      boolean isTombstoneFromGII, boolean hasDelta,
      VersionSource thisVM, InternalDistributedMember sender, boolean checkForConflicts) {
    basicProcessVersionTag(r, tag, isTombstoneFromGII, hasDelta, thisVM, sender, checkForConflicts);
  }
  @Override
  public void processVersionTag(EntryEvent cacheEvent) {
    super.processVersionTag(cacheEvent);
  }
  public short getRegionVersionHighBytes() {
    return this.regionVersionHighBytes;
  }
  public int getRegionVersionLowBytes() {
    return this.regionVersionLowBytes;
  }
  private Object key;
  @Override
  public final Object getRawKey() {
    return this.key;
  }
  @Override
  protected void _setRawKey(Object key) {
    this.key = key;
  }
  @Retained @Released private volatile long ohAddress;
  private final static AtomicLongFieldUpdater<VersionedBucketRowLocationStatsDiskRegionEntryOffHeap> ohAddrUpdater =
      AtomicUpdaterFactory.newLongFieldUpdater(VersionedBucketRowLocationStatsDiskRegionEntryOffHeap.class, "ohAddress");
  @Override
  public Token getValueAsToken() {
    return OffHeapRegionEntryHelper.getValueAsToken(this);
  }
  @Override
  @Unretained
  protected Object getValueField() {
    return OffHeapRegionEntryHelper._getValue(this);
  }
  @Override
  protected void setValueField(@Unretained Object v) {
    OffHeapRegionEntryHelper.setValue(this, v);
  }
  @Override
  @Retained
  public Object _getValueRetain(RegionEntryContext context, boolean decompress) {
    return OffHeapRegionEntryHelper._getValueRetain(this, decompress);
  }
  @Override
  public long getAddress() {
    return ohAddrUpdater.get(this);
  }
  @Override
  public boolean setAddress(long expectedAddr, long newAddr) {
    return ohAddrUpdater.compareAndSet(this, expectedAddr, newAddr);
  }
  @Override
  @Released
  public void release() {
    OffHeapRegionEntryHelper.releaseEntry(this);
  }
  private transient ExtraTableInfo tableInfo;
  @Override
  public final ExtraTableInfo getTableInfo(GemFireContainer baseContainer) {
    return this.tableInfo;
  }
  @Override
  public final Object getContainerInfo() {
    return this.tableInfo;
  }
  @Override
  public final Object setContainerInfo(final LocalRegion owner, final Object val) {
    final GemFireContainer container;
    ExtraTableInfo tabInfo;
    if (owner == null) {
      final RowFormatter rf;
      if ((tabInfo = this.tableInfo) != null
          && (rf = tabInfo.getRowFormatter()) != null) {
        container = rf.container;
      }
      else {
        return null;
      }
    }
    else {
      container = (GemFireContainer)owner.getUserAttribute();
    }
    if (container != null && container.isByteArrayStore()) {
      tabInfo = container.getExtraTableInfo(val);
      this.tableInfo = tabInfo;
      if (tabInfo != null && tabInfo.regionKeyPartOfValue()) {
        return tabInfo;
      }
    }
    return null;
  }
  @Override
  public int estimateMemoryUsage() {
    return ClassSize.refSize;
  }
  @Override
  public int getTypeFormatId() {
    return StoredFormatIds.ACCESS_MEM_HEAP_ROW_LOCATION_ID;
  }
  @Override
  public final Object cloneObject() {
    return this;
  }
  @Override
  public final RowLocation getClone() {
    return this;
  }
  @Override
  public final int compare(DataValueDescriptor other) {
    if (this == other) {
      return 0;
    }
    return this.hashCode() - other.hashCode();
  }
  @Override
  public DataValueDescriptor recycle() {
    return this;
  }
  @Override
  public DataValueDescriptor getNewNull() {
    return DataValueFactory.DUMMY;
  }
  @Override
  public boolean isNull() {
    return this == DataValueFactory.DUMMY;
  }
  @Override
  public Object getObject() throws StandardException {
    return this;
  }
  @Override
  public DataValueDescriptor coalesce(DataValueDescriptor[] list,
      DataValueDescriptor returnValue) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int compare(DataValueDescriptor other, boolean nullsOrderedLow)
      throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, DataValueDescriptor other,
      boolean orderedNulls, boolean unknownRV) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, DataValueDescriptor other,
      boolean orderedNulls, boolean nullsOrderedLow, boolean unknownRV)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue equals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int getLengthInBytes(DataTypeDescriptor dtd) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue greaterOrEquals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue greaterThan(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue in(DataValueDescriptor left,
      DataValueDescriptor[] inList, boolean orderedList)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue isNotNull() {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue isNullOp() {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue lessOrEquals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public BooleanDataValue lessThan(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public void normalize(DataTypeDescriptor dtd, DataValueDescriptor source)
      throws StandardException {
  }
  @Override
  public BooleanDataValue notEquals(DataValueDescriptor left,
      DataValueDescriptor right) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public void readExternalFromArray(ArrayInputStream ais) throws IOException,
  ClassNotFoundException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public void setValue(DataValueDescriptor theValue) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int writeBytes(byte[] outBytes, int offset, DataTypeDescriptor dtd) {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int computeHashCode(int maxWidth, int hash) {
    throw new UnsupportedOperationException("unexpected invocation for " + toString());
  }
  @Override
  public final DataValueDescriptor getKeyColumn(int index) {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public final void getKeyColumns(DataValueDescriptor[] keys) {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, ExecRow row, boolean byteArrayStore,
      int colIdx, boolean orderedNulls, boolean unknownRV)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public boolean compare(int op, CompactCompositeKey key, int colIdx,
      boolean orderedNulls, boolean unknownRV) throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public int equals(RowFormatter rf, byte[] bytes, boolean isKeyBytes,
      int logicalPosition, int keyBytesPos, final DataValueDescriptor[] outDVD)
          throws StandardException {
    throw new UnsupportedOperationException("unexpected invocation");
  }
  @Override
  public byte getTypeId() {
    throw new UnsupportedOperationException("Implement the method for DataType="+ this);
  }
  @Override
  public void writeNullDVD(DataOutput out) throws IOException{
    throw new UnsupportedOperationException("Implement the method for DataType="+ this);
  }
  @Override
  public Object getValueWithoutFaultInOrOffHeapEntry(LocalRegion owner) {
    return this;
  }
  @Override
  public Object getValueOrOffHeapEntry(LocalRegion owner) {
    return this;
  }
  @Override
  public Object getRawValue() {
    Object val = OffHeapRegionEntryHelper._getValueRetain(this, false);
    if (val != null && !Token.isInvalidOrRemoved(val)
        && val != Token.NOT_AVAILABLE) {
      CachedDeserializable storedObject = (CachedDeserializable) val;
      return storedObject.getDeserializedValue(null, this);
    }
    return null;
  }
  @Override
  public Object prepareValueForCache(RegionEntryContext r, Object val,
      boolean isEntryUpdate, boolean valHasMetadataForGfxdOffHeapUpdate) {
    if (okToStoreOffHeap(val)
        && OffHeapRegionEntryUtils.isValidValueForGfxdOffHeapStorage(val)) {
      if (isEntryUpdate
      ) {
        return OffHeapRegionEntryUtils.prepareValueForUpdate(this, r, val, valHasMetadataForGfxdOffHeapUpdate);
      } else {
        return OffHeapRegionEntryUtils.prepareValueForCreate(r, val, false);
      }
    }
    return super.prepareValueForCache(r, val, isEntryUpdate, valHasMetadataForGfxdOffHeapUpdate);
  }
  @Override
  public boolean destroy(LocalRegion region, EntryEventImpl event,
      boolean inTokenMode, boolean cacheWrite, @Unretained Object expectedOldValue,
      boolean forceDestroy, boolean removeRecoveredEntry)
      throws CacheWriterException, EntryNotFoundException, TimeoutException,
      RegionClearedException {
    Object key = event.getKey();
    if (key instanceof CompactCompositeRegionKey) {
      byte[] keyBytes = ((CompactCompositeRegionKey)key)
          .snapshotKeyFromValue(false);
      if (keyBytes != null) {
        this._setRawKey(keyBytes);
      }
    }
    return super.destroy(region, event, inTokenMode, cacheWrite,
        expectedOldValue, forceDestroy, removeRecoveredEntry);
  }
  @Override
  public Version[] getSerializationVersions() {
    return null;
  }
  private final int bucketId;
  @Override
  public Object getValue(GemFireContainer baseContainer) {
    return RegionEntryUtils.getValue(baseContainer, this.bucketId, this);
  }
  @Override
  public Object getValueWithoutFaultIn(GemFireContainer baseContainer) {
    return RegionEntryUtils.getValueWithoutFaultIn(baseContainer,this.bucketId, this);
  }
  @Override
  public ExecRow getRow(GemFireContainer baseContainer) {
    return RegionEntryUtils.getRow(baseContainer, this.bucketId, this, this.tableInfo);
  }
  @Override
  public ExecRow getRowWithoutFaultIn(GemFireContainer baseContainer) {
    return RegionEntryUtils.getRowWithoutFaultIn(baseContainer, this.bucketId, this,
        this.tableInfo);
  }
  @Override
  public int getBucketID() {
    return this.bucketId;
  }
  @Override
  protected StringBuilder appendFieldsToString(final StringBuilder sb) {
    sb.append("key=");
    final Object k = getKeyCopy();
    final Object val = OffHeapRegionEntryUtils.getHeapRowForInVMValue(this);
    RegionEntryUtils.entryKeyString(k, val, getTableInfo(null), sb);
    sb.append("; byte source = "+ this._getValue());
    sb.append("; rawValue=");
    ArrayUtils.objectStringNonRecursive(val, sb);
    sb.append("; lockState=0x").append(Integer.toHexString(getState()));
    sb.append("; bucketId=").append(this.bucketId);
    return sb;
  }
  private static RegionEntryFactory factory = new RegionEntryFactory() {
    public final RegionEntry createEntry(RegionEntryContext context, Object key, Object value) {
      return new VersionedBucketRowLocationStatsDiskRegionEntryOffHeap(context, key, value);
    }
    public final Class<?> getEntryClass() {
      return VersionedBucketRowLocationStatsDiskRegionEntryOffHeap.class;
    }
    public RegionEntryFactory makeVersioned() {
      return this;
    }
    @Override
    public RegionEntryFactory makeOnHeap() {
      return VersionedBucketRowLocationStatsDiskRegionEntryHeap.getEntryFactory();
    }
  };
  public static RegionEntryFactory getEntryFactory() {
    return factory;
  }
}
