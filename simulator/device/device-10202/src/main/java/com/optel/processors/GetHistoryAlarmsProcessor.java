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
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.GetHistoryAlarms;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.GetHistoryAlarmsInput;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.rpc.rev230426.GetHistoryAlarmsOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GetHistoryAlarmsProcessor extends ToasterServiceAbstractProcessor<GetHistoryAlarmsInput, GetHistoryAlarmsOutput>
        implements GetHistoryAlarms {

    private static final Logger LOG = LoggerFactory.getLogger(GetHistoryAlarmsProcessor.class);

    private final ToasterServiceImpl toasterService;
    private final QName qName = QName.create("urn:cmcc:yang:rpc", "get-history-alarms");

    public GetHistoryAlarmsProcessor(ToasterServiceImpl toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public QName getIdentifier() {
        return qName;
    }

    @Override
    public ListenableFuture<RpcResult<GetHistoryAlarmsOutput>> invoke(GetHistoryAlarmsInput input) {
        return toasterService.getHistoryAlarms(input);
    }

}
