package com.pivotal.gemfirexd.internal.engine.store;

import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import com.gemstone.gemfire.LogWriter;
import com.gemstone.gemfire.cache.EntryEvent;
import com.gemstone.gemfire.cache.Region;
import com.gemstone.gemfire.cache.execute.internal.ValidOperation;
import com.gemstone.gemfire.internal.cache.BucketRegion;
import com.gemstone.gemfire.internal.cache.CachePerfStats;
import com.gemstone.gemfire.internal.cache.EntryEventImpl;
import com.gemstone.gemfire.internal.cache.LocalRegion;
import com.gemstone.gemfire.internal.cache.RegionEntry;
import com.gemstone.gemfire.internal.concurrent.ConcurrentSkipListMap;
import com.pivotal.gemfirexd.internal.catalog.types.RoutineAliasInfo;
import com.pivotal.gemfirexd.internal.engine.Misc;
import com.pivotal.gemfirexd.internal.engine.distributed.utils.GemFireXDUtils;
import com.pivotal.gemfirexd.internal.engine.expression.ExpressionCompiler;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.reference.SQLState;
import com.pivotal.gemfirexd.internal.iapi.services.context.ContextService;
import com.pivotal.gemfirexd.internal.iapi.sql.Activation;
import com.pivotal.gemfirexd.internal.iapi.sql.ParameterValueSet;
import com.pivotal.gemfirexd.internal.iapi.sql.PreparedStatement;
import com.pivotal.gemfirexd.internal.iapi.sql.compile.C_NodeTypes;
import com.pivotal.gemfirexd.internal.iapi.sql.conn.LanguageConnectionContext;
import com.pivotal.gemfirexd.internal.iapi.sql.conn.StatementContext;
import com.pivotal.gemfirexd.internal.iapi.sql.execute.CursorResultSet;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueDescriptor;
import com.pivotal.gemfirexd.internal.iapi.types.RowLocation;
import com.pivotal.gemfirexd.internal.iapi.types.SQLBoolean;
import com.pivotal.gemfirexd.internal.impl.jdbc.EmbedConnection;
import com.pivotal.gemfirexd.internal.impl.sql.GenericParameterValueSet;
import com.pivotal.gemfirexd.internal.impl.sql.GenericPreparedStatement;
import com.pivotal.gemfirexd.internal.impl.sql.compile.FromList;
import com.pivotal.gemfirexd.internal.impl.sql.compile.SubqueryList;
import com.pivotal.gemfirexd.internal.impl.sql.compile.ValueNode;

/**
 * Created by rishim on 18/7/16.
 */
public class ValidUpdateOperation {

  public static String TEST_SQL_COMMENT = "--THREAD_WAITS_FOR_SOME_TIME";

  public static boolean isValid(Region<?, ?> region, String predicateString,
      ParameterValueSet valueSet, DataValueDescriptor[] otherKeyValues) throws
      StandardException {
    System.out.println(" Cheking valid for  " + predicateString);

    // setup LCC and get the ResultSet
    LanguageConnectionContext lcc = Misc.getLanguageConnectionContext();
    final GemFireContainer container = (GemFireContainer)region
        .getUserAttribute();
    // create the SELECT query to be used for getting the keys to be evicted
    PreparedStatement queryStatement = lcc.prepareInternalStatement("SELECT 1 FROM "
            + container.getQualifiedTableName() + " WHERE " + predicateString,
        (short)0);

    Activation childActivation = queryStatement.getActivation(lcc, false, null);

    GenericParameterValueSet gpvs = (GenericParameterValueSet)childActivation
        .getParameterValueSet();
    if (gpvs != null && gpvs.getParameterCount() > 0) {
      childActivation.setParameters(gpvs, queryStatement.getParameterTypes());
    }
    ((GenericPreparedStatement)queryStatement).setFlags(true, true);
    if (otherKeyValues != null) {
      for (int i = 0; i < otherKeyValues.length; i++ ) {
        Object value = otherKeyValues[i].getObject();
        System.out.println("value for index " + i + " = " + value);
        gpvs.setParameterAsObject(i, value, false);
      }
    }


    EmbedConnection conn = null;
    StatementContext statementContext = null;
    boolean contextSet = false;
    boolean popContext = false;
    CursorResultSet resultSet = null;
    Throwable t = null;
    try {
      if (lcc == null) {
        // Refer Bug 42810.In case of WAN, a PK based insert is converted into
        // region.put since it bypasses GemFireXD layer, the LCC can be null.
        conn = GemFireXDUtils.getTSSConnection(true, true, false);
        conn.getTR().setupContextStack();
        contextSet = true;
        lcc = conn.getLanguageConnectionContext();
        // lcc can be null if the node has started to go down.
        if (lcc == null) {
          Misc.getGemFireCache().getCancelCriterion()
              .checkCancelInProgress(null);
        }
      } else {
        contextSet = false;
      }

      if (lcc != null) {
        lcc.pushMe();
        popContext = true;
        assert ContextService
            .getContextOrNull(LanguageConnectionContext.CONTEXT_ID) != null;
        statementContext = lcc.pushStatementContext(false, false,
            predicateString, null, false, 0L, true);
        statementContext.setSQLAllowed(RoutineAliasInfo.READS_SQL_DATA,
            true);

        resultSet = (CursorResultSet)queryStatement.execute(lcc, false, 0L);

        if (resultSet.getNextRow() != null) {
          System.out.println(" Retruning valid for  " + predicateString);
          return true;
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
      t = e;
      LogWriter logger = Misc.getCacheLogWriterNoThrow();
      if (logger != null) {
        logger.warning("GfxdEvictionCriteria: Error in Iterator creation", e);
      }
    } finally {
      if (statementContext != null) {
        lcc.popStatementContext(statementContext, t);
      }
      if (lcc != null && popContext) {
        lcc.popMe();
      }
      if (contextSet) {
        conn.getTR().restoreContextStack();
      }
    }
    return false;
  }
}

