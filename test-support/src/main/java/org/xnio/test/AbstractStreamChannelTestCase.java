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

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jboss.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.xnio.ChannelListener;
import org.xnio.IoUtils;
import org.xnio.Pool;
import org.xnio.Pooled;
import org.xnio.Xnio;
import org.xnio.channels.StreamChannel;

import static org.junit.Assert.assertTrue;

/**
 * A battery of tests for regular bidirectional stream connections.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public abstract class AbstractStreamChannelTestCase extends AbstractXnioWorkerTestCase {

    private static final Logger log = Logger.getLogger("org.xnio.test.stream");

    private final boolean channelSupportsHalfClosed;
    private final List<Throwable> problems = new CopyOnWriteArrayList<Throwable>();

    protected AbstractStreamChannelTestCase(final Xnio xnio, final boolean channelSupportsHalfClosed, final Pool<ByteBuffer> bufferPool) {
        super(xnio, bufferPool);
        this.channelSupportsHalfClosed = channelSupportsHalfClosed;
    }

    public boolean isChannelSupportsHalfClosed() {
        return channelSupportsHalfClosed;
    }

    protected final void checkProblems() {
        for (Throwable problem : problems) {
            log.error("Test exception", problem);
        }
        assertTrue(problems.isEmpty());
    }

    protected abstract Closeable setUpConnection(final ChannelListener<? super StreamChannel> clientSideListener, final ChannelListener<? super StreamChannel> serverSideListener) throws IOException;

    protected void doConnectionTest(final Runnable body, final ChannelListener<? super StreamChannel> clientListener, final ChannelListener<? super StreamChannel> serverListener) throws Exception {
        final Closeable handle = setUpConnection(clientListener, serverListener);
        try {
            try {
                body.run();
                handle.close();
            } catch (Exception e) {
                log.errorf(e, "Error running body");
                throw e;
            } catch (Error e) {
                log.errorf(e, "Error running body");
                throw e;
            }
        } finally {
            IoUtils.safeClose(handle);
        }
    }

    @Before
    public void setupTest() {
        problems.clear();
    }

    private void doTestOneSideClose(boolean server) throws Exception {
        final ParkingMeter meter = new ParkingMeter(4);
        final AtomicBoolean closerOK = new AtomicBoolean(false);
        final AtomicBoolean closeeOK = new AtomicBoolean(false);
        final ChannelListener<StreamChannel> closerListener = new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                log.info("In closer open");
                try {
                    channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
                            log.info("In closer close");
                            meter.finish(0);
                        }
                    });
                    channel.getWriteSetter().set(new ChannelListener<StreamChannel>() {
                        public void handleEvent(final StreamChannel channel) {
                            try {
                                channel.close();
                                meter.finish(1);
                                closerOK.set(true);
                            } catch (IOException e) {
                                log.error("In closer", e);
                                throw new RuntimeException(e);
                            }
                        }
                    });
                    channel.resumeWrites();
                } catch (Throwable t) {
                    log.error("In closer", t);
                    throw new RuntimeException(t);
                }
            }
        };
        final ChannelListener<StreamChannel> closeeListener = new ChannelListener<StreamChannel>() {
            public void handleEvent(final StreamChannel channel) {
                log.info("In closee opened");
                channel.getCloseSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        log.info("In closee close");
                        meter.finish(2);
                    }
                });
                channel.getReadSetter().set(new ChannelListener<StreamChannel>() {
                    public void handleEvent(final StreamChannel channel) {
                        log.info("In closee readable");
                        try {
                            final Pooled<ByteBuffer> pooled = allocate();
                            int c;
                            do try {
                                c = channel.read(pooled.getResource());
                                if (c == 0) {
                                    return;
                                }
                                if (c == -1) {
                                    closeeOK.set(true);
                                    channel.close();
                                    meter.finish(3);
                                }
                                log.infof("closee read data (%d bytes)", Integer.valueOf(c));
                            } finally {
                                pooled.free();
                            } while (c > 0);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
                channel.resumeReads();
            }
        };
        doConnectionTest(new Runnable() {
            public void run() {
                meter.doAssert(1L, TimeUnit.SECONDS);
            }
        }, server ? closeeListener : closerListener, server ? closerListener : closeeListener);
        checkProblems();
    }

    @Test
    public void testClientSideClose() throws Exception {
        log.info("Test: test client-side close");
        doTestOneSideClose(false);
    }

    @Test
    public void testServerSideClose() throws Exception {
        log.info("Test: test server-side close");
        doTestOneSideClose(true);
    }


}
