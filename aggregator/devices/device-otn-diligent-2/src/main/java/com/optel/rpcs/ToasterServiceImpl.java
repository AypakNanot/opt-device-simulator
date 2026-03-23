/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.rpcs;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import io.lighty.netconf.device.requests.notification.NotificationPublishService;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.connection.rev240702.Capacity;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.connection.rev240702.capacity.cir.or.totalsize.ForCirBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.devm.rev240702.ServiceType;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.CreateEthConnectionInput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.CreateEthConnectionOutput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.CreateEthConnectionOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.ETH;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.create.eth.connection.output.err.or.ok.OperOkBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.create.eth.connection.output.err.or.ok.oper.ok.ConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.create.eth.connection.output.err.or.ok.oper.ok.CreateComponentBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.eth.rev240702.create.eth.connection.output.err.or.ok.oper.ok.PortsWithRoleBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.osu.rev240702.*;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.osu.rev240702.osu.adjustment.notification.RequestedCapacityBuilder;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.GetAllPmStateInput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.GetAllPmStateOutput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.performance.rev210131.GetAllPmStateOutputBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ToasterServiceImpl implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ToasterServiceImpl.class);

    private final ExecutorService executor;
    private NotificationPublishService notificationPublishService;

    public ToasterServiceImpl() {
        executor = Executors.newFixedThreadPool(1);
    }


    public void setNotificationPublishService(NotificationPublishService notificationPublishService) {
        this.notificationPublishService = notificationPublishService;
    }

    @Override
    public void close() {
        executor.shutdown();
    }

    public ListenableFuture<RpcResult<GetAllPmStateOutput>> getAllPmState(GetAllPmStateInput input) {
        return submit(() -> {
            GetAllPmStateOutputBuilder builder = new GetAllPmStateOutputBuilder();
            return RpcResultBuilder.success(builder.build()).build();
        });
    }


    public ListenableFuture<RpcResult<CreateEthConnectionOutput>> createEthConnection(CreateEthConnectionInput input) {
        return submit(() -> {
            CreateEthConnectionOutputBuilder builder = new CreateEthConnectionOutputBuilder();
            OperOkBuilder operOkBuilder = new OperOkBuilder();
            ConnectionBuilder connectionBuilder = new ConnectionBuilder();
            connectionBuilder.setName("connection=ETH1");
            connectionBuilder.setLabel("123");
            connectionBuilder.setLayerProtocolName(ETH.VALUE);
            var requestedCapacityBuilder = new org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.connection.rev240702.connection.RequestedCapacityBuilder();
            ForCirBuilder forCirBuilder = new ForCirBuilder();
            forCirBuilder.setCir(Uint64.valueOf(1000));
            forCirBuilder.setPir(Uint64.valueOf(1000));
            forCirBuilder.setCbs(Uint64.valueOf(20));
            forCirBuilder.setPbs(Uint64.valueOf(20));
            requestedCapacityBuilder.setCirOrTotalsize(forCirBuilder.build());
            connectionBuilder.setRequestedCapacity(requestedCapacityBuilder.build());
            connectionBuilder.setCtp(Set.of("PTP=/shelf=1/slot=1/port=1/CTP=65535", "FTP=/shelf=1/slot=1/port=201/CTP=65535"));
            connectionBuilder.setServiceType(ServiceType.EPL);
            operOkBuilder.setConnection(connectionBuilder.build());
            CreateComponentBuilder createComponentBuilder = new CreateComponentBuilder();
            createComponentBuilder.setCtpName(Set.of("PTP=/shelf=1/slot=2/port=101/CTP=1"));
            createComponentBuilder.setFtpName(Set.of("FTP=/shelf=1/slot=1/port=201"));
            operOkBuilder.setCreateComponent(createComponentBuilder.build());
            PortsWithRoleBuilder portsWithRoleBuilder = new PortsWithRoleBuilder();
            portsWithRoleBuilder.setEthPtpCtp("PTP=/shelf=1/slot=1/port=1/CTP=65535");
            portsWithRoleBuilder.setUlEthFtp("FTP=/shelf=1/slot=1/port=201");
            portsWithRoleBuilder.setUlEthFtpCtp("FTP=/shelf=1/slot=1/port=201/CTP=65535");
            portsWithRoleBuilder.setUlOduCtp("PTP=/shelf=1/slot=2/port=101/CTP=1");
            operOkBuilder.setPortsWithRole(portsWithRoleBuilder.build());
            builder.setErrOrOk(operOkBuilder.build());
            return RpcResultBuilder.success(builder.build()).build();
        });
    }

    private <T> ListenableFuture<RpcResult<T>> submit(java.util.concurrent.Callable<RpcResult<T>> task) {
        SettableFuture<RpcResult<T>> result = SettableFuture.create();
        executor.submit(() -> {
            try {
                result.set(task.call());
            } catch (Exception e) {
                LOG.error("Error executing task", e);
                result.setException(e);
            }
        });
        return result;
    }
}
