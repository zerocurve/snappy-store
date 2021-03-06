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
package com.gemstone.gemfire.internal.cache.execute;


public class MyFunctionExecutionException extends RuntimeException{
  
  /**
   * Creates new exception with given error message.
   * 
   */
  public MyFunctionExecutionException() {
  }

  /**
   * Creates new exception with given error message.
   * 
   * @param msg
   */
  public MyFunctionExecutionException(String msg) {
    super(msg);
  }

  /**
   * Creates new exception with given error message and optional nested
   * exception.
   * 
   * @param msg
   * @param cause
   */
  public MyFunctionExecutionException(String msg, Throwable cause) {
    super(msg, cause);
  }

  /**
   * Creates new exception given Throwable as a cause and source of
   * error message.
   * 
   * @param cause
   */
  public MyFunctionExecutionException(Throwable cause) {
    super(cause);
  }

}
