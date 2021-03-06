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

package com.gemstone.gemfire.internal.cache.tier.sockets;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.distributed.DistributedSystem;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.DSCODE;
import com.gemstone.gemfire.internal.DataSerializableFixedID;
import com.gemstone.gemfire.internal.InternalDataSerializer;
import com.gemstone.gemfire.internal.cache.Conflatable;
import com.gemstone.gemfire.internal.cache.EnumListenerEvent;
import com.gemstone.gemfire.internal.cache.EventID;
import com.gemstone.gemfire.internal.cache.lru.Sizeable;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientUpdateMessageImpl.ClientCqConcurrentMap;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientUpdateMessageImpl.CqNameToOp;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientUpdateMessageImpl.CqNameToOpHashMap;
import com.gemstone.gemfire.internal.cache.tier.sockets.ClientUpdateMessageImpl.CqNameToOpSingleEntry;
import com.gemstone.gemfire.internal.shared.Version;

/**
 * This new class acts as a wrapper for the existing
 * <code>ClientUpdateMessageImpl</code>. Now, the <code>HARegionQueue</code>s
 * will contain instances of <code>HAEventWrapper</code> as value, instead
 * that of <code>ClientUpdateMessagesImpl</code>. It implements the
 * <code>Conflatable</code> interface to fit itself into the
 * <code>HARegionQueue</code> mechanics. It also has a property to indicate
 * the number of <code>HARegionQueue</code>s referencing this instance.
 * 
 * @author ashetkar
 * @since 5.7
 * 
 */
public class HAEventWrapper implements Conflatable, DataSerializableFixedID, Sizeable {
  private static final long serialVersionUID = 5874226899495609988L;

  /**
   * The name of the <code>Region</code> that was updated
   */
  private String regionName;

  /**
   * The key that was updated
   */
  private Object keyOfInterest;

  /**
   * If the event should be conflated.
   */
  private boolean shouldConflate = false;

  /**
   * The event id of the event
   */
  private EventID eventIdentifier;

  /**
   * The underlying map for all the ha region queues associated with a bridge
   * server.
   */
  private Map haContainer;

  /**
   * Indicates the number of <code>HARegionQueue</code>s referencing
   * <code>this</code> instance.
   * All access should be done using rcUpdater.
   */
  @SuppressWarnings("unused")
  private volatile long referenceCount;
  private static final AtomicLongFieldUpdater<HAEventWrapper> rcUpdater
    = AtomicLongFieldUpdater.newUpdater(HAEventWrapper.class, "referenceCount");
  
  /**
   * If true, the entry containing this HAEventWrapper instance will not be
   * removed from the haContainer, even if the referenceCount value is zero.
   */
  private transient boolean putInProgress = false;

  /**
   * A value true indicates that this instance is not used in the
   * <code>haContainer</code>. So any changes in this instance will not be
   * visible to the <code>haContainer</code>.
   */
  private transient boolean isRefFromHAContainer = false;

  /**
   * A reference to its <code>ClientUpdateMessage</code> instance.
   */
  private ClientUpdateMessage clientUpdateMessage = null;

  /**
   * This will hold the CQ list while its ClientUpdateMessageImpl is overflown
   * to disk, and reassign it back when it's faulted-in.
   */
  private ClientCqConcurrentMap clientCqs = null;

  /**
   * Parameterized constructor.
   * 
   * @param event
   */
  public HAEventWrapper(ClientUpdateMessage event) {
    this.regionName = event.getRegionName();
    this.keyOfInterest = event.getKeyOfInterest();
    this.shouldConflate = event.shouldBeConflated();
    this.eventIdentifier = event.getEventId();
    rcUpdater.set(this,  0);
    this.clientUpdateMessage = event;
    this.clientCqs = ((ClientUpdateMessageImpl)event).getClientCqs();
  }

  /**
   * @param eventId
   */
  public HAEventWrapper(EventID eventId) {
    this.eventIdentifier = eventId;
    this.clientUpdateMessage = new ClientUpdateMessageImpl(
        EnumListenerEvent.AFTER_CREATE, new ClientProxyMembershipID(), eventId,
        null);
    rcUpdater.set(this,  0);
  }

