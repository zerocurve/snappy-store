package com.pivotal.gemfirexd.jdbc.transactions.snapshot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.internal.cache.BucketRegion;
import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
import com.gemstone.gemfire.internal.cache.PartitionedRegion;
import com.gemstone.gemfire.internal.cache.TXManagerImpl;
import com.gemstone.gemfire.internal.cache.TXState;
import com.gemstone.gemfire.internal.cache.versions.RegionVersionHolder;
import com.gemstone.gemfire.internal.cache.versions.VersionSource;
import com.gemstone.org.jgroups.oswego.concurrent.CyclicBarrier;
import com.pivotal.gemfirexd.internal.engine.Misc;
import com.pivotal.gemfirexd.jdbc.JdbcTestBase;


public class SnapshotTransactionTest  extends JdbcTestBase {

  private GemFireCacheImpl cache;

  private boolean gotConflict = false;

  private volatile Throwable threadEx;

  public SnapshotTransactionTest(String name) {
    super(name);
  }

  @Override
  protected String reduceLogging() {
    return "config";
  }


  public void testCommitOnReplicatedTable1() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, "
        + "primary key(c1)) replicate"+getSuffix());
    //conn.commit();
    conn = getConnection();
    //conn.setTransactionIsolation(getIsolationLevel());
    //conn.setAutoCommit(true);

    st = conn.createStatement();
    //st.execute("insert into t1 values (10, 10)");

    //conn.rollback();// rollback.

    ResultSet rs = st.executeQuery("Select * from t1");
    assertFalse("ResultSet should be empty ", rs.next());
    rs.close();

    st.execute("insert into t1 values (10, 10)");
    st.execute("insert into t1 values (20, 20)");

    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        Connection conn = null;
        try {
          conn = getConnection();
          Statement st = conn.createStatement();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            // Checking number of rows returned, since ordering of results
            // is not guaranteed. We can write an order by query for this (another
            // test).
            numRows++;
          }
          assertEquals("ResultSet should contain two rows ", 2, numRows);
          rs.close();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    });
    t.start();
    t.join();

    //conn.commit(); // commit two rows.
    rs = st.executeQuery("Select * from t1");
    int numRows = 0;
    while (rs.next()) {
      // Checking number of rows returned, since ordering of results
      // is not guaranteed. We can write an order by query for this (another
      // test).
      numRows++;
    }
    assertEquals("ResultSet should contain two rows ", 2, numRows);

    // Close connection, resultset etc...
    rs.close();
    st.close();
    //conn.commit();
    conn.close();
  }

  public void testReadSnapshotOnReplicatedTable() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, "
        + "primary key(c1)) replicate persistent enable concurrency checks"+getSuffix());
    conn = getConnection();
    conn.setTransactionIsolation(getIsolationLevel());
    conn.setAutoCommit(false);

    st = conn.createStatement();
    st.execute("insert into t1 values (10, 10)");

    st.execute("insert into t1 values (20, 20)");

    ResultSet rs = st.executeQuery("Select * from t1");
    int numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // withing tx also the row count should be 2
    assertEquals("ResultSet should contain two rows ", 2, numRows);

    rs = st.executeQuery("Select * from t1 where c1=10");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // withing tx also the row count should be 1
    assertEquals("ResultSet should contain one rows ", 1, numRows);

    conn.commit();
    rs = st.executeQuery("Select * from t1");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain two rows ", 2, numRows);

    st.execute("delete from t1 where c1=10");
    conn.commit();
    rs = st.executeQuery("Select * from t1");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);


    //start a read tx and another tx for insert, current tx shouldn't see new entry
    rs = st.executeQuery("Select * from t1");
    //ResultSet rs2 = st.executeQuery("Select * from t1 where c1 = 10");
    //ResultSet rs3 = st.executeQuery("Select * from t1 where c1 > 5");
    //ResultSet rs4 = st.executeQuery("Select * from t1 where c2 > 20");
    // rs5 = st.executeQuery("Select * from t1 where c2 = 20");
    // do some insert operation in different transaction
    doInsertOpsInTx();
    // even after commit of above tx, as the below was started earlier
    // it shouldn't see entry of previous tx.
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);

