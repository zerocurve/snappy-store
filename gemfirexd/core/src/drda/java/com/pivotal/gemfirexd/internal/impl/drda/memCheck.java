/*

   Derby - Class com.pivotal.gemfirexd.internal.impl.drda.memCheck

   Licensed to the Apache Software Foundation (ASF) under one or more
   contributor license agreements.  See the NOTICE file distributed with
   this work for additional information regarding copyright ownership.
   The ASF licenses this file to You under the Apache License, Version 2.0
   (the "License"); you may not use this file except in compliance with
   the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.

 */

/*
 * Changes for GemFireXD distributed data platform (some marked by "GemStone changes")
 *
 * Portions Copyright (c) 2010-2015 Pivotal Software, Inc. All rights reserved.
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

package com.pivotal.gemfirexd.internal.impl.drda;

import java.util.Date;

public class memCheck extends Thread {
	int delay = 200000;
	boolean stopNow = false;

public memCheck () {}

public  memCheck (int num) {
	delay = num;
}

public void run () {
	while (stopNow == false) {
		try {
			showmem();
			sleep(delay);
		} catch (java.lang.InterruptedException ie) {
			System.out.println("memcheck interrupted");
			stopNow = true;
		}
	}
}

// GemStone changes BEGIN
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="DM_GC")
// GemStone changes END
	public static String getMemInfo() {
	Runtime rt = null;
	rt = Runtime.getRuntime();
	rt.gc();
	return "total memory: " 
		+ rt.totalMemory()
		+ " free: "
		+ rt.freeMemory();
	
	}

	public static long totalMemory() {
		Runtime rt = Runtime.getRuntime();
		return rt.totalMemory();
	}

// GemStone changes BEGIN
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="DM_GC")
// GemStone changes END
	public static long freeMemory() {
		
		Runtime rt =  Runtime.getRuntime();
		rt.gc();
		return rt.freeMemory();
	}

	public static void showmem() {
	Date d = null;
	d = new Date();
	System.out.println(getMemInfo() + " " + d.toString());

}

// GemStone changes BEGIN
        @edu.umd.cs.findbugs.annotations.SuppressWarnings(value="RU_INVOKE_RUN")
// GemStone changes END
public static void main (String argv[]) {
	System.out.println("memCheck starting");
	memCheck mc = new memCheck();
	mc.run();
}
}
