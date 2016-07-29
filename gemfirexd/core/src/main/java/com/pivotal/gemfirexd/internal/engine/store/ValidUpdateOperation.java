package com.pivotal.gemfirexd.internal.engine.store;

import com.gemstone.gemfire.LogWriter;
import com.gemstone.gemfire.cache.Region;
import com.pivotal.gemfirexd.internal.engine.Misc;
import com.pivotal.gemfirexd.internal.iapi.error.StandardException;
import com.pivotal.gemfirexd.internal.iapi.sql.Activation;
import com.pivotal.gemfirexd.internal.iapi.sql.PreparedStatement;
import com.pivotal.gemfirexd.internal.iapi.sql.ResultSet;
import com.pivotal.gemfirexd.internal.iapi.sql.conn.LanguageConnectionContext;
import com.pivotal.gemfirexd.internal.iapi.types.DataValueDescriptor;
import com.pivotal.gemfirexd.internal.impl.sql.GenericParameterValueSet;
import com.pivotal.gemfirexd.internal.impl.sql.GenericPreparedStatement;

/**
 * Created by rishim on 18/7/16.
 */
public class ValidUpdateOperation {


  public static boolean isValid(Region<?, ?> region, String predicateString,
      DataValueDescriptor[] otherKeyValues) throws
      StandardException {

    System.out.println("checking validity of " + predicateString);
    ResultSet r = null;
    try {
      // setup LCC and get the ResultSet
      LanguageConnectionContext lcc = Misc.getLanguageConnectionContext();
      final GemFireContainer container = (GemFireContainer)region
          .getUserAttribute();
      PreparedStatement queryStatement = lcc.prepareInternalStatement("SELECT * FROM "
              + container.getQualifiedTableName() + " WHERE " + predicateString,
          (short)0);

      Activation childActivation = queryStatement.getActivation(lcc, false, predicateString);

      GenericParameterValueSet gpvs = (GenericParameterValueSet)childActivation
          .getParameterValueSet();

      if (gpvs != null && gpvs.getParameterCount() > 0) {
        childActivation.setParameters(gpvs, queryStatement.getParameterTypes());
      }
      ((GenericPreparedStatement)queryStatement).setFlags(true, true);
      if (otherKeyValues != null) {
        for (int i = 0; i < otherKeyValues.length; ++i) {
          DataValueDescriptor paramValue = otherKeyValues[i];
          if(paramValue != null){
            Object value = paramValue.getObject();
            gpvs.setParameterAsObject(i, value, false);
          }

        }
      }

      r = queryStatement.execute(childActivation, true, 0L, true, true);

      if (r.getNextRow() != null) {
        return true;
      }
    } catch (Exception e) {
      e.printStackTrace();
      LogWriter logger = Misc.getCacheLogWriterNoThrow();
      if (logger != null) {
        logger.warning("ValidUpdateOperation: Error while checking validity", e);
      }
    } finally {
      if (r != null) {
        r.close(true);
      }
    }
    return false;
  }
}

