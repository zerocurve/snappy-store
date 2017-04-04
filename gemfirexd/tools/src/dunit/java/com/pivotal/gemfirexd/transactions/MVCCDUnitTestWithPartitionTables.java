package com.pivotal.gemfirexd.transactions;

/**
 * Created by sachin on 22/3/17.
 */
public class MVCCDUnitTestWithPartitionTables  extends MVCCDUnit {

  public MVCCDUnitTestWithPartitionTables(String name) {
    super(name);
  }


  @Override
  public String getSuffix() {
    return "partition_by column(intcol) ";
  }
}
