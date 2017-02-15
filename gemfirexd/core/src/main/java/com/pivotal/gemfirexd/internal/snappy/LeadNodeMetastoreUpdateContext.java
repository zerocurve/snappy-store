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

public final class LeadNodeMetastoreUpdateContext implements GfxdSerializable {

  public enum Optype { REGISTER_TABLE, UNREGISTER_TABLE }

  private Optype type;
  private String tableIdentifier;
  private String userSpecifiedJsonSchema;


  private String schemaDDL;
  private String provider;
  private byte[] options;
  private byte[] mode;
  private Boolean isBuiltIn = true;
  private Boolean ifExists = false;
  private String indexIdentifier; //set only by index operations

  public LeadNodeMetastoreUpdateContext() {

  }

  public LeadNodeMetastoreUpdateContext(Optype type,
      String tableIdentifier,
      String provider,
      String userSpecifiedJsonSchema,
      String schemaDDL,
      byte[] mode,
      byte[] options,
      Boolean isBuiltIn,
      Boolean ifExists) {
    this.type = type;
    this.tableIdentifier = tableIdentifier;
    this.provider = provider;
    this.userSpecifiedJsonSchema = userSpecifiedJsonSchema;
    this.schemaDDL = schemaDDL;
    this.mode = mode;
    this.options = options;
    this.isBuiltIn = isBuiltIn;
    this.ifExists = ifExists;
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
    DataSerializer.writeString(userSpecifiedJsonSchema, out);
    DataSerializer.writeString(schemaDDL, out);
    DataSerializer.writeString(provider, out);
    DataSerializer.writeByteArray(mode, out);
    DataSerializer.writeByteArray(options, out);
    DataSerializer.writeBoolean(isBuiltIn, out);
    DataSerializer.writeBoolean(ifExists, out);
    DataSerializer.writeString(indexIdentifier, out);
  }

  @Override
  public void fromData(DataInput in) throws IOException, ClassNotFoundException {
    this.type = DataSerializer.readObject(in);
    this.tableIdentifier = DataSerializer.readString(in);
    this.userSpecifiedJsonSchema = DataSerializer.readString(in);
    this.schemaDDL = DataSerializer.readString(in);
    this.provider = DataSerializer.readString(in);
    this.mode = DataSerializer.readByteArray(in);
    this.options = DataSerializer.readByteArray(in);
    this.isBuiltIn = DataSerializer.readBoolean(in);
    this.ifExists = DataSerializer.readBoolean(in);
    this.indexIdentifier = DataSerializer.readString(in);
  }

  @Override
  public Version[] getSerializationVersions() {
    return new Version[0];
  }

  public String getTableIdentifier() {
    return tableIdentifier;
  }

  public String getProvider() {
    return provider;
  }

  public byte[] getOptions() {
    return options;
  }

  public Optype getType() {
    return type;
  }

  public String getUserSpecifiedJsonSchema() {
    return userSpecifiedJsonSchema;
  }

  public String getSchemaDDL() {
    return schemaDDL;
  }

  public byte[] getMode() {
    return mode;
  }

  public Boolean getIsBuiltIn() {
    return isBuiltIn;
  }

  public Boolean getIfExists() {
    return ifExists;
  }

  public String getIndexIdentifier() {
    return indexIdentifier;
  }

}
