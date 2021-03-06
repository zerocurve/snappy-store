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
package com.gemstone.gemfire.cache.snapshot;

import java.io.File;
import java.io.FileFilter;
import java.util.Map.Entry;

import com.examples.snapshot.MyObject;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.snapshot.RegionGenerator.RegionType;
import com.gemstone.gemfire.cache.snapshot.RegionGenerator.SerializationType;
import com.gemstone.gemfire.cache.snapshot.SnapshotOptions.SnapshotFormat;

public class CacheSnapshotJUnitTest extends SnapshotTestCase {
  public void testExportAndImport() throws Exception {
    for (final RegionType rt : RegionType.values()) {
      for (final SerializationType st : SerializationType.values()) {
        Region<Integer, MyObject> region = rgen.createRegion(cache, ds.getName(), rt, "test-" + rt.name() + "-" + st.name());
        region.putAll(createExpected(st));
      }
    }

    // save all regions
    cache.getSnapshotService().save(snaps, SnapshotFormat.GEMFIRE);

    for (final RegionType rt : RegionType.values()) {
      for (final SerializationType st : SerializationType.values()) {
        String name = "test-" + rt.name() + "-" + st.name();
        
        Region<Integer, MyObject> region = cache.getRegion(name);
        region.destroyRegion();
        
        rgen.createRegion(cache, ds.getName(), rt, name);
      }
    }
    
    // load all regions
    cache.getSnapshotService().load(snaps, SnapshotFormat.GEMFIRE);
    
    for (final RegionType rt : RegionType.values()) {
      for (final SerializationType st : SerializationType.values()) {
        Region<Integer, MyObject> region = cache.getRegion("test-" + rt.name() + "-" + st.name());
        for (Entry<Integer, MyObject> entry : createExpected(st).entrySet()) {
          assertEquals("Comparison failure for " + rt.name() + "/" + st.name(), 
              entry.getValue(), region.get(entry.getKey()));
        }
      }
    }
  }

  public void testFilter() throws Exception {
    for (final RegionType rt : RegionType.values()) {
      for (final SerializationType st : SerializationType.values()) {
        Region<Integer, MyObject> region = rgen.createRegion(cache, ds.getName(), rt, "test-" + rt.name() + "-" + st.name());
        region.putAll(createExpected(st));
      }
    }
    
    SnapshotFilter<Object, Object> even = new SnapshotFilter<Object, Object>() {
      @Override
      public boolean accept(Entry<Object, Object> entry) {
        return ((Integer) entry.getKey()) % 2 == 0;
      }
    };
    
    SnapshotFilter<Object, Object> odd = new SnapshotFilter<Object, Object>() {
      @Override
      public boolean accept(Entry<Object, Object> entry) {
        return ((Integer) entry.getKey()) % 2 == 1;
      }
    };

    // save even entries
    CacheSnapshotService css = cache.getSnapshotService();
    SnapshotOptions<Object, Object> options = css.createOptions().setFilter(even);
    cache.getSnapshotService().save(snaps, SnapshotFormat.GEMFIRE, options);

    for (final RegionType rt : RegionType.values()) {
      for (final SerializationType st : SerializationType.values()) {
        Region region = cache.getRegion("test-" + rt.name() + "-" + st.name());
        region.destroyRegion();
        rgen.createRegion(cache, ds.getName(), rt, "test-" + rt.name() + "-" + st.name());
      }
    }

    // load odd entries
    File[] snapshots = snaps.listFiles(new FileFilter() {
      @Override
      public boolean accept(File pathname) {
        return pathname.getName().startsWith("snapshot-");
      }
    });
    
    options = css.createOptions().setFilter(odd);
    css.load(snapshots, SnapshotFormat.GEMFIRE, options);
    
    for (final RegionType rt : RegionType.values()) {
      for (final SerializationType st : SerializationType.values()) {
        Region region = cache.getRegion("test-" + rt.name() + "-" + st.name());
        assertEquals("Comparison failure for " + rt.name() + "/" + st.name(), 0, region.size());
      }
    }
  }
}