  /**
   * Default constructor
   * 
   */
  public HAEventWrapper() {
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.gemstone.gemfire.internal.cache.Conflatable#getEventId()
   */
  public EventID getEventId() {
    return this.eventIdentifier;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.gemstone.gemfire.internal.cache.Conflatable#getKeyToConflate()
   */
  public Object getKeyToConflate() {
    return this.keyOfInterest;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.gemstone.gemfire.internal.cache.Conflatable#getRegionToConflate()
   */
  public String getRegionToConflate() {
    return this.regionName;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.gemstone.gemfire.internal.cache.Conflatable#getValueToConflate()
   */
  public Object getValueToConflate() {
    return null;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.gemstone.gemfire.internal.cache.Conflatable#setLatestValue(java.lang.Object)
   */
  public void setLatestValue(Object value) {
    // this.value = (byte[])value;
  }

  /*
   * (non-Javadoc)
   * 
   * @see com.gemstone.gemfire.internal.cache.Conflatable#shouldBeConflated()
   */
  public boolean shouldBeConflated() {
    return this.shouldConflate;
  }

  /**
   * {@inheritDoc}
   */
  public boolean shouldBeMerged() {
    return false;
  }

  /**
   * {@inheritDoc}
   */
  public boolean merge(Conflatable existing) {
    throw new AssertionError("not expected to be invoked");
  }

  public void setClientUpdateMessage(ClientUpdateMessage cum) {
    this.clientUpdateMessage = cum;
  }

  /**
   * Use this method <B>only</B> when put operation on HARegionQueue is in
   * progress. It'll always return null otherwise.
   * 
   * @return an instance of <code>ClientUpdateMessage</code> implementation.
   */
  public ClientUpdateMessage getClientUpdateMessage() {
    return this.clientUpdateMessage;
  }

  public ClientCqConcurrentMap getClientCqs() {
    return this.clientCqs;
  }

  public void setPutInProgress(boolean inProgress) {
    this.putInProgress = inProgress;
  }

  public boolean getPutInProgress() {
    return this.putInProgress;
  }

  public void setIsRefFromHAContainer(boolean bool) {
    this.isRefFromHAContainer = bool;
  }

  public boolean getIsRefFromHAContainer() {
    return this.isRefFromHAContainer;
  }

  public void setHAContainer(Map container) {
    this.haContainer = container;
  }

  /**
   * This implementation considers only the EventID of
   * <code>HAEventWrapper</code> for the equality test. It allows an instance
   * of {@link EventID} to be tested for equality.
   * 
   * @param other
   *          The instance of HAEventWrapper or EventID to be tested for
   *          equality.
   * 
   * @return boolean true if <code>this</code> object's event id matches with
   *         that of other.
   */
  @Override
  public boolean equals(Object other) {
    if (other == null || !(other instanceof HAEventWrapper)) {
      return false;
    }

    return this == other
        || this.getEventId().equals(((HAEventWrapper)other).getEventId());
  }

  /**
   * Returns the hash code of underlying event id.
   * 
   * @return int The hash code of underlying EventID instance.
   */
  @Override
  public int hashCode() {
    return this.getEventId().hashCode();
  }

  @Override
  public String toString() {
    if (this.clientUpdateMessage != null) {
      return "HAEventWrapper[refCount=" + getReferenceCount() + "; msg=" +
      		this.clientUpdateMessage+"]";
    } else {
      return "HAEventWrapper[region=" + this.regionName
        + "; key=" + this.keyOfInterest
        + "; refCount=" + getReferenceCount()
        + "; inContainer=" + this.isRefFromHAContainer
        + "; putInProgress=" + this.putInProgress
        + "; event=" + this.eventIdentifier
        + ((this.clientUpdateMessage == null) ? "; no message" : ";with message")
        + ((this.clientUpdateMessage == null) ? "" : ("; op=" + this.clientUpdateMessage.getOperation()))
        + ((this.clientUpdateMessage == null) ? "" : ("; version=" + this.clientUpdateMessage.getVersionTag()))
        + "]";
    }
  }

  /**
   * Calls toData() on its clientUpdateMessage present in the
   * haContainer (client-messages-region or the map).
   * 
   * @param out
   *          The output stream which the object should be written to.
   */
  public void toData(DataOutput out) throws IOException {
    ClientUpdateMessageImpl cum = (ClientUpdateMessageImpl)this.haContainer
        .get(this);

    // If the dispatcher sends the cum object to the client and removes it from
    // the haContainer before we do haContainer.get() (above), we indicate that
    // by sending false boolean value.
    if (cum != null) {
      DataSerializer.writePrimitiveBoolean(true, out);
      DataSerializer.writeObject(cum.getEventId(), out);
    }
    else {
      DataSerializer.writePrimitiveBoolean(false, out);
      DataSerializer.writeObject(new EventID(), out);
      // Create a dummy ClientUpdateMessageImpl instance
      cum = new ClientUpdateMessageImpl(EnumListenerEvent.AFTER_CREATE,
          new ClientProxyMembershipID(), null, null);
    }
    InternalDataSerializer.invokeToData(cum, out);
    if (cum.hasCqs()) {
      DataSerializer.writeConcurrentHashMap(cum.getClientCqs(), out);
    }
  }

  /**
   * Calls fromData() on ClientUpdateMessage and sets it as its member variable.
   * Also, sets the referenceCount to zero.
   * 
   * @param in
   *          The input stream from which the object should be constructed.
   */
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    LogWriterI18n logger = null;
    DistributedSystem ds = InternalDistributedSystem.getAnyInstance();
    if (ds != null) {
      logger = ds.getLogWriter().convertToLogWriterI18n();
    }

    if (DataSerializer.readPrimitiveBoolean(in)) {
      // Indicates that we have a ClientUpdateMessage along with the HAEW instance in inputstream.
      this.eventIdentifier = (EventID)DataSerializer.readObject(in);
      this.clientUpdateMessage = new ClientUpdateMessageImpl();
      InternalDataSerializer.invokeFromData(this.clientUpdateMessage, in);
      ((ClientUpdateMessageImpl)this.clientUpdateMessage)
          .setEventIdentifier(this.eventIdentifier);
      if (this.clientUpdateMessage.hasCqs()) {
        {
          ClientCqConcurrentMap cqMap;
          int size = InternalDataSerializer.readArrayLength(in);
          if (size == -1) {
            cqMap = null;
          } else {
            cqMap = new ClientCqConcurrentMap(size, 1.0f, 1);
            for (int i = 0; i < size; i++) {
              ClientProxyMembershipID key = DataSerializer.<ClientProxyMembershipID>readObject(in);
              CqNameToOp value;
              {
                byte typeByte = in.readByte();
                if (typeByte == DSCODE.HASH_MAP) {
                  int cqNamesSize = InternalDataSerializer.readArrayLength(in);
                  if (cqNamesSize == -1) {
                    throw new IllegalStateException("The value of a ConcurrentHashMap is not allowed to be null.");
                  } else if (cqNamesSize == 1) {
                    String cqNamesKey = DataSerializer.<String>readObject(in);
                    Integer cqNamesValue = DataSerializer.<Integer>readObject(in);
                    value = new CqNameToOpSingleEntry(cqNamesKey, cqNamesValue);
                  } else if (cqNamesSize == 0) {
                    value = new CqNameToOpSingleEntry(null, 0);
                  } else {
                    value = new CqNameToOpHashMap(cqNamesSize);
                    for (int j = 0; j < cqNamesSize; j++) {
                      String cqNamesKey = DataSerializer.<String>readObject(in);
                      Integer cqNamesValue = DataSerializer.<Integer>readObject(in);
                      value.add(cqNamesKey, cqNamesValue);
                    }
                  }
                } else if (typeByte == DSCODE.NULL) {
                  throw new IllegalStateException("The value of a ConcurrentHashMap is not allowed to be null.");
                } else {
                  throw new IllegalStateException("Expected DSCODE.NULL or DSCODE.HASH_MAP but read " + typeByte);
                }
              }
              cqMap.put(key, value);
            }
          }
          this.clientCqs = cqMap;
        }
        ((ClientUpdateMessageImpl)this.clientUpdateMessage)
            .setClientCqs(this.clientCqs);
      }
      this.regionName = this.clientUpdateMessage.getRegionName();
      this.keyOfInterest = this.clientUpdateMessage.getKeyOfInterest();
      this.shouldConflate = this.clientUpdateMessage.shouldBeConflated();
      rcUpdater.set(this, 0);
    }
    else {
      // Read and ignore dummy eventIdentifier instance.
      DataSerializer.readObject(in);
      // Read and ignore dummy ClientUpdateMessageImpl instance.
      InternalDataSerializer.invokeFromData(new ClientUpdateMessageImpl(), in);
      // hasCq will be false here, so client CQs are not read from this input
      // stream.
      if (logger.fineEnabled()) {
        logger
            .fine("HAEventWrapper.fromData(): The event has already been sent to the client by the primary server.");
      }
    }
  }
  
  public int getDSFID() {
    return HA_EVENT_WRAPPER;
  }

  /**
   * Returning zero as default size. This is because this method will be called
   * only in case of ha-overflow, in which case, we ignore size of instances of
   * <code>HAEventWrapper</code>, as they are never overflown to disk.
   * 
   * @return int Size of this instance in bytes.
   */
  public int getSizeInBytes() {
    return 0;
  }

  @Override
  public Version[] getSerializationVersions() {
     return null;
  }
  
  public long getReferenceCount() {
    return rcUpdater.get(this);
  }
  
  public long incAndGetReferenceCount() {
    return rcUpdater.incrementAndGet(this);
  }
  public long decAndGetReferenceCount() {
    return rcUpdater.decrementAndGet(this);
  }

}
