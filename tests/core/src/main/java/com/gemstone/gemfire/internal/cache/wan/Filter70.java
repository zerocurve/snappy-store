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
package com.gemstone.gemfire.internal.cache.wan;

import java.io.Serializable;

import com.gemstone.gemfire.cache.wan.GatewayEventFilter;
import com.gemstone.gemfire.cache.wan.GatewayQueueEvent;

public class Filter70 implements GatewayEventFilter, Serializable {
  String Id = "Filter70";
  public int eventEnqued = 0;

  public int eventTransmitted = 0;

  public boolean beforeEnqueue(GatewayQueueEvent event) {
    if ((Long)event.getKey() >= 0 && (Long)event.getKey() < 500) {
      return false;
    }
    return true;
  }

  public boolean beforeTransmit(GatewayQueueEvent event) {
    eventEnqued++;
    return true;
  }

  public void close() {

  }

  @Override
  public String toString() {
    return Id;
  }

  public void afterAcknowledgement(GatewayQueueEvent event) {
  }
  
  @Override
  public boolean equals(Object obj){
    if(this == obj){
      return true;
    }
    if ( !(obj instanceof Filter70) ) return false;
    Filter70 filter = (Filter70)obj;
    return this.Id.equals(filter.Id);
  }
}