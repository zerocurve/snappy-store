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
package com.pivotal.gemfirexd.internal.engine.map;

import java.util.Random;
import java.util.UUID;

import junit.framework.TestCase;

public class DHMTest extends TestCase {

  public static void main(String[] args) {
    DHMTest t = new DHMTest();
    t.test1M();
    t.__test50M();
  }

  public void test1M() {

    DenseIntValueHashMap<String> map = new DenseIntValueHashMap<>(new DHMDefaultSerializer(), 100);
//    DenseIntValueHashMap<String> map = new DenseIntValueHashMap<>(new com.pivotal.gemfirexd.internal.engine.map.DHMBase64Serializer(), 32);
//    HashMap<String, Integer> map = new HashMap<>(32);
//    ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>(32);
//    TObjectIntHashMap map = new TObjectIntHashMap(32);

    final Random r = new Random(System.currentTimeMillis());

    final int count = 1000000;
    Runtime rt = Runtime.getRuntime();
    System.out.println("populating keys");
/*
    String[] keys = new String[count];
    for (int i = 0; i < count; i++) {
      keys[i] = UUID.randomUUID().toString();
    }
*/

    long lastReport = System.currentTimeMillis();
    System.gc();
    System.gc();
    System.gc();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println("starting inserts.. in-memory keys taking " + (rt.totalMemory() - rt.freeMemory()) / 1048576.0 + " mb");

    System.out.printf("Count %d, Memory = %.2f mb", map.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0).println();
    for (int i = 0; i < count; i++) {
      try {
        String k = UUID.randomUUID().toString();
        map.put(k, i%113);
        assert map.get(k) == i%113;
        if (System.currentTimeMillis() - lastReport > 1000) {
          System.gc();
          System.gc();
          System.gc();
          System.out.printf("\rCount %d, Memory = %.2f mb", map.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0).println();
          lastReport = System.currentTimeMillis();
        }
      } catch (RuntimeException e) {
        e.printStackTrace();
        break;
      }
    }

    System.gc();
    System.gc();
    System.gc();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.printf("\rCount %d, Memory = %.2f mb ", map.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0);

/*
    for (int c = 0; c < keys.length; c++) {
      Object o = map.get(keys[c]);
      assert o != null;
    }
*/


  }

  public void __test50M() {

    DenseIntValueHashMap<String> map = new DenseIntValueHashMap<>(new DHMDefaultSerializer(), 32);
//    com.pivotal.gemfirexd.internal.engine.map.DenseIntValueHashMap<String> map = new com.pivotal.gemfirexd.internal.engine.map.DenseIntValueHashMap<>(new com.pivotal.gemfirexd.internal.engine.map.DHMBase64Serializer(), 32);

    final int count = 50000000;
    final Runtime rt = Runtime.getRuntime();

    long lastReport = System.currentTimeMillis();
    System.gc();
    System.gc();
    System.gc();
    try {
      Thread.sleep(1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.out.println("starting inserts.. in-memory keys taking " + (rt.totalMemory() - rt.freeMemory()) / 1048576.0 + " mb");

    System.out.printf("Count %d, Memory = %.2f mb", map.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0).println();
    for (int i = 0; i < count; i++) {
      try {
        final String k = UUID.randomUUID().toString();
        map.put(k, i%113);
        if (System.currentTimeMillis() - lastReport > 10000) {
          System.out.printf("\rCount %d, Memory = %.2f mb", map.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0).println();
          lastReport = System.currentTimeMillis();
        }
      } catch (RuntimeException e) {
        e.printStackTrace();
        break;
      }
    }

    System.gc();
    System.gc();
    System.gc();
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    System.gc();
    System.gc();
    System.gc();
    System.out.printf("\rCount %d, Memory = %.2f mb ", map.size(), (rt.totalMemory() - rt.freeMemory()) / 1048576.0);
  }

}
