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

package io.github.protocol.sms.smpp.client;

import io.github.protocol.codec.smpp.SmppDecoder;
import io.github.protocol.codec.smpp.SmppEncoder;
import io.github.protocol.codec.smpp.SmppMessage;
import io.github.protocol.codec.smpp.SmppSubmitSmResp;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

public class SmppClient extends SimpleChannelInboundHandler<SmppMessage> {

    private final SmppClientConfig config;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    public SmppClient(SmppClientConfig config) {
        this.config = config;
    }

    public void start() throws Exception {
        if (group != null) {
            throw new IllegalStateException("Smpp Client Already started");
        }
        if (config.ioThreadsNum > 0) {
            group = new NioEventLoopGroup(config.ioThreadsNum);
        } else {
            group = new NioEventLoopGroup();
        }
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.remoteAddress(config.host, config.port);
        SmppEncoder smppEncoder = new SmppEncoder();
        bootstrap.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline p = ch.pipeline();
                        p.addLast(new SmppDecoder());
                        p.addLast(smppEncoder);
                        p.addLast(this);
                    }
                });
        bootstrap.connect().sync();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SmppMessage msg) throws Exception {
        if (msg instanceof SmppSubmitSmResp) {
            SmppSubmitSmResp submitSmResp = (SmppSubmitSmResp) msg;
        }
    }

    private void processSubmitSmResp(SmppSubmitSmResp submitSmResp) {
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
