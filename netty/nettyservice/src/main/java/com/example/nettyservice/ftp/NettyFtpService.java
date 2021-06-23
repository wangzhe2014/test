package com.example.nettyservice.ftp;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.stream.ChunkedWriteHandler;

public class NettyFtpService {

    private static final String DEFAULT_URL = "/nettyservice";
    public void run(String url, int port) {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap serverBootstrap = new ServerBootstrap();
            serverBootstrap
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChildChannelHandler(url));

            //发起异步连接操作
            ChannelFuture future = serverBootstrap.bind("127.0.0.1", port).sync();
            System.out.println("http文件目录服务器启动, 网址是: http://127.0.0.1:" + port + url);

            //等待服务端监听端口关闭
            future.channel().closeFuture().sync();
        }catch (Exception e) {
            e.printStackTrace();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    private class ChildChannelHandler extends ChannelInitializer {
        private String url;
        public ChildChannelHandler(String url) {
            this.url = url;
        }

        @Override
        protected void initChannel(Channel channel) throws Exception {
            channel.pipeline()
                    .addLast("http-decoder", new HttpRequestDecoder())
                    .addLast("http-aggregator", new HttpObjectAggregator(65536))
                    .addLast("http-encoder", new HttpResponseEncoder())
                    .addLast("http-chunked", new ChunkedWriteHandler())
                    .addLast("fileServiceHandler", new HttpFileServerHandler(url));

        }
    }

    public static void main(String[] args) {
        new NettyFtpService().run(DEFAULT_URL,8081);
    }
}

