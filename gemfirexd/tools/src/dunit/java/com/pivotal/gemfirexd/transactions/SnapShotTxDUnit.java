package com.pivotal.gemfirexd.transactions;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.IsolationLevel;
import com.gemstone.gemfire.cache.PartitionAttributes;
import com.gemstone.gemfire.cache.PartitionAttributesFactory;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.RegionEntry;
import com.gemstone.gemfire.internal.cache.TXManagerImpl;
import com.gemstone.gemfire.internal.cache.TXState;
import com.gemstone.gemfire.internal.cache.TXStateInterface;
import com.gemstone.gemfire.internal.cache.TXStateProxy;
import com.pivotal.gemfirexd.DistributedSQLTestBase;
import com.pivotal.gemfirexd.TestUtil;
import com.pivotal.gemfirexd.internal.engine.distributed.message.RegionExecutorMessage;
import io.snappydata.test.dunit.SerializableRunnable;
import io.snappydata.test.dunit.VM;


public class SnapShotTxDUnit extends DistributedSQLTestBase {

  private boolean gotConflict = false;

  private volatile Throwable threadEx;

  public SnapShotTxDUnit(String name) {
    super(name);
  }

  public static final String regionName = "T1";

  @Override
  protected String reduceLogging() {
    return "fine";
  }

  public static void createPR(String partitionedRegionName, Integer redundancy,
      Integer totalNumBuckets) {
    PartitionAttributesFactory paf = new PartitionAttributesFactory();
    paf.setRedundantCopies(redundancy.intValue())
        .setLocalMaxMemory(50).setTotalNumBuckets(
        totalNumBuckets.intValue());
    PartitionAttributes prAttr = paf.create();
    AttributesFactory attr = new AttributesFactory();
    attr.setPartitionAttributes(prAttr);
    attr.setConcurrencyChecksEnabled(true);
    final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
    assertNotNull(cache);
    Region pr = cache.createRegion(partitionedRegionName, attr.create());
    assertNotNull(pr);
//    getLogWriter().info.(
//        "Partitioned Region " + partitionedRegionName
//            + " created Successfully :" + pr.toString());
  }

