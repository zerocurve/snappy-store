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

package com.gemstone.gemfire.cache.query.internal;

import java.util.*;
import com.gemstone.gemfire.cache.query.*;
import com.gemstone.gemfire.internal.i18n.LocalizedStrings;



/**
 * Predefined functions
 *
 * @version     $Revision: 1.1 $
 * @author      ericz
 */


public class CompiledFunction extends AbstractCompiledValue {
  private CompiledValue [] _args;
  private int _function;
  
  public CompiledFunction(CompiledValue [] args, int function) {
    _args = args;
    _function = function;
  }
  
  @Override
  public List getChildren() {
    return Arrays.asList(this._args);
  }
  
  
  public int getType() {
    return FUNCTION;
  }
  public int getFunction() {
    return this._function;
  }
  
  public Object evaluate(ExecutionContext context)
  throws FunctionDomainException, TypeMismatchException, NameResolutionException,
          QueryInvocationTargetException {
    if (this._function == LITERAL_element) {
      Object arg = _args[0].evaluate(context);
      return call(arg);
    } else if (this._function == LITERAL_nvl) {
      return Functions.nvl(_args[0],_args[1], context);
    } else if (this._function == LITERAL_to_date){
      return Functions.to_date(_args[0],_args[1], context);
    } else {  
      throw new QueryInvalidException(LocalizedStrings.CompiledFunction_UNSUPPORTED_FUNCTION_WAS_USED_IN_THE_QUERY.toLocalizedString());
    }
  }
  
  @Override
  public Set computeDependencies(ExecutionContext context)
  throws TypeMismatchException, AmbiguousNameException, NameResolutionException {
    int len =  this._args.length;
    for(int i = 0; i < len; i++) {
      context.addDependencies(this,this._args[i].computeDependencies(context));  
    }
    return context.getDependencySet(this, true);
  }
  
  private Object call(Object arg)
  throws FunctionDomainException, TypeMismatchException {
    Support.Assert(_function == LITERAL_element);
    return Functions.element(arg);
  }
  
  public CompiledValue [] getArguments() {
    return this._args;
  }
  
  @Override
  public void generateCanonicalizedExpression(StringBuffer clauseBuffer, ExecutionContext context)
  throws AmbiguousNameException, TypeMismatchException, NameResolutionException {
    clauseBuffer.insert(0, ')');
    int len = this._args.length;
    for(int i = len - 1; i > 0; i--) {
      _args[i].generateCanonicalizedExpression(clauseBuffer, context);
       clauseBuffer.insert(0, ',');
    }
    _args[0].generateCanonicalizedExpression(clauseBuffer, context);
    switch(this._function) {
      case LITERAL_nvl : 
        clauseBuffer.insert(0, "NVL(");
        break;
      case LITERAL_element : 
        clauseBuffer.insert(0, "ELEMENT(");
        break;
      case LITERAL_to_date : 
        clauseBuffer.insert(0, "TO_DATE(");
        break;    
       default :
        super.generateCanonicalizedExpression(clauseBuffer, context);
    }          
  }
}