//    numRows = 0;
//    while (rs2.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);

    conn.commit();
    // start a read tx, it should see all the changes.
    rs = st.executeQuery("Select * from t1 ");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain eight row ", 8, numRows);
    conn.commit();

    //TODO: start a read tx and another tx for delete, current tx should be able to see old entry



    //TODO: start a read tx and another tx for update, current tx should be able to see old entry



    // Close connection, resultset etc...
    rs.close();
    st.close();
    conn.commit();
    conn.close();
  }

  // only insert operations to ignore
  public void testReadSnapshotOnPartitionedTable() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, "
        + "primary key(c1)) partition by column (c1) enable concurrency checks "+getSuffix());
    //conn.commit();
    conn = getConnection();
    //conn.setTransactionIsolation(getIsolationLevel());
    //conn.setAutoCommit(true);

    st = conn.createStatement();

    st.execute("insert into t1 values (10, 10)");
    st.execute("insert into t1 values (20, 20)");

    ResultSet rs = st.executeQuery("Select * from t1");
    int numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // withing tx also the row count should be 2
    assertEquals("ResultSet should contain two row ", 2, numRows);

    rs = st.executeQuery("Select * from t1 where c1=10");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // withing tx also the row count should be 1
    assertEquals("ResultSet should contain one row ", 1, numRows);
    //conn.commit(); // commit two rows.

    rs = st.executeQuery("Select * from t1");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain two rows ", 2, numRows);
    //conn.commit();

    st.execute("delete from t1 where c1=10");
    //conn.commit();

    //start a read tx(different flavor) and another tx for insert, current tx shouldn't see new entry
    //TODO: Currently can't execute multiple query, getting rs closed exception

    rs = st.executeQuery("Select * from t1 ");//where c1=20");
    doInsertOpsInTx();
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);

    st.execute("truncate table t1");

    st.execute("insert into t1 values (10, 10)");
    st.execute("insert into t1 values (20, 20)");
    //conn.commit();
    st.execute("delete from t1 where c1=10");
    //conn.commit();

    rs = st.executeQuery("Select * from t1 where c1 = 30");
    //doInsertOpsInTx();
//
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 0, numRows);

    //conn.commit();
    rs = st.executeQuery("Select * from t1 where c1 > 1");
    doInsertOpsInTx();
//
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);
    //conn.commit();
    rs = st.executeQuery("Select * from t1 where c2 > 20");
    //doInsertOpsInTx();
//
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 7, numRows);

   // conn.commit();

//    ResultSet rs3 = st.executeQuery("Select * from t1 where c1 > 1");
//    ResultSet rs4 = st.executeQuery("Select * from t1 where c2 > 20");
//    ResultSet rs5 = st.executeQuery("Select * from t1 where c2 = 20");
    // do some insert operation in different transaction
   // doInsertOpsInTx();


