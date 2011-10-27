/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.xnio.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * A simple one-waiter synchronizer.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class Parker {
    private static final Object NOTHING = new Object();
    private static final Object DONE = new Object();

    private volatile Object thing = NOTHING;

    private static final AtomicReferenceFieldUpdater<Parker, Object> thingUpdater = AtomicReferenceFieldUpdater.newUpdater(Parker.class, Object.class, "thing");

    public void await() {
        Object thing;
        do {
            thing = this.thing;
            if (thing instanceof Thread) {
                if (thing != Thread.currentThread()) {
                    throw new IllegalStateException("Another thread is already waiting");
                } else {
                    // it's already us!
                    break;
                }
            }
            if (thing == DONE) {
                // already done
                return;
            }
        } while (! thingUpdater.compareAndSet(this, thing, Thread.currentThread()));
        while (this.thing != DONE) {
            LockSupport.park(this);
        }
    }

    public boolean await(long time, TimeUnit unit) {
        Object thing;
        do {
            thing = this.thing;
            if (thing instanceof Thread) {
                if (thing != Thread.currentThread()) {
                    throw new IllegalStateException("Another thread is already waiting");
                } else {
                    // it's already us!
                    break;
                }
            }
            if (thing == DONE) {
                // already done
                return true;
            }
        } while (! thingUpdater.compareAndSet(this, thing, Thread.currentThread()));
        long clock = System.nanoTime();
        long elapsed = 0L;
        long nanoCount = unit.toNanos(time);
        while (this.thing != DONE && elapsed < nanoCount) {
            LockSupport.parkNanos(this, nanoCount - elapsed);
            elapsed = System.nanoTime() - clock;
        }
        return this.thing == DONE;
    }

    public void go() {
        Object thing;
        do {
            thing = this.thing;
            if (thing == DONE) {
                return;
            }
        } while (! thingUpdater.compareAndSet(this, thing, DONE));
        if (thing instanceof Thread) {
            LockSupport.unpark((Thread) thing);
        }
    }
}
