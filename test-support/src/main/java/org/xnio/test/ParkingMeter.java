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
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import static junit.framework.Assert.assertEquals;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class ParkingMeter {
    private final Parker parker;
    @SuppressWarnings("unused")
    private volatile int bits;

    private static final AtomicIntegerFieldUpdater<ParkingMeter> bitsUpdater = AtomicIntegerFieldUpdater.newUpdater(ParkingMeter.class, "bits");

    public ParkingMeter(final int count) {
        this(new Parker(), count);
    }

    public ParkingMeter(final Parker parker, final int count) {
        this.parker = parker;
        bits = (1 << count) - 1;
    }

    public void finish(int event) {
        if (event > 31) {
            throw new IllegalArgumentException("No such event");
        }
        final int c = (1 << event);
        int bits, newBits;
        do {
            bits = this.bits;
            if ((bits & c) == 0) {
                // duplicate
                return;
            }
            newBits = bits & ~c;
        } while (! bitsUpdater.compareAndSet(this, bits, newBits));
        if (newBits == 0) {
            parker.go();
        }
    }

    public void await() {
        parker.await();
    }

    public boolean await(long time, TimeUnit unit) {
        return parker.await(time, unit);
    }

    public void doAssert(long time, TimeUnit unit) {
        if (! parker.await(time, unit)) {
            assertEquals(toString(), 0, bits);
        }
    }

    public String toString() {
        int bits = this.bits;
        if (bits == 0) {
            return "ParkingMeter (finished)";
        } else {
            final StringBuilder b = new StringBuilder();
            b.append("ParkingMeter (waiting for:");
            while (bits > 0) {
                b.append(' ');
                b.append(Integer.numberOfTrailingZeros(bits));
                bits ^= Integer.lowestOneBit(bits);
            }
            b.append(')');
            return b.toString();
        }
    }
}
