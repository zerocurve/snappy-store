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
/*
 * Created on Sep 13, 2005
 *
 * 
 */
package com.gemstone.gemfire.internal.cache;

import com.gemstone.gemfire.cache.AttributesFactory;
import com.gemstone.gemfire.cache.DataPolicy;
import com.gemstone.gemfire.cache.CacheException;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.RegionAttributes;
import com.gemstone.gemfire.cache.RegionEvent;
import com.gemstone.gemfire.cache.Scope;
import com.gemstone.gemfire.cache30.CacheSerializableRunnable;
import com.gemstone.gemfire.cache30.CacheTestCase;
import com.gemstone.gemfire.internal.cache.CacheObserverAdapter;
import com.gemstone.gemfire.internal.cache.CacheObserverHolder;
import dunit.*;

import java.util.Properties;

/**
 * @author ashahid
 * 
 *  
 */
public class MapClearGIITest extends CacheTestCase {

  protected static boolean wasGIIInProgressDuringClear = false;

  public MapClearGIITest(String name) {
    super(name);
  } 
  volatile static Region region;
  /*
  public void setUp() {
    Host host = Host.getHost(0);
    VM vm0 = host.getVM(0);
    VM vm1 = host.getVM(1);
    vm0.invoke(MapClearGIITest.class, "createCacheVM0");
    vm1.invoke(MapClearGIITest.class, "createCacheVM1");
    System.out.println("Cache created in successfully");
  }*/
/*
  public void tearDown() {
    Host host = Host.getHost(0);
    VM vm0 = host.getVM(0);
    VM vm1 = host.getVM(1);
    vm0.invoke(MapClearGIITest.class, "closeCache");
    vm1.invoke(MapClearGIITest.class, "closeCache");
  }*/

/*  public static void createCacheVM0() throws Exception {
    InitialImageOperation.slowImageProcessing = 200;
    Properties mprops = new Properties();
    // mprops.setProperty("mcast-port", "7777");
    
    ds = (new MapClearGIITest("Clear")).getSystem(mprops);
    //ds = DistributedSystem.connect(props);
    cache = CacheFactory.create(ds);
    CacheObserverImpl observer = new CacheObserverImpl();
    CacheObserverHolder.setInstance(observer);
    LocalRegion.ISSUE_CALLBACKS_TO_CACHE_OBSERVER = true;
  } //end of create cache for VM0

  public static void createCacheVM1() throws Exception {
    Properties mprops = new Properties();
    // mprops.setProperty("mcast-port", "7777");
    ds = (new MapClearGIITest("Clear")).getSystem(mprops);
    // ds = DistributedSystem.connect(null);
    cache = CacheFactory.create(ds);
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setDataPolicy(DataPolicy.REPLICATE);
    RegionAttributes attr = factory.create();
    region = cache.createRegion("map", attr);
    //region = region.createSubregion("map",attr);
    for (int i = 0; i < 10000; ++i) {
      region.put("" + i, "" + i);
    }
  }*/

  public static boolean checkImageStateFlag() throws Exception {
    Region rgn = new MapClearGIITest("dumb object to get cache").getCache().getRegion("/map");
    if (rgn == null) {
      fail("Map region not yet created");
    }
    if (((LocalRegion) rgn).getImageState().getClearRegionFlag()) {
      fail(
          "The image state clear region flag should have been cleared"
          + " (region size=" + rgn.size() + ")."
          + " Hence failing");
    }
    if (!wasGIIInProgressDuringClear) {
      fail(
          "The clear operation invoked from VM1 reached VM0 after the "
          + "GII completed, or it reached VM0 even before the region in "
          + " VM0 got inserted in the subregion Map"
          + " (region size=" + rgn.size() + ")."
          + " Hence failing");
    }
    if (rgn.size() != 0) {
      fail(
          "The clear operation invoked from VM1 should have made the "
          + "size of region zero. Hence failing. Size = "
          + rgn.size());
    }
    return true;
  }

  public static void createRegionInVm0() throws Exception {
    AttributesFactory factory = new AttributesFactory();
    factory.setScope(Scope.DISTRIBUTED_ACK);
    factory.setDataPolicy(DataPolicy.REPLICATE);
    factory.setConcurrencyChecksEnabled(true);
    RegionAttributes attr = factory.create();

    region = new MapClearGIITest("dumb object to get cache").getCache().createRegion("map", attr);

    // region = region.createSubregion("map",attr);
    getLogWriter().info("Region in VM0 created ");
  }
/*
  public static void closeCache() {
    try {
      cache.close();
      ds.disconnect();
    }
    catch (Exception ex) {
      ex.printStackTrace();
    }
  }*/

