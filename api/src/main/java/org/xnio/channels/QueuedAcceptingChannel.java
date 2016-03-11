package org.xnio.channels;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Map;

/**
 * @author Stuart Douglas
 */
public interface QueuedAcceptingChannel<C> {

    void setAcceptTask(AcceptTask function);

    Map.Entry<C, Object> acceptEntry()  throws IOException;

    interface AcceptTask {
        Object create(SocketChannel channel);
    }

}