//    numRows = 0;
//    while (rs.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);
//
//    numRows = 0;
//    while (rs3.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);
//
//    numRows = 0;
//    while (rs4.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 0, numRows);
//
//    numRows = 0;
//    while (rs5.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);
//
//
//    conn.commit();
//    // start a read tx, it should see all the changes.
//    rs = st.executeQuery("Select * from t1 ");
//    numRows = 0;
//    while (rs.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain eight row ", 8, numRows);
//    conn.commit();


    //TODO: start a read tx and another tx for delete, current tx should be able to see old entry


    //TODO: start a read tx and another tx for update, current tx should be able to see old entry


    // Close connection, resultset etc...
    rs.close();
    st.close();
    //conn.commit();
    conn.close();
  }

  // test putAll path
  // test contains path
  // test local index path
  //foreign key?


  public void testSnapshotAgainstUpdateOperations() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, c3 int not null,"
        + "primary key(c1)) partition by column (c1) enable concurrency checks "+getSuffix());
    conn.commit();
    conn = getConnection();
    conn.setTransactionIsolation(getIsolationLevel());
    //conn.setAutoCommit(false);

    st = conn.createStatement();

    st.execute("insert into t1 values (10, 10, 20)");
    st.execute("insert into t1 values (20, 20, 20)");

    ResultSet rs = st.executeQuery("Select * from t1");
    int numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // within tx also the row count should be 2
    assertEquals("ResultSet should contain two row ", 2, numRows);

    rs = st.executeQuery("Select * from t1 where c1=10");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // withing tx also the row count should be 1
    assertEquals("ResultSet should contain one row ", 1, numRows);
    conn.commit(); // commit two rows.

    // start a read tx
    rs = st.executeQuery("Select * from t1");
    // another thread update all row
    doUpdateOpsInTx();

    // iterate over the ResultSet
    numRows = 0;
    while (rs.next()) {
      numRows++;
      int c2 = rs.getInt("c3");
      assertEquals("C3 should be  20 ", 20, c2);
    }
    assertEquals("ResultSet should contain two rows ", 2, numRows);
    //assert that old value is returned
  }

  public void testSnapshotAgainstDeleteOperations() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, c3 int not null, "
        + "primary key(c1)) partition by column (c1) enable concurrency checks "+getSuffix());
    conn.commit();
    conn = getConnection();
    conn.setTransactionIsolation(getIsolationLevel());
    conn.setAutoCommit(false);

    st = conn.createStatement();

    st.execute("insert into t1 values (10, 20, 10)");
    st.execute("insert into t1 values (20, 30, 20)");

    ResultSet rs = st.executeQuery("Select * from t1");
    int numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    // withing tx also the row count should be 2
    assertEquals("ResultSet should contain two row ", 2, numRows);

    conn.commit(); // commit two rows.

    // start a read tx
    rs = st.executeQuery("Select * from t1");

    // another thread delete one row
    doDeleteOpsInTx();

    // iterate over the ResultSet
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain two row ", 2, numRows);
    //assert that old value is returned
  }

  public void testSnapshotAgainstMultipleTable() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, "
        + "primary key(c1)) partition by column (c1) enable concurrency checks "+getSuffix());

    st.execute("Create table t2 (c1 int not null , c2 int not null, "
        + "primary key(c1)) partition by column (c1) enable concurrency checks "+getSuffix());
    conn.commit();
    conn = getConnection();
    conn.setTransactionIsolation(getIsolationLevel());
    conn.setAutoCommit(false);

    st = conn.createStatement();

    st.execute("insert into t1 values (10, 10)");
    st.execute("insert into t1 values (20, 20)");

    st.execute("insert into t2 values (10, 10)");
    st.execute("insert into t2 values (20, 20)");

    conn.commit(); // commit two rows.

    ResultSet rs = st.executeQuery("Select * from t1");
    int numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain two rows ", 2, numRows);

    rs = st.executeQuery("Select * from t2");
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain two rows ", 2, numRows);
    conn.commit();

    st.execute("delete from t1 where c1=10");
    conn.commit();

    //start a read tx(different flavor) and another tx for insert, current tx shouldn't see new entry
    //TODO: Currently can't execute multiple query, getting rs closed exception

   // rs = st.executeQuery("Select * from t1");

    ResultSet rs2 = st.executeQuery("Select * from t2");

    doInsertOpsInTx();
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);


    numRows = 0;
    while (rs2.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 2, numRows);

    st.execute("truncate table t1");
    st.execute("truncate table t2");

    st.execute("insert into t1 values (10, 10)");
    st.execute("insert into t1 values (20, 20)");

    st.execute("insert into t2 values (10, 10)");
    st.execute("insert into t2 values (20, 20)");

    conn.commit();
    st.execute("delete from t1 where c1=10");
    st.execute("delete from t2 where c1=10");
    conn.commit();

    rs = st.executeQuery("Select * from t1 where c1 = 30");
    //doInsertOpsInTx();
//
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 0, numRows);

    conn.commit();
    rs = st.executeQuery("Select * from t1 where c1 > 1");
    rs2 = st.executeQuery("Select * from t2 where c1 > 1");
    doInsertOpsInTxMultiTable();
