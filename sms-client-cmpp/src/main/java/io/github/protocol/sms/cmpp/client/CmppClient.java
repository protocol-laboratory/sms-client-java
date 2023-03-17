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

package io.github.protocol.sms.cmpp.client;

import io.github.protocol.codec.cmpp.CmppConnect;
import io.github.protocol.codec.cmpp.CmppConnectBody;
import io.github.protocol.codec.cmpp.CmppConnectResp;
import io.github.protocol.codec.cmpp.CmppConnectRespBody;
import io.github.protocol.codec.cmpp.CmppConst;
import io.github.protocol.codec.cmpp.CmppDecoder;
import io.github.protocol.codec.cmpp.CmppEncoder;
import io.github.protocol.codec.cmpp.CmppHeader;
import io.github.protocol.codec.cmpp.CmppMessage;
import io.github.protocol.codec.cmpp.CmppSubmit;
import io.github.protocol.codec.cmpp.CmppSubmitBody;
import io.github.protocol.codec.cmpp.CmppSubmitResp;
import io.github.protocol.codec.cmpp.CmppSubmitRespBody;
import io.github.protocol.sms.client.util.BoundAtomicInt;
import io.github.protocol.sms.client.util.SslContextUtil;
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
import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class CmppClient extends SimpleChannelInboundHandler<CmppMessage> {

    private final CmppClientConfig config;

    private final BoundAtomicInt seq;

    private volatile CompletableFuture<ConnectResult> connectFuture;

    private final Map<Integer, CompletableFuture<SubmitResult>> submitFutures;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    private final Optional<SslContext> sslContextOp;

    public CmppClient(CmppClientConfig config) {
        this.config = config;
        this.seq = new BoundAtomicInt(0x7FFFFFFF);
        this.submitFutures = new ConcurrentHashMap<>();
        if (config.useSsl) {
            sslContextOp = Optional.of(SslContextUtil.buildFromJks(config.keyStorePath, config.keyStorePassword,
                    config.trustStorePath, config.trustStorePassword, config.skipSslVerify,
                    config.ciphers));
        } else {
            sslContextOp = Optional.empty();
        }
    }

    public void start() throws Exception {
        if (group != null) {
            throw new IllegalStateException("cmpp client already started");
        }
        log.info("begin start cmpp client, config is {}", config);
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
                        p.addLast(new CmppDecoder());
                        p.addLast(CmppEncoder.INSTANCE);
                        p.addLast(CmppClient.this);
                        if (config.useSsl) {
                            if (!sslContextOp.isPresent()) {
                                throw new IllegalStateException("ssl context not present");
                            }
                            p.addLast(sslContextOp.get().newHandler(ch.alloc()));
                        }
                    }
                });
        ChannelFuture channelFuture = bootstrap.connect().sync();
        if (channelFuture.isSuccess()) {
            log.info("cmpp client started");
        } else {
            log.error("cmpp client start failed", channelFuture.cause());
            throw new Exception("cmpp client start failed", channelFuture.cause());
        }
    }

    public CompletableFuture<ConnectResult> connectAsync(CmppConnectBody connectBody) {
        CompletableFuture<ConnectResult> future = new CompletableFuture<>();
        CmppHeader header = new CmppHeader(CmppConst.CONNECT_ID, seq.nextVal());
        ctx.writeAndFlush(new CmppConnect(header, connectBody)).addListener(f -> {
            if (f.isSuccess()) {
                connectFuture = future;
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<SubmitResult> submitAsync(CmppSubmitBody submitBody) {
        CompletableFuture<SubmitResult> future = new CompletableFuture<>();
        CmppHeader header = new CmppHeader(CmppConst.SUBMIT_ID, seq.nextVal());
        ctx.writeAndFlush(new CmppSubmit(header, submitBody)).addListener(f -> {
            if (f.isSuccess()) {
                submitFutures.put(header.sequenceId(), future);
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
    protected void channelRead0(ChannelHandlerContext ctx, CmppMessage msg) throws Exception {
        if (msg instanceof CmppConnect) {
            processConnect((CmppConnect) msg);
        } else if (msg instanceof CmppConnectResp) {
            processConnectResp((CmppConnectResp) msg);
        } else if (msg instanceof CmppSubmitResp) {
            processSubmitResp((CmppSubmitResp) msg);
        }
    }

    private void processConnect(CmppConnect cmppConnect) {
        throw new IllegalStateException("client side can't process connect");
    }

    private void processConnectResp(CmppConnectResp cmppConnectResp) {
        if (connectFuture == null) {
            throw new IllegalStateException("connect future is null");
        }
        CmppConnectRespBody body = cmppConnectResp.body();
        if (body.status() == 0) {
            connectFuture.complete(new ConnectResult(body.authenticatorISMG(), body.version()));
        } else {
            connectFuture.completeExceptionally(new Exception("connect failed, status is " + body.status()));
        }
    }

    private void processSubmitResp(CmppSubmitResp cmppSubmitResp) {
        CompletableFuture<SubmitResult> future = submitFutures.remove(cmppSubmitResp.header().sequenceId());
        if (future == null) {
            log.warn("submit future is null, sequence id is {}", cmppSubmitResp.header().sequenceId());
            return;
        }
        CmppSubmitRespBody body = cmppSubmitResp.body();
        if (body.result() == 0) {
            future.complete(new SubmitResult(body.msgId()));
        } else {
            future.completeExceptionally(new Exception("submit failed, result is " + body.result()));
        }
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
