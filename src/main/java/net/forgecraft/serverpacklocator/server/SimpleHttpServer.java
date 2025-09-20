package net.forgecraft.serverpacklocator.server;

import net.forgecraft.serverpacklocator.secure.IConnectionSecurityManager;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.logging.LogUtils;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import org.slf4j.Logger;

/**
 * Simple Http Server for serving file and manifest requests to clients.
 */
public class SimpleHttpServer {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final NioEventLoopGroup PARENT_GROUP = new NioEventLoopGroup(1, new ThreadFactoryBuilder()
            .setNameFormat("ServerPack Locator Parent - %d")
            .setDaemon(true)
            .build());
    private static final NioEventLoopGroup CHILD_GROUP = new NioEventLoopGroup(1, new ThreadFactoryBuilder()
            .setNameFormat("ServerPack Locator Child - %d")
            .setDaemon(true)
            .build());

    private static final int MAX_CONTENT_LENGTH = 2 << 19;

    private SimpleHttpServer() {
        throw new IllegalArgumentException("Can not instantiate SimpleHttpServer.");
    }

    public static void run(int port, IConnectionSecurityManager securityManager, ServerFileManager fileManager) {
        final ServerBootstrap bootstrap = new ServerBootstrap()
                .group(PARENT_GROUP, CHILD_GROUP)
                .channel(NioServerSocketChannel.class)
                .handler(new ChannelInitializer<ServerSocketChannel>() {
                    @Override
                    protected void initChannel(final ServerSocketChannel ch) {
                        ch.pipeline().addLast(new ChannelInboundHandlerAdapter() {
                            @Override
                            public void channelActive(final ChannelHandlerContext ctx) {
                                LOGGER.info("ServerPack server active on port {}", port);
                            }
                        });
                    }
                })
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(final SocketChannel ch) {
                        ch.pipeline().addLast("codec", new HttpServerCodec());
                        ch.pipeline().addLast("aggregator", new HttpObjectAggregator(MAX_CONTENT_LENGTH));
                        ch.pipeline().addLast("request", new RequestHandler(
                                securityManager, fileManager
                        ));
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
        bootstrap.bind(port).syncUninterruptibly();
    }
}
