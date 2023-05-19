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

package io.github.protocol.sms.sgip.client;

import io.github.protocol.codec.sgip.SgipBind;
import io.github.protocol.codec.sgip.SgipBindBody;
import io.github.protocol.codec.sgip.SgipBindResp;
import io.github.protocol.codec.sgip.SgipBindRespBody;
import io.github.protocol.codec.sgip.SgipConst;
import io.github.protocol.codec.sgip.SgipDecoder;
import io.github.protocol.codec.sgip.SgipDeliverResp;
import io.github.protocol.codec.sgip.SgipDeliverRespBody;
import io.github.protocol.codec.sgip.SgipEncoder;
import io.github.protocol.codec.sgip.SgipHeader;
import io.github.protocol.codec.sgip.SgipMessage;
import io.github.protocol.codec.sgip.SgipReport;
import io.github.protocol.codec.sgip.SgipReportBody;
import io.github.protocol.codec.sgip.SgipReportResp;
import io.github.protocol.codec.sgip.SgipReportRespBody;
import io.github.protocol.codec.sgip.SgipSubmit;
import io.github.protocol.codec.sgip.SgipSubmitBody;
import io.github.protocol.codec.sgip.SgipSubmitResp;
import io.github.protocol.codec.sgip.SgipSubmitRespBody;
import io.github.protocol.codec.sgip.SgipTrace;
import io.github.protocol.codec.sgip.SgipTraceBody;
import io.github.protocol.codec.sgip.SgipTraceResp;
import io.github.protocol.codec.sgip.SgipTraceRespBody;
import io.github.protocol.codec.sgip.SgipUnbind;
import io.github.protocol.codec.sgip.SgipUnbindResp;
import io.github.protocol.codec.sgip.SgipUserRpt;
import io.github.protocol.codec.sgip.SgipUserRptBody;
import io.github.protocol.codec.sgip.SgipUserRptResp;
import io.github.protocol.codec.sgip.SgipUserRptRespBody;
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
public class SgipClient extends SimpleChannelInboundHandler<SgipMessage> {

    private final SgipClientConfig config;

    private final BoundAtomicInt seq;

    private EventLoopGroup group;

    private ChannelHandlerContext ctx;

    private final Optional<SslContext> sslContextOp;

    private volatile CompletableFuture<SgipBindRespBody> bindFuture;

    private volatile CompletableFuture<Void> unbindFuture;

    private final Map<Long, CompletableFuture<SgipSubmitRespBody>> submitFuture;

    private final Map<Long, CompletableFuture<SgipDeliverRespBody>> deliverFuture;

    private final Map<Long, CompletableFuture<SgipReportRespBody>> reportFuture;

    private final Map<Long, CompletableFuture<SgipUserRptRespBody>> userRptFuture;

    private final Map<Long, CompletableFuture<SgipTraceRespBody>> traceRptFuture;

