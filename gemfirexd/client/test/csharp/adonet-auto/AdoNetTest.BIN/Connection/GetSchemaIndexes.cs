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
 
using System;
using System.Data;
using System.Data.Common;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading;
using Pivotal.Data.GemFireXD;

namespace AdoNetTest.BIN.Connection
{
    /// <summary>
    /// Retrieves index information for each of the user define tables
    /// </summary>
    class GetSchemaIndexes : GFXDTest
    {
        public GetSchemaIndexes(ManualResetEvent resetEvent)
            : base(resetEvent)
        {
        }

        public override void Run(object context)
        {
            try
            {
                DataTable table = Connection.GetSchema("Indexes");

                if (table.Rows.Count <= 0)
                    Fail("GetSchema('Indexes') did not return information.");
                else
                    ParseDataTable(table);
            }
            catch (Exception e)
            {
                Fail(e);
            }
            finally
            {   
                base.Run(context);
            }
        }
    }
}
