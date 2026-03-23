/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package com.optel.processors;

import com.google.common.util.concurrent.ListenableFuture;
import com.optel.rpcs.ToasterServiceImpl;
import io.lighty.netconf.device.processors.ToasterServiceAbstractProcessor;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rpc.rev200630.StartNotification;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rpc.rev200630.StartNotificationInput;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.notification.rpc.rev200630.StartNotificationOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

@SuppressWarnings("checkstyle:MemberName")
public class StartNotificationProcessor extends ToasterServiceAbstractProcessor<StartNotificationInput,
        StartNotificationOutput> implements StartNotification {

    private final ToasterServiceImpl toasterService;

    public StartNotificationProcessor(ToasterServiceImpl toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public QName getIdentifier() {
        QName qname = StartNotification.QNAME;
        return QName.create(qname.getNamespace(), qname.getRevision(), qname.getLocalName());
    }

    @Override
    public ListenableFuture<RpcResult<StartNotificationOutput>> invoke(StartNotificationInput input) {
        return toasterService.startNotification(input);
    }

}