//
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);

    numRows = 0;
    while (rs2.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);

    conn.commit();
    rs = st.executeQuery("Select * from t1 where c2 > 20");
    //doInsertOpsInTx();
//
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 7, numRows);

    conn.commit();

//    ResultSet rs3 = st.executeQuery("Select * from t1 where c1 > 1");
//    ResultSet rs4 = st.executeQuery("Select * from t1 where c2 > 20");
//    ResultSet rs5 = st.executeQuery("Select * from t1 where c2 = 20");
    // do some insert operation in different transaction
    // doInsertOpsInTx();


//    numRows = 0;
//    while (rs.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);
//
//    numRows = 0;
//    while (rs3.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);
//
//    numRows = 0;
//    while (rs4.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 0, numRows);
//
//    numRows = 0;
//    while (rs5.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain one row ", 1, numRows);
//
//
//    conn.commit();
//    // start a read tx, it should see all the changes.
//    rs = st.executeQuery("Select * from t1 ");
//    numRows = 0;
//    while (rs.next()) {
//      numRows++;
//    }
//    assertEquals("ResultSet should contain eight row ", 8, numRows);
//    conn.commit();


    //TODO: start a read tx and another tx for delete, current tx should be able to see old entry


    //TODO: start a read tx and another tx for update, current tx should be able to see old entry


    // Close connection, resultset etc...
    rs.close();
    st.close();
    //conn.commit();
    conn.close();

  }

  public void testSnapshotAgainstMultipleTableInsert() throws Exception {

  }

  public void testSnapshotAgainstMultipleTableDelete() throws Exception {

  }

  private void doInsertOpsInTxMultiTable() throws SQLException, InterruptedException {
    final Connection conn2 = getConnection();
    Runnable r = new Runnable(){
      @Override
      public void run() {
        try {
          Statement st = conn2.createStatement();
          conn2.setTransactionIsolation(Connection.TRANSACTION_NONE);
          //conn2.setAutoCommit(false);
          st.execute("insert into t1 values (1, 30)");
          st.execute("insert into t1 values (2, 30)");
          st.execute("insert into t1 values (10, 30)");
          st.execute("insert into t1 values (123, 30)");
          st.execute("insert into t1 values (30, 30)");
          st.execute("insert into t1 values (40, 30)");
          st.execute("insert into t1 values (50, 30)");

          st.execute("insert into t2 values (1, 30)");
          st.execute("insert into t2 values (2, 30)");
          st.execute("insert into t2 values (123, 30)");
          st.execute("insert into t2 values (30, 30)");
          st.execute("insert into t2 values (40, 30)");
          st.execute("insert into t2 values (50, 30)");

          conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain eight rows ", 8, numRows);


          rs = st.executeQuery("Select * from t2");
          numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain eight rows ", 8, numRows);
          conn2.commit();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    };
    Thread t = new Thread(r);
    t.start();
    t.join();
  }

  private void doInsertOpsInTx() throws SQLException, InterruptedException {
    final Connection conn2 = getConnection();
    Runnable r = new Runnable(){
      @Override
      public void run() {
        try {
          Statement st = conn2.createStatement();
          //conn2.setTransactionIsolation(Connection.TRANSACTION_NONE);
          //conn2.setAutoCommit(true);
          st.execute("insert into t1 values (1, 30)");
          st.execute("insert into t1 values (2, 30)");
          st.execute("insert into t1 values (10, 30)");
          st.execute("insert into t1 values (123, 30)");
          st.execute("insert into t1 values (30, 30)");
          st.execute("insert into t1 values (40, 30)");
          st.execute("insert into t1 values (50, 30)");



          // conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain eight rows ", 8, numRows);
          //conn2.commit();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    };
    Thread t = new Thread(r);
    t.start();
    t.join();
  }

  private void doInsertOpsInTxForConcurrencytest() throws SQLException, InterruptedException {
    final Connection conn2 = getConnection();
    Runnable r = new Runnable(){
      @Override
      public void run() {
        try {
          Statement st = conn2.createStatement();
          //conn2.setTransactionIsolation(Connection.TRANSACTION_NONE);
          //conn2.setAutoCommit(true);
          st.execute("insert into t1 values (101, 30)");
          st.execute("insert into t1 values (102, 30)");
          st.execute("insert into t1 values (103, 30)");
          st.execute("insert into t1 values (104, 30)");
          st.execute("insert into t1 values (105, 30)");
          st.execute("insert into t1 values (106, 30)");
          st.execute("insert into t1 values (107, 30)");



         // conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain 41 rows ", 41, numRows);
          //conn2.commit();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    };
    Thread t = new Thread(r);
    t.start();
    t.join();
  }

  // do both..point update and scan update
  private void doUpdateOpsInTx() throws SQLException, InterruptedException {
    final Connection conn2 = getConnection();
    Runnable r = new Runnable(){
      @Override
      public void run() {
        try {
          Statement st = conn2.createStatement();
          conn2.setTransactionIsolation(Connection.TRANSACTION_NONE);
          conn2.setAutoCommit(false);
          st.execute("update t1 set c3=50 where c2 = 20");
          conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain eight rows ", 2, numRows);
          conn2.commit();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    };
    Thread t = new Thread(r);
    t.start();
    t.join();
  }

  private void doDeleteOpsInTx() throws SQLException, InterruptedException {
    final Connection conn2 = getConnection();
    Runnable r = new Runnable(){
      @Override
      public void run() {
        try {
          Statement st = conn2.createStatement();
          conn2.setTransactionIsolation(Connection.TRANSACTION_NONE);
          conn2.setAutoCommit(false);
          st.execute("delete from t1 where c2 = 20");
          conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain eight rows ", 1, numRows);
          conn2.commit();
        } catch (SQLException e) {
          e.printStackTrace();
        }
      }
    };
    Thread t = new Thread(r);
    t.start();
    t.join();
  }


  /**
   * Check supported isolation levels.
   */
  public void SURtestIsolationLevels() throws Exception {
    // try {
    Connection conn = getConnection();
    conn.setTransactionIsolation(Connection.TRANSACTION_NONE);
    conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
    conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
    conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

    try {
      conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      fail("expected failure in unsupported isolation-level SERIALIZABLE");
    } catch (SQLException ex) {
      if (!ex.getSQLState().equalsIgnoreCase("XJ045")) {
        throw ex;
      }
    }
    conn.close();
  }


  // only insert operations to ignore
  public void testReadSnapshotOnPartitionedTableInConcurrency() throws Exception {
    Connection conn = getConnection();
    GemFireCacheImpl cache = GemFireCacheImpl.getInstance();
    Statement st = conn.createStatement();
    // Use single bucket as it will be easy to test versions
    st.execute("Create table t1 (c1 int not null , c2 int not null, "
        + "primary key(c1)) partition by column (c1) buckets 1 enable concurrency checks " + getSuffix
        ());
    conn.commit();
    conn = getConnection();
    conn.setTransactionIsolation(getIsolationLevel());


    //Inserting 34 records to avoid null pointer exception while getting TxState
    for(int i=0;i<35;i++) {
      st.execute("insert into t1 values ("+i+", 10)");
    }


    //As there is only one bucket there will be only one bucket region
    PartitionedRegion region = (PartitionedRegion)Misc.getRegionForTableByPath("/APP/T1", false);
    BucketRegion bucketRegion = region.getDataStore().getAllLocalBucketRegions().iterator()
        .next();

    ResultSet rs = st.executeQuery("Select * from t1");

    rs.next();

    TXState txState = TXManagerImpl.getCurrentTXState().getLocalTXState();
    long initialVersion = getRegionVersionForTransaction(txState, bucketRegion);


    int numRows = 0;
    while (rs.next()) {
      numRows++;
    }

    st = conn.createStatement();

    // rvv.getCurrentVersion();
    // withing tx also the row count should be 34 as we have done rs.next once to begin transaction
    assertEquals("ResultSet should contain two row ", 34, numRows);

    st.execute("delete from t1 where c1=1");

    rs = st.executeQuery("Select * from t1");
    rs.next();
    TXState txState1 = TXManagerImpl.getCurrentTXState().getLocalTXState();
    long versionAfterDelete = getRegionVersionForTransaction(txState1, bucketRegion);
    doInsertOpsInTxForConcurrencytest();
    long actualVersionAfterInsert = getRegionVersionForTransaction(txState, bucketRegion);

    // The insert done in above method should no affect the snapshot of transaction
    assert (actualVersionAfterInsert == initialVersion);

    //Version after delete operation should be one greater than the initial version
    assert (versionAfterDelete == (initialVersion + 1));


    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 33, numRows);


    conn.commit();
    final Object msg = new Object();

    //Start new transaction
    rs = st.executeQuery("Select * from t1");

    rs.next();

    TXState txState2 = TXManagerImpl.getCurrentTXState().getLocalTXState();

    long versionBeforeExecutingThread = getRegionVersionForTransaction(txState2, bucketRegion);
    doInsertOpsInThread(msg);
    long versionAfterStartingThread = getRegionVersionForTransaction(txState2, bucketRegion);
    assert(versionBeforeExecutingThread == versionAfterStartingThread);


    //Sleep some time to let thread go in waiting state
    Thread.sleep(3000);
    conn.commit();
    rs = st.executeQuery("Select * from t1");
    rs.next();
    TXState txState3 = TXManagerImpl.getCurrentTXState().getLocalTXState();
    //Iterating rs till last record in order for cleaning up the transaction
    while(rs.next());
    long versionAfterExecutingThreadWithNewTx = getRegionVersionForTransaction(txState3,
        bucketRegion);


    assert (versionAfterExecutingThreadWithNewTx == (versionAfterStartingThread + 4));

    synchronized (msg) {
      //Notify thread
      msg.notify();
    }
    synchronized (msg) {
      msg.wait();
    }

    conn.commit();
    rs = st.executeQuery("Select * from t1");
    rs.next();
    //Get old snapshot version of previous transaction to see the effect
    versionAfterExecutingThreadWithNewTx = getRegionVersionForTransaction(txState3,
        bucketRegion);
    TXState txState4 = TXManagerImpl.getCurrentTXState().getLocalTXState();
    long versionAfterExecutingUpdate = getRegionVersionForTransaction(txState4,
        bucketRegion);
    assert(versionAfterExecutingUpdate == (versionAfterExecutingThreadWithNewTx+1));
    conn.commit();
    rs.close();
    st.close();
    conn.close();
  }

  protected int getIsolationLevel() {
    return Connection.TRANSACTION_NONE;
  }


  protected String getSuffix() {
    return " ";
  }

  private long getRegionVersionForTransaction(TXState txState, Region region) {
    long version = 0l;

    Map<String, Map<VersionSource,RegionVersionHolder>> expectedSnapshot = txState
        .getCurrentRvvSnapShot
            (region);
    version = expectedSnapshot.get(region.getFullPath()).values().iterator().next()
        .getVersion();
    return version;
  }

  private void doInsertOpsInThread(final Object msg) throws SQLException, InterruptedException {
    final Connection conn2 = getConnection();
    Runnable r = new Runnable(){
      @Override
      public void run() {
        try {
          Statement st = conn2.createStatement();
          conn2.setTransactionIsolation(Connection.TRANSACTION_NONE);
          //conn2.setAutoCommit(false);
          st.execute("insert into t1 values (210, 310)");
          st.execute("insert into t1 values (211, 311)");
          st.execute("insert into t1 values (212, 312)");
          st.execute("insert into t1 values (213, 314)");
          //msg.notify();
          // Wait for parent thread to verify version
          synchronized (msg) {
            msg.wait();
          }
          st.execute("update t1 set c2=410 where c1=210");
          // Wait for parent thread to verify version
          synchronized (msg) {
            msg.notify();
          }

          conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
          while (rs.next()) {
            numRows++;
          }
          assertEquals("ResultSet should contain eight rows ", 45, numRows);
          conn2.commit();
        } catch (SQLException e) {
          e.printStackTrace();
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
      }
    };
    Thread t = new Thread(r);
    t.start();
  }

}

