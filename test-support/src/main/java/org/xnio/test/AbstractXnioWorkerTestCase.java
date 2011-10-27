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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.xnio.OptionMap;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Xnio;
import org.xnio.XnioWorker;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractXnioWorkerTestCase {

    protected final Xnio xnio;
    protected final Pool<ByteBuffer> bufferPool;
    private XnioWorker worker;

    protected AbstractXnioWorkerTestCase(final Xnio xnio, final Pool<ByteBuffer> bufferPool) {
        this.xnio = xnio;
        this.bufferPool = bufferPool;
    }

    @Before
    public void setup() throws IOException {
        worker = xnio.createWorker(getWorkerOptions());
    }

    @After
    public void teardown() throws InterruptedException {
        worker.shutdown();
        worker.awaitTermination(5L, TimeUnit.SECONDS);
        worker = null;
    }

    protected XnioWorker getWorker() {
        return worker;
    }

    protected Pooled<ByteBuffer> allocate() {
        return bufferPool.allocate();
    }

    protected OptionMap getWorkerOptions() {
        return OptionMap.EMPTY;
    }
}
