package pohjavirta;

import org.xnio.Buffers;

import java.nio.ByteBuffer;

public class Util {

    // From org.projectodd.wunderboss.web.undertow.async.websocket.UndertowWebsocket
    public static byte[] toArray(ByteBuffer... payload) {
        if (payload.length == 1) {
            ByteBuffer buf = payload[0];
            if (buf.hasArray() && buf.arrayOffset() == 0 && buf.position() == 0) {
                return buf.array();
            }
        }
        int size = (int) Buffers.remaining(payload);
        byte[] data = new byte[size];
        for (ByteBuffer buf : payload) {
            buf.get(data);
        }
        return data;
    }
}
