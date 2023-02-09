/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.protocol.sms.smgp.client;

import io.github.protocol.codec.smgp.SmgpDecoder;
import io.github.protocol.codec.smgp.SmgpEncoder;
import io.github.protocol.codec.smgp.SmgpMessage;
import io.github.protocol.sms.client.util.BoundAtomicInt;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmgpClient extends SimpleChannelInboundHandler<SmgpMessage> {

    private final SmgpClientConfig config;

    private final BoundAtomicInt seq;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    public SmgpClient(SmgpClientConfig config) {
        this.config = config;
        this.seq = new BoundAtomicInt(0x7FFFFFFF);
    }

    public void start() throws Exception {
        if (group != null) {
            throw new IllegalStateException("smgp client already started");
        }
        log.info("begin start smgp client, config is {}", config);
        if (config.ioThreadsNum > 0) {
            group = new NioEventLoopGroup(config.ioThreadsNum);
        } else {
            group = new NioEventLoopGroup();
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.remoteAddress(config.host, config.port);
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new SmgpDecoder());
                        p.addLast(SmgpEncoder.INSTANCE);
                        p.addLast(SmgpClient.this);
                    }
                });
        ChannelFuture channelFuture = bootstrap.connect().sync();
        if (channelFuture.isSuccess()) {
            log.info("smgp client started");
        } else {
            log.error("smgp client start failed", channelFuture.cause());
            throw new Exception("smgp client start failed", channelFuture.cause());
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SmgpMessage msg) throws Exception {
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
