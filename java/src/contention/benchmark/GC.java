/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package contention.benchmark;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class GC {

	/**
	 * Execute System.gc() if we the System.gc option is set.
	 *
	 * @return true if we did
	 */
	static public boolean runSystemGC() {
		
		System.out.println("******* RUNNING GARBAGE COLLECTOR *********");
		
		List<GarbageCollectorMXBean> enabledBeans = new ArrayList<>();

		long beforeGcCount = 0;
		for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
			long count = bean.getCollectionCount();
			if (count != -1) {
				enabledBeans.add(bean);
			}
		}

		for (GarbageCollectorMXBean bean : enabledBeans) {
			beforeGcCount += bean.getCollectionCount();
		}

		// Run the GC twice, and force finalization before each GCs.
		System.runFinalization();
		System.gc();
		System.runFinalization();
		System.gc();

		// Now make sure GC actually happened. We have to wait for two things:
		// a) That at least two collections happened, indicating GC work.
		// b) That counter updates have not happened for a while, indicating GC
		// work had ceased.
		//
		// Note there is an opportunity window for a concurrent GC to happen
		// before the first
		// System.gc() call, which would get counted towards our GCs. This race
		// is unresolvable
		// unless we have GC-specific information about the collection cycles,
		// and verify those
		// were indeed GCs triggered by us.

		final int MAX_WAIT_MSEC = 20 * 1000;

		if (enabledBeans.isEmpty()) {
			System.out.println("WARNING: MXBeans can not report GC info. System.gc() invoked, pessimistically waiting "
					+ MAX_WAIT_MSEC + " msecs");
			try {
				TimeUnit.MILLISECONDS.sleep(MAX_WAIT_MSEC);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			return true;
		}

		boolean gcHappened = false;

		long start = System.nanoTime();
		while (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < MAX_WAIT_MSEC) {
			try {
				TimeUnit.MILLISECONDS.sleep(200);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

			long afterGcCount = 0;
			for (GarbageCollectorMXBean bean : enabledBeans) {
				afterGcCount += bean.getCollectionCount();
			}

			if (!gcHappened) {
				if (afterGcCount - beforeGcCount >= 2) {
					gcHappened = true;
				}
			} else {
				if (afterGcCount == beforeGcCount) {
					// Stable!
					return true;
				}
				beforeGcCount = afterGcCount;
			}
		}

		if (gcHappened) {
			System.out.println(
					"WARNING: System.gc() was invoked but unable to wait while GC stopped, is GC too asynchronous?");
		} else {
			System.out.println(
					"WARNING: System.gc() was invoked but couldn't detect a GC occurring, is System.gc() disabled?");
		}
		return false;
	}

}
