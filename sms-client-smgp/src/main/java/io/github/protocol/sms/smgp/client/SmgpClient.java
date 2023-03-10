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

import io.github.protocol.codec.smgp.SmgpConst;
import io.github.protocol.codec.smgp.SmgpDecoder;
import io.github.protocol.codec.smgp.SmgpEncoder;
import io.github.protocol.codec.smgp.SmgpHeader;
import io.github.protocol.codec.smgp.SmgpLogin;
import io.github.protocol.codec.smgp.SmgpLoginBody;
import io.github.protocol.codec.smgp.SmgpLoginResp;
import io.github.protocol.codec.smgp.SmgpLoginRespBody;
import io.github.protocol.codec.smgp.SmgpMessage;
import io.github.protocol.codec.smgp.SmgpSubmit;
import io.github.protocol.codec.smgp.SmgpSubmitBody;
import io.github.protocol.codec.smgp.SmgpSubmitResp;
import io.github.protocol.codec.smgp.SmgpSubmitRespBody;
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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SmgpClient extends SimpleChannelInboundHandler<SmgpMessage> {

    private final SmgpClientConfig config;

    private final BoundAtomicInt seq;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    private volatile CompletableFuture<SmgpLoginRespBody> loginFuture;

    private final Map<Integer, CompletableFuture<SmgpSubmitRespBody>> submitFuture;

    public SmgpClient(SmgpClientConfig config) {
        this.config = config;
        this.seq = new BoundAtomicInt(0x7FFFFFFF);
        this.submitFuture = new ConcurrentHashMap<>();
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

    public CompletableFuture<SmgpLoginRespBody> loginAsync(SmgpLoginBody smgpLoginBody) {
        CompletableFuture<SmgpLoginRespBody> future = new CompletableFuture<>();
        SmgpHeader header = new SmgpHeader(SmgpConst.LOGIN_ID, seq.nextVal());
        ctx.writeAndFlush(new SmgpLogin(header, smgpLoginBody)).addListener(f -> {
            if (f.isSuccess()) {
                loginFuture = future;
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<SmgpSubmitRespBody> submitAsync(SmgpSubmitBody smgpSubmitBody) {
        CompletableFuture<SmgpSubmitRespBody> future = new CompletableFuture<>();
        SmgpHeader header = new SmgpHeader(SmgpConst.SUBMIT_ID, seq.nextVal());
        ctx.writeAndFlush(new SmgpSubmit(header, smgpSubmitBody)).addListener(f -> {
            if (f.isSuccess()) {
                submitFuture.put(header.sequenceID(), future);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SmgpMessage msg) throws Exception {
        if (msg instanceof SmgpLogin) {
            processLogin((SmgpLogin) msg);
        } else if (msg instanceof SmgpLoginResp) {
            processLoginResp((SmgpLoginResp) msg);
        } else if (msg instanceof SmgpSubmitResp) {
            processSubmitResp((SmgpSubmitResp) msg);
        }
    }

    private void processLogin(SmgpLogin smgpLogin) {
        throw new IllegalStateException("client side can't process login");
    }

    private void processLoginResp(SmgpLoginResp smgpLoginResp) {
        if (loginFuture == null) {
            throw new IllegalStateException("login future is null");
        }
        loginFuture.complete(smgpLoginResp.body());
    }

    private void processSubmitResp(SmgpSubmitResp smgpSubmitResp) {
        CompletableFuture<SmgpSubmitRespBody> future = submitFuture.remove(smgpSubmitResp.header().sequenceID());
        if (future == null) {
            log.warn("submit future is null, sequence id is {}", smgpSubmitResp.header().sequenceID());
            return;
        }
        future.complete(smgpSubmitResp.body());
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
