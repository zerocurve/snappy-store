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
 * 
 */
package objects;

import com.gemstone.gemfire.pdx.PdxReader;
import com.gemstone.gemfire.pdx.PdxSerializable;
import com.gemstone.gemfire.pdx.PdxWriter;

import hydra.Log;

import java.io.IOException;

/**
 * @author lynn
 *
 */
public class PdxVersionedPortfolio extends VersionedPortfolio implements
    PdxSerializable {
  
  public PdxVersionedPortfolio() {
    myVersion = "testsVersions/version1/objects.PdxVersionedPortfolio";
  }

  /* (non-Javadoc)
   * @see com.gemstone.gemfire.pdx.PdxSerializable#toData(com.gemstone.gemfire.pdx.PdxWriter)
   */
  public void toData(PdxWriter writer) {
    Log.getLogWriter().info("In testsVersions/version1/objects.PdxVersionedPortfolio.toData, " +
        " calling myToData...");
    myToData(writer);
  }

  /* (non-Javadoc)
   * @see com.gemstone.gemfire.pdx.PdxSerializable#fromData(com.gemstone.gemfire.pdx.PdxReader)
   */
  public void fromData(PdxReader reader) {
    Log.getLogWriter().info("In testsVersions/version1/objects.PdxVersionedPortfolio.fromData, " +
        " calling myFromData...");
    myFromData(reader);
  }



}
