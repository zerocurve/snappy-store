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
package com.gemstone.gemfire.internal.cache.partitioned;

import com.gemstone.gemfire.i18n.LogWriterI18n;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import com.gemstone.gemfire.SystemFailure;
import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.RegionDestroyedException;
import com.gemstone.gemfire.cache.query.Index;
import com.gemstone.gemfire.cache.query.QueryException;
import com.gemstone.gemfire.distributed.internal.DM;
import com.gemstone.gemfire.distributed.internal.DistributionManager;
import com.gemstone.gemfire.distributed.internal.InternalDistributedSystem;
import com.gemstone.gemfire.distributed.internal.ReplyException;
import com.gemstone.gemfire.distributed.internal.ReplyMessage;
import com.gemstone.gemfire.distributed.internal.ReplyProcessor21;
import com.gemstone.gemfire.distributed.internal.membership.InternalDistributedMember;
import com.gemstone.gemfire.internal.cache.ForceReattemptException;
import com.gemstone.gemfire.internal.cache.PartitionedRegion;
import com.gemstone.gemfire.internal.cache.PartitionedRegionException;
import com.gemstone.gemfire.internal.cache.TXStateInterface;

/**
 * This class represents a partition message for removing indexes. An instance of this
 * class is send over the wire to remove indexes on remote vms. This class
 * extends PartitionMessage
 * {@link com.gemstone.gemfire.internal.cache.partitioned.PartitionMessage}
 * 
 * @author rdubey
 * 
 */
