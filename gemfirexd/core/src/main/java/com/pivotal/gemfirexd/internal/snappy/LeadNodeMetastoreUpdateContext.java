/*
 * Copyright (c) 2016 SnappyData, Inc. All rights reserved.
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

package com.pivotal.gemfirexd.internal.snappy;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import com.gemstone.gemfire.DataSerializer;
import com.gemstone.gemfire.internal.DataSerializableFixedID;
import com.gemstone.gemfire.internal.shared.Version;
import com.pivotal.gemfirexd.internal.engine.GfxdSerializable;
import com.pivotal.gemfirexd.internal.iapi.services.sanity.SanityManager;

public final class LeadNodeMetastoreUpdateContext implements GfxdSerializable {

  public enum Optype { REGISTER_TABLE, UNREGISTER_TABLE }

  private Optype type;
  private String tableIdentifier;
  private byte[] userSpecifiedSchema;
  private byte[] partitionColumns;
  private String provider;
  private byte[] options;
  private byte[] relation;

  public LeadNodeMetastoreUpdateContext() {

  }

  public LeadNodeMetastoreUpdateContext(Optype type,
      String tableIdentifier,
      byte[] userSpecifiedSchema,
      byte[] partitionColumns,
      String provider,
      byte[] options,
      byte[] relation) {
    this.type = type;
    this.tableIdentifier = tableIdentifier;
    this.userSpecifiedSchema = userSpecifiedSchema;
    this.partitionColumns = partitionColumns;
    this.provider = provider;
    this.options = options;
    this.relation = relation;
  }

  @Override
  public byte getGfxdID() {
    return LEAD_NODE_META_UPDATE_CTX;
  }

  @Override
  public int getDSFID() {
    return DataSerializableFixedID.GFXD_TYPE;
  }

  @Override
  public void toData(DataOutput out) throws IOException {
    DataSerializer.writeObject(type, out);
    DataSerializer.writeString(tableIdentifier, out);
    DataSerializer.writeByteArray(userSpecifiedSchema, out);
    DataSerializer.writeByteArray(partitionColumns, out);
    DataSerializer.writeString(provider, out);
    DataSerializer.writeByteArray(options, out);
    DataSerializer.writeByteArray(relation, out);
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    this.type = DataSerializer.readObject(in);
    this.tableIdentifier = DataSerializer.readString(in);
    this.userSpecifiedSchema = DataSerializer.readByteArray(in);
    this.partitionColumns = DataSerializer.readByteArray(in);
    this.provider = DataSerializer.readString(in);
    this.options = DataSerializer.readByteArray(in);
    this.relation = DataSerializer.readByteArray(in);
  }

  @Override
  public Version[] getSerializationVersions() {
    return new Version[0];
  }

  public String getTableIdentifier() {
    return tableIdentifier;
  }

  public byte[] getUserSpecifiedSchema() {
    return userSpecifiedSchema;
  }

  public byte[] getPartitionColumns() {
    return partitionColumns;
  }

  public String getProvider() {
    return provider;
  }

  public byte[] getOptions() {
    return options;
  }

  public byte[] getRelation() {
    return relation;
  }

  public Optype getType() {
    return type;
  }

}
