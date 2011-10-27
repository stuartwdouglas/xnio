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

package org.xnio.nio.test;

import java.io.Closeable;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import org.xnio.BufferAllocator;
import org.xnio.Buffers;
import org.xnio.ChannelListener;
import org.xnio.IoFuture;
import org.xnio.IoUtils;
import org.xnio.OptionMap;
import org.xnio.Xnio;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.StreamChannel;
import org.xnio.test.AbstractStreamChannelTestCase;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class NioTcp2TestCase extends AbstractStreamChannelTestCase {

    public NioTcp2TestCase() {
        super(Xnio.getInstance("nio", NioTcp2TestCase.class.getClassLoader()), true, Buffers.allocatedBufferPool(BufferAllocator.BYTE_BUFFER_ALLOCATOR, 256));
    }

    private static final int SERVER_PORT = 12345;

    protected Closeable setUpConnection(final ChannelListener<? super StreamChannel> clientSideListener, final ChannelListener<? super StreamChannel> serverSideListener) throws IOException {
        final InetSocketAddress address = new InetSocketAddress(Inet4Address.getByAddress(new byte[] { 127, 0, 0, 1 }), SERVER_PORT);
        final IoFuture<ConnectedStreamChannel> futureServer = getWorker().acceptStream(address, serverSideListener, null, OptionMap.EMPTY);
        boolean ok = false;
        try {
            final IoFuture<ConnectedStreamChannel> futureClient = getWorker().connectStream(address, clientSideListener, OptionMap.EMPTY);
            final Closeable closeable = new Closeable() {
                public void close() throws IOException {
                    futureServer.addNotifier(IoUtils.closingNotifier(), null);
                    futureClient.addNotifier(IoUtils.closingNotifier(), null);
                    futureServer.cancel();
                    futureClient.cancel();
                }
            };
            futureClient.get();
            futureServer.get();
            ok = true;
            return closeable;
        } finally {
            if (! ok) {
                futureServer.addNotifier(IoUtils.closingNotifier(), null);
                futureServer.cancel();
            }
        }
    }
}
