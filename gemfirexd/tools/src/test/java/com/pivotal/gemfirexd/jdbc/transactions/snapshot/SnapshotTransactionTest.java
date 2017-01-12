package com.pivotal.gemfirexd.jdbc.transactions.snapshot;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.gemstone.gemfire.internal.cache.GemFireCacheImpl;
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


  public void testReadSnapshotOnReplicatedTable() throws Exception {
    Connection conn = getConnection();
    Statement st = conn.createStatement();
    st.execute("Create table t1 (c1 int not null , c2 int not null, "
        + "primary key(c1)) replicate persistent enable concurrency checks"+getSuffix());
    conn.commit();
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
    ResultSet rs2 = st.executeQuery("Select * from t1 where c1 = 10");
    ResultSet rs3 = st.executeQuery("Select * from t1 where c1 > 5");
    ResultSet rs4 = st.executeQuery("Select * from t1 where c2 > 20");
    ResultSet rs5 = st.executeQuery("Select * from t1 where c2 = 20");
    // do some insert operation in different transaction
    doInsertOpsInTx();
    // even after commit of above tx, as the below was started earlier
    // it shouldn't see entry of previous tx.
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
    conn.commit();
    conn = getConnection();
    conn.setTransactionIsolation(getIsolationLevel());
    //conn.setAutoCommit(false);

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
    conn.commit(); // commit two rows.

    rs = st.executeQuery("Select * from t1");
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

    rs = st.executeQuery("Select * from t1");
    doInsertOpsInTx();
    numRows = 0;
    while (rs.next()) {
      numRows++;
    }
    assertEquals("ResultSet should contain one row ", 1, numRows);

    st.execute("truncate table t1");

    st.execute("insert into t1 values (10, 10)");
    st.execute("insert into t1 values (20, 20)");
    conn.commit();
    st.execute("delete from t1 where c1=10");
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
    doInsertOpsInTx();
//
    numRows = 0;
    while (rs.next()) {
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

  // test putAll path
  // test contains path
  // test local index path

  //foreign key?


  public void testSnapshotAgainstUpdateOperations() throws Exception {

  }

  public void testSnapshotAgainstDeleteOperations() throws Exception {

  }

  public void testSnapshotAgainstMultipleTable() throws Exception {

  }

  public void testSnapshotAgainstMultipleTableInsert() throws Exception {

  }

  public void testSnapshotAgainstMultipleTableDelete() throws Exception {

  }

  private void doInsertOpsInTx() throws SQLException, InterruptedException {
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

          conn2.commit();
          ResultSet rs = st.executeQuery("Select * from t1");
          int numRows = 0;
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


  protected int getIsolationLevel() {
    return Connection.TRANSACTION_NONE;
  }


  protected String getSuffix() {
    return " ";
  }

}