  public void testSnapshotInsertAPI() throws Exception {
    startVMs(0, 2);
    Properties props = new Properties();
    final Connection conn = TestUtil.getConnection(props);
    conn.createStatement();

    serverSQLExecute(1, "create schema test");
    serverSQLExecute(1, "create table test.XATT2 (intcol int not null, text varchar(100) not null)");

    VM server1 = this.serverVMs.get(0);
    VM server2 = this.serverVMs.get(1);
    server1.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});
    server2.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        getLogWriter().info(" SKSK in server1 doing two operations. ");
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        r.put(1, 1);
        r.put(2, 2);
        // even before commit it should be visible in both vm
        assertEquals(1, r.get(1));
        assertEquals(2, r.get(2));
        r.getCache().getCacheTransactionManager().commit();
      }
    });

    server2.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        // read the entries
        assertEquals(1, r.get(1));// get don't need to take from snapshot
        assertEquals(2, r.get(2));// get don't need to take from snapshot

        //itr will work on a snapshot.
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        TXState txState = txstate.getLocalTXState();

        for (String regionName : txState.getCurrentSnapshot().keySet()) {
          getLogWriter().info(" the snapshot is for region  " + regionName + " is : " + " snapshot " + Integer.toHexString(System.identityHashCode(txState.getCurrentSnapshot())) + " "
              + txState.getCurrentSnapshot().get(regionName));
        }

        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        Iterator itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null,
            false, false, true, (LocalRegion)r);

        // after this start another insert in a separate thread and those put shouldn't be visible
        Runnable run = new Runnable() {
          @Override
          public void run() {
            ((LocalRegion)r).put(3, 3);
            ((LocalRegion)r).put(4, 4);
          }
        };
        Thread t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          getLogWriter().info("txitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(2, num);
        // should be visible if read directly from region
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          getLogWriter().info("regitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
        r.getCache().getCacheTransactionManager().commit();

      }
    });

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        final Region r = cache.getRegion(regionName);
        // take new snapshot and all the data should be visisble
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        //itr will work on a snapshot. not other ops
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        TXState txState = txstate.getLocalTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
      }
    });
  }

  public void testSnapshotInsertAPI2() throws Exception {
    startVMs(0, 2);
    Properties props = new Properties();
    final Connection conn = TestUtil.getConnection(props);
    conn.createStatement();

    //serverSQLExecute(1, "create schema test");
    //serverSQLExecute(1, "create table test.XATT2 (intcol int not null, text varchar(100) not null)");

    VM server1 = this.serverVMs.get(0);
    VM server2 = this.serverVMs.get(1);
    server1.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});
    server2.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        getLogWriter().info(" SKSK in server1 doing two operations. ");
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        r.put(1, 1);
        r.put(2, 2);
        // even before commit it should be visible in both vm
        assertEquals(1, r.get(1));
        assertEquals(2, r.get(2));
        r.getCache().getCacheTransactionManager().commit();
      }
    });

    server2.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        // read the entries
        assertEquals(1, r.get(1));// get don't need to take from snapshot
        assertEquals(2, r.get(2));// get don't need to take from snapshot


        //itr will work on a snapshot.
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        TXState txState = txstate.getLocalTXState();

        for (String regionName : txState.getCurrentSnapshot().keySet()) {
          getLogWriter().info(" the snapshot is for region  " + regionName + " is : "
              + txState.getCurrentSnapshot().get(regionName));
        }

        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        Iterator itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null,
            false, false, true, (LocalRegion)r);

        int num = 0;
        /*while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          getLogWriter().info("txitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }*/
        //assertEquals(2, num);

        // after this start another insert in a separate thread and those put shouldn't be visible
        Runnable run = new Runnable() {
          @Override
          public void run() {
            r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
            ((LocalRegion)r).put(3, 3);
            ((LocalRegion)r).put(4, 4);
            r.getCache().getCacheTransactionManager().commit();
          }
        };
        Thread t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          getLogWriter().info("txitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion()
              + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(2, num);
        // should be visible if read directly from region
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
        r.getCache().getCacheTransactionManager().commit();

      }
    });

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        final Region r = cache.getRegion(regionName);
        // take new snapshot and all the data should be visisble
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        //itr will work on a snapshot. not other ops
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        TXState txState = txstate.getLocalTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        Iterator itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null,
            false, false, true, (LocalRegion)r);

        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
      }
    });
  }

  public void testSnapshotPutAllAPI() throws Exception {
    startVMs(0, 2);
    Properties props = new Properties();
    final Connection conn = TestUtil.getConnection(props);
    conn.createStatement();

    VM server1 = this.serverVMs.get(0);
    VM server2 = this.serverVMs.get(1);
    server1.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});
    server2.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        getLogWriter().info(" SKSK in server1 doing two operations. ");
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        final Region r = cache.getRegion(regionName);
        Map m = new HashMap();
        m.put(1, 1);
        m.put(2, 2);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        r.putAll(m);
        // even before commit it should be visible in both vm
        assertEquals(1, r.get(1));
        assertEquals(2, r.get(2));
        r.getCache().getCacheTransactionManager().commit();
      }
    });

    server2.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);

        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        //itr will work on a snapshot. not other ops
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        TXState txState = txstate.getLocalTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(2, num);
        r.getCache().getCacheTransactionManager().commit();

        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        // read the entries
        assertEquals(1, r.get(1));// get don't need to take from snapshot
        assertEquals(2, r.get(2));// get don't need to take from snapshot

        //itr will work on a snapshot.
        txstate = TXManagerImpl.getCurrentTXState();
        txState = txstate.getLocalTXState();

        for (String regionName : txState.getCurrentSnapshot().keySet()) {
          getLogWriter().info(" the snapshot is for region  " + regionName + " is : " + " snapshot " + Integer.toHexString(System.identityHashCode(txState.getCurrentSnapshot())) + " "
              + txState.getCurrentSnapshot().get(regionName));
        }

        txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        Iterator itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null,
            false, false, true, (LocalRegion)r);

        // after this start another insert in a separate thread and those put shouldn't be visible
        Runnable run = new Runnable() {
          @Override
          public void run() {
            Map m = new HashMap();
            m.put(3, 3);
            m.put(4, 4);
            ((LocalRegion)r).putAll(m);
          }
        };
        Thread t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          getLogWriter().info("txitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(2, num);
        // should be visible if read directly from region
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          getLogWriter().info("regitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
        r.getCache().getCacheTransactionManager().commit();


        // after this start another insert in a separate thread and those put shouldn't be visible
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        txstate = TXManagerImpl.getCurrentTXState();
        txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null,
            false, false, true, (LocalRegion)r);

        run = new Runnable() {
          @Override
          public void run() {
            r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
            Map m = new HashMap();
            m.put(5, 5);
            m.put(6, 6);
            ((LocalRegion)r).putAll(m);
            r.getCache().getCacheTransactionManager().commit();
          }
        };
        t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          getLogWriter().info("txitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
        // should be visible if read directly from region
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          getLogWriter().info("regitr : re.getVersionStamp() " + re.getVersionStamp().getRegionVersion() + " txState " + txState);
          if (!re.isTombstone())
            num++;
        }
        assertEquals(6, num);

        r.getCache().getCacheTransactionManager().commit();
      }
    });

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        final Region r = cache.getRegion(regionName);
        // take new snapshot and all the data should be visisble
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        //itr will work on a snapshot. not other ops
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        TXState txState = txstate.getLocalTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(6, num);
        r.getCache().getCacheTransactionManager().commit();
      }
    });
  }


  public void testNoConflict() throws Exception {
    startVMs(0, 2);
    Properties props = new Properties();
    final Connection conn = TestUtil.getConnection(props);
    conn.createStatement();

    VM server1 = this.serverVMs.get(0);
    VM server2 = this.serverVMs.get(1);
    server1.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});
    server2.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});

    server2.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);

        r.put(1, 1);
        r.put(2, 2);
        r.put(3, 3);
        r.put(4, 4);

        // after this start another insert in a separate thread and those put shouldn't be visible
        Runnable run = new Runnable() {
          @Override
          public void run() {
            ((LocalRegion)r).put(3, 6);
            ((LocalRegion)r).put(4, 8);
          }
        };
        Thread t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        run = new Runnable() {
          @Override
          public void run() {
            r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);

            ((LocalRegion)r).put(1, 2);
            ((LocalRegion)r).put(2, 4);
            r.getCache().getCacheTransactionManager().commit();
          }
        };
        t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        r.getCache().getCacheTransactionManager().commit();

        assertEquals(2, r.get(1));
        assertEquals(4, r.get(2));
        assertEquals(6, r.get(3));
        assertEquals(8, r.get(4));
      }
    });

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        assertEquals(2, r.get(1));
        assertEquals(4, r.get(2));
        assertEquals(6, r.get(3));
        assertEquals(8, r.get(4));

        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone())
            num++;
        }
        assertEquals(4, num);
        r.getCache().getCacheTransactionManager().commit();
      }
    });
  }

  public void testInsertDeleteUpdate() throws Exception {
    startVMs(0, 2);
    Properties props = new Properties();
    final Connection conn = TestUtil.getConnection(props);
    conn.createStatement();


    VM server1 = this.serverVMs.get(0);
    VM server2 = this.serverVMs.get(1);
    server1.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});
    server2.invoke(SnapShotTxDUnit.class, "createPR", new Object[]{regionName, 1, 1});

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        Map map = new HashMap();
        map.put(1, 1);
        map.put(2, 2);
        r.putAll(map);
        // even before commit it should be visible
        assertEquals(1, r.get(1));
        assertEquals(2, r.get(2));
        r.getCache().getCacheTransactionManager().commit();
      }
    });

    server2.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        //itr will work on a snapshot.
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        // read the entries
        assertEquals(1, r.get(1));// get don't need to take from snapshot
        assertEquals(2, r.get(2));// get don't need to take from snapshot
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        Iterator itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        // after this start another insert in a separate thread and those put shouldn't be visible
        Runnable run = new Runnable() {
          @Override
          public void run() {
            r.put(1, 2);
            r.destroy(2);
          }
        };
        Thread t = new Thread(run);
        t.start();
        try {
          t.join();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }

        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone()) {
            num++;
            // 1,1 and 2,2
            assertEquals(re.getKey(), re.getValue(null));
          }
        }
        assertEquals(2, num);
        // should be visible if read directly from region
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          if (!re.isTombstone()) {
            num++;
            assertEquals(1, re.getKey());
            assertEquals(2, re.getValue(null));
          }
        }
        assertEquals(1, num);
        r.getCache().getCacheTransactionManager().commit();

      }
    });

    server1.invoke(new SerializableRunnable() {
      @Override
      public void run() {
        final GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
        //take an snapshot again//gemfire level
        final Region r = cache.getRegion(regionName);
        //itr will work on a snapshot.
        r.getCache().getCacheTransactionManager().begin(IsolationLevel.SNAPSHOT, null);
        TXStateInterface txstate = TXManagerImpl.getCurrentTXState();
        Iterator txitr = txstate.getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        Iterator itr = ((LocalRegion)r).getSharedDataView().getLocalEntriesIterator(null, false, false, true, (LocalRegion)r);
        int num = 0;
        while (txitr.hasNext()) {
          RegionEntry re = (RegionEntry)txitr.next();
          if (!re.isTombstone()) {
            num++;
            assertEquals(1, re.getKey());
            assertEquals(2, re.getValue(null));
          }
        }
        assertEquals(1, num);
        // should be visible if read directly from region
        num = 0;
        while (itr.hasNext()) {
          RegionEntry re = (RegionEntry)itr.next();
          if (!re.isTombstone()) {
            num++;
            assertEquals(1, re.getKey());
            assertEquals(2, re.getValue(null));
          }
        }
        assertEquals(1, num);
        r.getCache().getCacheTransactionManager().commit();
      }
    });
  }

  @Override
  public String getLogLevel() {
    return "fine";
  }

}