  public static void clearRegionInVm1() {
    // wait for profile of getInitialImage cache to show up
    final com.gemstone.gemfire.internal.cache.CacheDistributionAdvisor adv =
      ((com.gemstone.gemfire.internal.cache.DistributedRegion)region).getCacheDistributionAdvisor();
    final int expectedProfiles = 1;
    WaitCriterion ev = new WaitCriterion() {
      public boolean done() {
        int numProfiles;
        numProfiles = adv.adviseReplicates().size();
        return numProfiles == expectedProfiles;
      }
      public String description() {
        return null;
      }
    };
    DistributedTestCase.waitForCriterion(ev, 10 * 1000, 200, true);
    region.clear();
    assertEquals(0, region.size());
  }

  //test methods
  public void testClearImageStateFlag() throws Throwable {
    Host host = Host.getHost(0);
    VM vm0 = host.getVM(0);
    VM vm1 = host.getVM(1);
    //vm0.invoke(MapClearGIITest.class, "createCacheVM0");
    
    vm0.invoke(new CacheSerializableRunnable("createCacheVM0") {
      public void run2() throws CacheException
      {
        InitialImageOperation.slowImageProcessing = 10;
        InitialImageOperation.slowImageSleeps = 0;
        Properties mprops = new Properties();
        // mprops.setProperty("mcast-port", "7777");
        
        getSystem(mprops);
        //ds = DistributedSystem.connect(props);
        getCache();
        CacheObserverImpl observer = new CacheObserverImpl();
        CacheObserverHolder.setInstance(observer);
        LocalRegion.ISSUE_CALLBACKS_TO_CACHE_OBSERVER = true;
      }
    });
    vm1.invoke(new CacheSerializableRunnable("createCacheVM1") {
      public void run2() throws CacheException
      {
        Properties mprops = new Properties();
        // mprops.setProperty("mcast-port", "7777");
        getSystem(mprops);
        // ds = DistributedSystem.connect(null);
        getCache();
        AttributesFactory factory = new AttributesFactory();
        factory.setScope(Scope.DISTRIBUTED_ACK);
        factory.setDataPolicy(DataPolicy.REPLICATE);
        factory.setConcurrencyChecksEnabled(true);
        RegionAttributes attr = factory.create();
        region = createRootRegion("map", attr);
        //region = region.createSubregion("map",attr);
        for (int i = 0; i < 10000; ++i) {
          region.put("" + i, "" + i);
        }
      }
    });
    getLogWriter().info("Cache created in VM1 successfully");
    try {
      AsyncInvocation asyncGII = vm0.invokeAsync(MapClearGIITest.class, 
          "createRegionInVm0");
      // wait until vm0's gii has done 20 slow image sleeps (10ms*20 = 200ms)
      // before starting the clear
      vm0.invoke(new CacheSerializableRunnable("wait for sleeps") {
          public void run2() throws CacheException {
            WaitCriterion ev = new WaitCriterion() {
              public boolean done() {
                return InitialImageOperation.slowImageSleeps >= 20;
              }
              public String description() {
                return null;
              }
            };
            DistributedTestCase.waitForCriterion(ev, 30 * 1000, 200, true);
          }
        });
      // now that the gii has received some entries do the clear
      vm1.invoke(MapClearGIITest.class, "clearRegionInVm1");
      // wait for GII to complete
      DistributedTestCase.join(asyncGII, 30 * 1000, getLogWriter());
      if (asyncGII.exceptionOccurred()) {
        Throwable t = asyncGII.getException();
        fail("createRegionInVM0 failed", t);
      }
      assertTrue(vm0
          .invokeBoolean(MapClearGIITest.class, "checkImageStateFlag"));

      if (asyncGII.exceptionOccurred()) {
        fail("asyncGII failed", asyncGII.getException());
      }
				   
	  
    }
    catch (Exception e) {
      fail("Test failed", e);
    }
    finally {
      vm0.invoke(new SerializableRunnable("Set fast image processing") {
        public void run() {
          InitialImageOperation.slowImageProcessing = 0;
          InitialImageOperation.slowImageSleeps = 0;
        }
      });
      
    }
  }//end of test case

  public static class CacheObserverImpl extends CacheObserverAdapter {

    public void afterRegionClear(RegionEvent event) {
      getLogWriter().info("**********Received clear event in VM0 . ");
      Region rgn = event.getRegion();
      wasGIIInProgressDuringClear = ((LocalRegion) rgn).getImageState()
        .wasRegionClearedDuringGII();
      InitialImageOperation.slowImageProcessing = 0;
      InitialImageOperation.slowImageSleeps = 0;
      getLogWriter().info(
          "wasGIIInProgressDuringClear when clear event was received= "
              + wasGIIInProgressDuringClear);
    }
  }
}// end of test class