    public SgipClient(SgipClientConfig config) {
        this.config = config;
        this.seq = new BoundAtomicInt(0x7FFFFFFF);
        this.submitFuture = new ConcurrentHashMap<>();
        this.deliverFuture = new ConcurrentHashMap<>();
        this.reportFuture = new ConcurrentHashMap<>();
        this.userRptFuture = new ConcurrentHashMap<>();
        this.traceRptFuture = new ConcurrentHashMap<>();
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
            throw new IllegalStateException("sgip client already started");
        }
        log.info("begin start sgip client, config is {}", config);
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
                        p.addLast(new SgipDecoder());
                        p.addLast(SgipEncoder.INSTANCE);
                        p.addLast(SgipClient.this);
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
            log.info("sgip client started");
        } else {
            log.error("sgip client start failed", channelFuture.cause());
            throw new Exception("sgip client start failed", channelFuture.cause());
        }
    }

    public CompletableFuture<SgipBindRespBody> bindAsync(SgipBindBody bindBody) {
        CompletableFuture<SgipBindRespBody> future = new CompletableFuture<>();
        SgipHeader sgipHeader = new SgipHeader(SgipConst.BIND_ID, seq.nextVal());
        ctx.writeAndFlush(new SgipBind(sgipHeader, bindBody)).addListener(f -> {
            if (f.isSuccess()) {
                bindFuture = future;
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<Void> unbindAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        SgipHeader sgipHeader = new SgipHeader(SgipConst.UNBIND_ID, seq.nextVal());
        ctx.writeAndFlush(new SgipUnbind(sgipHeader)).addListener(f -> {
            if (f.isSuccess()) {
                unbindFuture = future;
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<SgipSubmitRespBody> submitAsync(SgipSubmitBody submitBody) {
        CompletableFuture<SgipSubmitRespBody> future = new CompletableFuture<>();
        SgipHeader header = new SgipHeader(SgipConst.SUBMIT_ID, seq.nextVal());
        submitFuture.put(header.sequenceNumber(), future);
        ctx.writeAndFlush(new SgipSubmit(header, submitBody)).addListener(f -> {
            if (!f.isSuccess()) {
                future.completeExceptionally(f.cause());
                submitFuture.remove(header.sequenceNumber());
            }
        });
        return future;
    }

    public CompletableFuture<SgipReportRespBody> reportAsync(SgipReportBody reportBody) {
        CompletableFuture<SgipReportRespBody> future = new CompletableFuture<>();
        SgipHeader sgipHeader = new SgipHeader(SgipConst.REPORT_ID, seq.nextVal());
        ctx.writeAndFlush(new SgipReport(sgipHeader, reportBody)).addListener(f -> {
            if (f.isSuccess()) {
                reportFuture.put(sgipHeader.sequenceNumber(), future);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }


    public CompletableFuture<SgipUserRptRespBody> userRptAsync(SgipUserRptBody userRptBody) {
        CompletableFuture<SgipUserRptRespBody> future = new CompletableFuture<>();
        SgipHeader sgipHeader = new SgipHeader(SgipConst.USERRPT_ID, seq.nextVal());
        ctx.writeAndFlush(new SgipUserRpt(sgipHeader, userRptBody)).addListener(f -> {
            if (f.isSuccess()) {
                userRptFuture.put(sgipHeader.sequenceNumber(), future);
            } else {
                future.completeExceptionally(f.cause());
            }
        });
        return future;
    }

    public CompletableFuture<SgipTraceRespBody> traceAsync(SgipTraceBody traceBody) {
        CompletableFuture<SgipTraceRespBody> future = new CompletableFuture<>();
        SgipHeader sgipHeader = new SgipHeader(SgipConst.TRACE_ID, seq.nextVal());
        ctx.writeAndFlush(new SgipTrace(sgipHeader, traceBody)).addListener(f -> {
            if (f.isSuccess()) {
                traceRptFuture.put(sgipHeader.sequenceNumber(), future);
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
    protected void channelRead0(ChannelHandlerContext ctx, SgipMessage msg) throws Exception {
        if (msg instanceof SgipBind) {
            processBind((SgipBind) msg);
        } else if (msg instanceof SgipBindResp) {
            processBindResp((SgipBindResp) msg);
        } else if (msg instanceof SgipUnbindResp) {
            processUnbindResp((SgipUnbindResp) msg);
        } else if (msg instanceof SgipSubmitResp) {
            processSubmitResp((SgipSubmitResp) msg);
        } else if (msg instanceof SgipReportResp) {
            processReportResp((SgipReportResp) msg);
        } else if (msg instanceof SgipUserRptResp) {
            processUserRptResp((SgipUserRptResp) msg);
        } else if (msg instanceof SgipTraceResp) {
            processTraceResp((SgipTraceResp) msg);
        }
    }

    private void processBind(SgipBind sgipBind) {
        throw new IllegalStateException("client side can't process bind");
    }

    private void processBindResp(SgipBindResp sgipBind) {
        if (bindFuture == null) {
            throw new IllegalStateException("bind future is null");
        }
        bindFuture.complete(sgipBind.body());
    }

    private void processUnbindResp(SgipUnbindResp msg) {
        if (unbindFuture == null) {
            throw new IllegalStateException("unbind is null");
        }
        unbindFuture.complete(null);
    }

    private void processSubmitResp(SgipSubmitResp msg) {
        CompletableFuture<SgipSubmitRespBody> future = submitFuture.remove(msg.header().sequenceNumber());
        if (future == null) {
            log.warn("submit future is null, sequence number is {}", msg.header().sequenceNumber());
            return;
        }
        future.complete(msg.body());
    }

    private void processDeliverResp(SgipDeliverResp msg) {
        CompletableFuture<SgipDeliverRespBody> future = deliverFuture.remove(msg.header().sequenceNumber());
        if (future == null) {
            log.warn("deliver future is null, sequence number is {}", msg.header().sequenceNumber());
            return;
        }
        future.complete(msg.body());
    }

    private void processReportResp(SgipReportResp msg) {
        CompletableFuture<SgipReportRespBody> future = reportFuture.remove(msg.header().sequenceNumber());
        if (future == null) {
            log.warn("report future is null, sequence number is {}", msg.header().sequenceNumber());
            return;
        }
        future.complete(msg.body());
    }


    private void processUserRptResp(SgipUserRptResp msg) {
        CompletableFuture<SgipUserRptRespBody> future = userRptFuture.remove(msg.header().sequenceNumber());
        if (future == null) {
            log.warn("userRpt future is null, sequence number is {}", msg.header().sequenceNumber());
            return;
        }
        future.complete(msg.body());
    }

    private void processTraceResp(SgipTraceResp msg) {
        CompletableFuture<SgipTraceRespBody> future = traceRptFuture.remove(msg.header().sequenceNumber());
        if (future == null) {
            log.warn("trace future is null, sequence number is {}", msg.header().sequenceNumber());
            return;
        }
        future.complete(msg.body());
    }

    public void stop() {
        if (group != null) {
            group.shutdownGracefully();
        }
    }
}
