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
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.fgotn.rev240916.SendFgoduflexNotification;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.fgotn.rev240916.SendFgoduflexNotificationInput;
import org.opendaylight.yang.gen.v1.urn.ccsa.yang.acc.fgotn.rev240916.SendFgoduflexNotificationOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

public class SendFgoduflexNotificationProcessor extends ToasterServiceAbstractProcessor<SendFgoduflexNotificationInput, SendFgoduflexNotificationOutput>
        implements SendFgoduflexNotification {

    private final ToasterServiceImpl toasterService;

    public SendFgoduflexNotificationProcessor(ToasterServiceImpl toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public QName getIdentifier() {
        QName qname = SendFgoduflexNotification.QNAME;
        return QName.create(qname.getNamespace(), qname.getLocalName());
    }

    @Override
    public ListenableFuture<RpcResult<SendFgoduflexNotificationOutput>> invoke(SendFgoduflexNotificationInput input) {
        return toasterService.sendFgoduflexNotification(input);
    }

}
