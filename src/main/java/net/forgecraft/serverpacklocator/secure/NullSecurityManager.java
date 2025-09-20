package net.forgecraft.serverpacklocator.secure;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

public final class NullSecurityManager implements IConnectionSecurityManager {
    public static final NullSecurityManager INSTANCE = new NullSecurityManager();

    private NullSecurityManager() {
    }

    @Override
    public boolean validateServerRequest(final ChannelHandlerContext ctx, final FullHttpRequest msg) {
        return true;
    }

    @Override
    public boolean needsAuthRequest() {
        return false;
    }
}
