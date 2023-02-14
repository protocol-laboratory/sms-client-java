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

package io.github.protocol.sms.cngp.client;

import io.github.protocol.codec.cngp.CngpConst;
import io.github.protocol.codec.cngp.CngpDecoder;
import io.github.protocol.codec.cngp.CngpEncoder;
import io.github.protocol.codec.cngp.CngpExit;
import io.github.protocol.codec.cngp.CngpExitResp;
import io.github.protocol.codec.cngp.CngpHeader;
import io.github.protocol.codec.cngp.CngpLogin;
import io.github.protocol.codec.cngp.CngpLoginBody;
import io.github.protocol.codec.cngp.CngpLoginResp;
import io.github.protocol.codec.cngp.CngpLoginRespBody;
import io.github.protocol.codec.cngp.CngpMessage;
import io.github.protocol.codec.cngp.CngpSubmit;
import io.github.protocol.codec.cngp.CngpSubmitBody;
import io.github.protocol.codec.cngp.CngpSubmitResp;
import io.github.protocol.codec.cngp.CngpSubmitRespBody;
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
public class CngpClient extends SimpleChannelInboundHandler<CngpMessage> {

    private final CngpClientConfig config;

    private final BoundAtomicInt seq;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    private volatile CompletableFuture<CngpLoginRespBody> loginFuture;

    private final Map<Integer, CompletableFuture<CngpSubmitRespBody>> submitFutures;

    private volatile CompletableFuture<Void> exitFuture;

    public CngpClient(CngpClientConfig config) {
        this.config = config;
        this.seq = new BoundAtomicInt(0x7FFFFFFF);
        this.submitFutures = new ConcurrentHashMap<>();
    }

    public void start() throws Exception {
        if (group != null) {
            throw new IllegalStateException("cngp client already started");
        }
        log.info("begin start cngp client, config is {}", config);
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
                        p.addLast(new CngpDecoder());
                        p.addLast(CngpEncoder.INSTANCE);
                        p.addLast(CngpClient.this);
                    }
                });
        ChannelFuture channelFuture = bootstrap.connect().sync();
        if (channelFuture.isSuccess()) {
            log.info("cngp client started");
        } else {
            log.error("cngp client start failed", channelFuture.cause());
            throw new Exception("cngp client start failed", channelFuture.cause());
        }
    }

    public CompletableFuture<CngpLoginRespBody> loginAsync(CngpLoginBody loginBody) {
        CompletableFuture<CngpLoginRespBody> future = new CompletableFuture<>();
        CngpHeader header = new CngpHeader(CngpConst.LOGIN_ID, 0, seq.nextVal());
        ctx.writeAndFlush(new CngpLogin(header, loginBody)).addListener(f -> {
            if (f.isSuccess()) {
                loginFuture = future;
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<CngpSubmitRespBody> submitAsync(CngpSubmitBody submitBody) {
        CompletableFuture<CngpSubmitRespBody> future = new CompletableFuture<>();
        CngpHeader header = new CngpHeader(CngpConst.SUBMIT_ID, 0, seq.nextVal());
        ctx.writeAndFlush(new CngpSubmit(header, submitBody)).addListener(f -> {
            if (f.isSuccess()) {
                submitFutures.put(header.sequenceId(), future);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<Void> exitAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        CngpHeader header = new CngpHeader(CngpConst.EXIT_ID, 0, seq.nextVal());
        ctx.writeAndFlush(new CngpExit(header)).addListener(f -> {
            if (f.isSuccess()) {
                exitFuture = future;
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
    protected void channelRead0(ChannelHandlerContext ctx, CngpMessage msg) throws Exception {
        if (msg instanceof CngpLogin) {
            processLogin((CngpLogin) msg);
        } else if (msg instanceof CngpLoginResp) {
            processLoginResp((CngpLoginResp) msg);
        } else if (msg instanceof CngpSubmitResp) {
            processSubmitResp((CngpSubmitResp) msg);
        } else if (msg instanceof CngpExitResp) {
            processExitResp((CngpExitResp) msg);
        }
    }

    private void processLogin(CngpLogin cngpLogin) {
        throw new IllegalStateException("client side can't process login");
    }

    private void processLoginResp(CngpLoginResp cngpLoginResp) {
        if (loginFuture == null) {
            throw new IllegalStateException("login future is null");
        }
        loginFuture.complete(cngpLoginResp.body());
    }

    private void processSubmitResp(CngpSubmitResp cngpSubmitResp) {
        CompletableFuture<CngpSubmitRespBody> future = submitFutures.remove(cngpSubmitResp.header().sequenceId());
        if (future == null) {
            log.warn("submit future is null, sequence id is {}", cngpSubmitResp.header().sequenceId());
            return;
        }
        future.complete(cngpSubmitResp.body());
    }

    private void processExitResp(CngpExitResp cngpExitResp) {
        if (exitFuture == null) {
            log.warn("exit future is null, sequence id is {}", cngpExitResp.header().sequenceId());
            return;
        }
        exitFuture.complete(null);
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