public final class RemoveIndexesMessage extends PartitionMessage
  {

  /**
   * Represents how many buckets had indexes and got removed.
   */
//  private int bucketIndexesRemoved;
  
  /**
   * Name of the index to be removed.
   */
  private String indexName;
  
  /**
   * Boolean indicating only a single index has to be removed.
   */
  private boolean removeSingleIndex;

  /**
   * Constructor.
   */
  public RemoveIndexesMessage() {

  }
  
  /**
   * Constructor for remove indexes to be sent over the wire.
   * 
   * @param recipients
   *          members to which this message has to be sent
   * @param regionId
   *          partitioned region id
   * @param processor
   *          the processor to reply to
   */
  public RemoveIndexesMessage(Set recipients, int regionId,
      ReplyProcessor21 processor) {
    super(recipients, regionId, processor, null);
  }

  /**
   * Constructor to remove a particular index which will be sent over the wire.
   * @param recipients
   *          members to which this message has to be sent
   * @param regionId
   *          partitioned region id
   * @param processor
   *          the processor to reply to
   * @param removeSingleIndex boolean indicating to remove a partitular index
   * 
   * @param indexName name of the index to be removed.
   * 
   */
  public RemoveIndexesMessage(Set recipients, int regionId,
      ReplyProcessor21 processor, boolean removeSingleIndex, String indexName) {
    super(recipients, regionId, processor, null);
    this.removeSingleIndex = removeSingleIndex;
    this.indexName = indexName;
  }

  /**
   * This message may be sent to nodes before the PartitionedRegion is
   * completely initialized due to the RegionAdvisor(s) knowing about the
   * existance of a partitioned region at a very early part of the
   * initialization
   */
  @Override
  protected final boolean failIfRegionMissing() {
    return false;
  }

  /**
   * This method is responsible to remove index on the given partitioned region.
   * @param dm
   *          Distribution maanger for the system
   * @param pr
   *          Partitioned region to remove indexes on.
   * 
   * @throws CacheException
   *           indicates a cache level error
   * @throws ForceReattemptException
   *           if the peer is no longer available
   * @throws InterruptedException if
   *           the thread is interrupted in the operation for example during
   *           shutdown.
   */
  @Override
  protected boolean operateOnPartitionedRegion(DistributionManager dm,
      PartitionedRegion pr, long startTime) throws CacheException, QueryException,
      ForceReattemptException, InterruptedException
  {
    // TODO Auto-generated method stub
    
    LogWriterI18n logger = pr.getLogWriterI18n();
    ReplyException replyEx = null;
    boolean result = true;
    int bucketIndexRemoved = 0; // invalid
    int numIndexesRemoved = 0;
    
    logger.info(LocalizedStrings.RemoveIndexesMessage_WILL_REMOVE_THE_INDEXES_ON_THIS_PR___0, pr);
    try {
      if (this.removeSingleIndex) {
        bucketIndexRemoved = pr.removeIndex(this.indexName);
      } else {
        bucketIndexRemoved = pr.removeIndexes(true); //remotely orignated
      }
      numIndexesRemoved = pr.getDataStore().getAllLocalBuckets().size();
    } catch (Exception ex) {
      result = false;
      replyEx = new ReplyException (ex);
    }

    // send back the reply.
    sendReply(getSender(), getProcessorId(), dm, replyEx, result, 
        bucketIndexRemoved, numIndexesRemoved);
    
    return false;
  }
  
  /**
   * Send a reply for remove indexes message.
   * 
   * @param member
   *          representing the actual index creatro in the system
   * @param procId
   *          waiting processor
   * @param dm
   *          distirbution manager to send the message
   * @param ex
   *          any exceptions
   * @param result
   *          represents remove index worked properly.
   * @param bucketIndexesRemoved
   *          number of bucket indexes removed properly.
   */
  void sendReply(InternalDistributedMember member, int procId, DM dm,
      ReplyException ex, boolean result, int bucketIndexesRemoved,
      int totalNumBuckets) {
    RemoveIndexesReplyMessage.send(member, processorId, dm, ex, result, bucketIndexesRemoved, totalNumBuckets);
    
  }

  /**
   * Sends this RemoveIndexesMessage to all the participating members in the system.
   * 
   * @param pr prartitioned region to remove the index on.
   * @return PartitionResponse indicating sucessful remove index
   * 
   */

  public static PartitionResponse send(PartitionedRegion pr, Index ind, boolean removeAllIndex)
  {
    RemoveIndexesResponse processor = null;
  //  PartitionResponse processor = null;
    RegionAdvisor advisor = (RegionAdvisor)(pr.getDistributionAdvisor());
    final Set recipients = new HashSet(advisor.adviseDataStore());
    // removing the originator for remove index command.
    recipients.remove(pr.getDistributionManager().getDistributionManagerId());
    
   // RemoveIndexesResponse processor = null;
   // RemoveIndexesMessage removeIndexesMsg = new RemoveIndexesMessage();
    if (recipients.size() > 0) {
      processor = (RemoveIndexesResponse)(new RemoveIndexesMessage())
          .createReplyProcessor(pr, recipients, null);
    }
    if (removeAllIndex) {
      RemoveIndexesMessage rm = new RemoveIndexesMessage(recipients, pr.getPRId(), processor);
      /*Set failures =*/ pr.getDistributionManager().putOutgoing(rm);
    } else {
      // remove a single index.
      RemoveIndexesMessage rm = new RemoveIndexesMessage(recipients, pr.getPRId(), processor, true, ind.getName());
      /*Set failures = */ pr.getDistributionManager().putOutgoing(rm);
    }
    return processor;

  }
  
  @Override
  PartitionResponse createReplyProcessor(PartitionedRegion r, Set recipients,
      final TXStateInterface tx) {
    // r.getCache().getLogger().warning("PutMessage.createReplyProcessor()", new
    // Exception("stack trace"));
    return new RemoveIndexesResponse(r.getSystem(), recipients, tx);
  }

  public int getDSFID() {
    return PR_REMOVE_INDEXES_MESSAGE;
  }

  @Override
  public void fromData(DataInput in)
      throws IOException, ClassNotFoundException {
    super.fromData(in);
    this.removeSingleIndex = in.readBoolean();
    if (this.removeSingleIndex) 
      this.indexName = in.readUTF();
  }

  @Override
  public void toData(DataOutput out)
      throws IOException {
    super.toData(out);
    out.writeBoolean(this.removeSingleIndex);
    if (this.removeSingleIndex)
      out.writeUTF(this.indexName);
  }

  /**
   * Processes remove index on the receiver.
   */
  @Override
  protected final void basicProcess(final DistributionManager dm) {

    Throwable thr = null;
    LogWriterI18n logger = null;
    boolean sendReply = true;
    PartitionedRegion pr = null;
    
    try {
      logger = dm.getLoggerI18n();
      logger.info(LocalizedStrings.RemoveIndexesMessage_TRYING_TO_GET_PR_WITH_ID___0, this.regionId);
      pr = PartitionedRegion.getPRFromId(this.regionId);
      logger.info(LocalizedStrings.RemoveIndexesMessage_REMOVE_INDEXES_MESSAGE_GOT_THE_PR__0, pr);
      
      if (pr == null /* && failIfRegionMissing() */ ) {
        throw new PartitionedRegionException(LocalizedStrings.RemoveIndexesMessage_COULD_NOT_GET_PARTITIONED_REGION_FROM_ID_0_FOR_MESSAGE_1_RECEIVED_ON_MEMBER_2_MAP_3.toLocalizedString(
              new Object[] {Integer.valueOf(this.regionId), this, dm.getId(), PartitionedRegion.dumpPRId()}));
      }
      // remove the indexes on the pr.
      sendReply = operateOnPartitionedRegion(dm, pr, 0);

    
    }
    catch(PRLocallyDestroyedException pde){
      if(logger.fineEnabled()){
        logger.fine("Region is locally Destroyed ");
        }
      thr = pde;
    }
    catch (Throwable t) {
      Error err;
      if (t instanceof Error && SystemFailure.isJVMFailureError(
          err = (Error)t)) {
        SystemFailure.initiateFailure(err);
        // If this ever returns, rethrow the error. We're poisoned
        // now, so don't let this thread continue.
        throw err;
      }
      // Whenever you catch Error or Throwable, you must also
      // check for fatal JVM error (see above). However, there is
      // _still_ a possibility that you are dealing with a cascading
      // error condition, so you also need to check to see if the JVM
      // is still usable:
      SystemFailure.checkFailure();
      // log the exception at fine level if there is no reply to the message
      if (this.processorId == 0) {
        logger.fine(this + " exception while processing message:", t);
      }
      else if (DistributionManager.VERBOSE && (t instanceof RuntimeException)) {
        logger.fine("Exception caught while processing message", t);
      }
      if (t instanceof RegionDestroyedException && pr != null) {
        if (pr.isClosed) {
          logger.info(LocalizedStrings.RemoveIndexesMessage_REGION_IS_LOCALLY_DESTROYED_THROWING_REGIONDESTROYEDEXCEPTION_FOR__0, pr);
          thr = new RegionDestroyedException(LocalizedStrings.RemoveIndexesMessage_REGION_IS_LOCALLY_DESTROYED_ON_0.toLocalizedString(dm.getId()), pr.getFullPath());
        }
      }
      else {
        thr = t;
      }
    }
    finally {
      if (sendReply && this.processorId != 0) {
        ReplyException rex = null;
        if (thr != null) {
          rex = new ReplyException(thr);
        }
        sendReply(getSender(), this.processorId, dm, rex, pr, 0);
      }
    }
  
    
  }

  /**
   * Class representing remove index response. This class has all the
   * information for successful or unsucessful remove index on the member of
   * the partitioned region.
   * 
   * @author rdubey
   * 
   */
  public static class RemoveIndexesResponse extends PartitionResponse
   {

    
    /**
     * Result of remove index.
     */
//     boolean result;
     
     
    /**
     * Number of buckets index removed.
     */
    private int numBucketIndexRemoved;
    
    /**
     * Total number of buckets in the sytem.
     */
    private int numTotalRemoteBuckets;

    /**
     * Constructor.
     */
    public RemoveIndexesResponse(InternalDistributedSystem ds, Set recipients,
        final TXStateInterface tx) {
      super(ds, recipients, tx);
    }

    /**
     * Waits for the response from the members for remove indexes call on this system.
     * @throws ForceReattemptException 
     */
    public RemoveIndexesResult waitForResults() throws CacheException,
        ForceReattemptException
    {
      waitForCacheException();
      return new RemoveIndexesResult(0);
    }
    
    /**
     * Sets the relevant information in the response.
     * 
     * @param result
     *          true if index removed properly
     * @param numBucketsIndexesRemoved
     *          number of buckets indexes removed remotely for a memeber.
     * @param numTotalBuckets
     *          number of total buckets in the member.
     */
    public void setResponse(boolean result, int numBucketsIndexesRemoved,
        int numTotalBuckets)
    {
//      this.result = result;
      this.numBucketIndexRemoved += numBucketsIndexesRemoved;
      this.numTotalRemoteBuckets += numTotalBuckets;
    }
    
    /**
     * Returns number of remotely removed indexes.
     */
    public int getRemoteRemovedIndexes() {
      return this.numBucketIndexRemoved;
    }
    
    /**
     * Returns the total number of remote buckets.
     */
    public int getTotalRemoteBuckets() {
      return this.numTotalRemoteBuckets;
    }

  }// RemoveIndexResponse

  /**
   * Class representing remove index results on pr.
   * 
   * @author rdubey
   */
  public static class RemoveIndexesResult
   {

    /**
     * Int representing number of total bucket indexes removed.
     */
//    private int numBucketIndexRemoved;

    /**
     * Constructor.
     * @param numBucketIndexRemoved number of total bucket indexes removed.
     * 
     */
    public RemoveIndexesResult(int numBucketIndexRemoved) {

//      this.numBucketIndexRemoved = numBucketIndexRemoved;
    }

  } // RemoveIndexesResult
  
  
  
  /**
   * Class for index creation reply. This class has the information about sucessful
   * or unsucessful index creation.
   * @author rdubey
   *
   */
  public static final class RemoveIndexesReplyMessage extends ReplyMessage  {
    
    /** Indexes removed or not. */
    private boolean result;

    /** 
     * Number of buckets locally remove indexes
     */
    private int numBucketsIndexesRemoved;

    /** Number of total bukets in this vm. */
    private int numTotalBuckets;
    
    /**
     * Default constructor.
     *
     */
    public RemoveIndexesReplyMessage () {
      
    }
    
    
    /**
     * Constructor for index creation reply message.
     * @param processorId processor id of the waiting processor
     * @param ex any exceptions
     * @param result ture if indexes removed properly else false
     * @param numBucketsIndexesRemoved number of buckets indexed.
     * @param numTotalBuckets number of total buckets.
     */
    RemoveIndexesReplyMessage(int processorId, ReplyException ex, boolean result,
        int numBucketsIndexesRemoved, int numTotalBuckets) {
      super();
      super.setException(ex);
      this.result = result;
      this.numBucketsIndexesRemoved = numBucketsIndexesRemoved;
      this.numTotalBuckets = numTotalBuckets;
      setProcessorId(processorId);
    }
    
    @Override
    public int getDSFID() {
      return PR_REMOVE_INDEXES_REPLY_MESSAGE;
    }

    @Override
    public void fromData(DataInput in) throws IOException,
        ClassNotFoundException {
      super.fromData(in);
      this.result = in.readBoolean();
      this.numBucketsIndexesRemoved = in.readInt();
      this.numTotalBuckets = in.readInt();
    }
    
    @Override
    public void toData(DataOutput out) throws IOException {
      super.toData(out);
      out.writeBoolean(this.result);
      out.writeInt(this.numBucketsIndexesRemoved);
      out.writeInt(this.numTotalBuckets);
    }

    
    
    /**
     * Actual method sending the index creation reply message.
     * @param recipient the originator of index creation message
     * @param processorId waiting processor id
     * @param dm distribution manager
     * @param ex any exceptions
     * @param result true is indexes removed sucessfully
     * @param numBucketsIndexesRemoved number of buckets indexed
     * @param numTotalBuckets total number of buckets
     */
    public static void send(InternalDistributedMember recipient,
        int processorId, DM dm, ReplyException ex, boolean result,
        int numBucketsIndexesRemoved, int numTotalBuckets)
    {
      RemoveIndexesReplyMessage rmIndMsg = new RemoveIndexesReplyMessage(processorId, ex,
          result, numBucketsIndexesRemoved, numTotalBuckets);
      rmIndMsg.setRecipient(recipient);
      dm.putOutgoing(rmIndMsg);
    }
    
    /**
     * Processes this RemoveIndexesReplyMessge on the receiver.
     * @param dm distribution manager
     */
    @Override
    public final void process(final DM dm, final ReplyProcessor21 p)
    {
      RemoveIndexesResponse processor = (RemoveIndexesResponse)p;
      if (processor != null) {
        processor.setResponse(this.result, this.numBucketsIndexesRemoved,
            this.numTotalBuckets);
        processor.process(this);
      }
    }
    
  } // RemvoeIndexReplyMessage

}
