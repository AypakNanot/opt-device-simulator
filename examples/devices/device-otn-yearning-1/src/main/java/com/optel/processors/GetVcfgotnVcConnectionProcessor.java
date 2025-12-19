package com.optel.processors;

import com.google.common.util.concurrent.ListenableFuture;
import com.optel.rpcs.ToasterServiceImpl;
import io.lighty.netconf.device.processors.ToasterServiceAbstractProcessor;
import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.GetVcfgotnVcConnection;

import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.GetVcfgotnVcConnectionInput;
import org.opendaylight.yang.gen.v1.com.optel.yang.otn.extension.rev241204.GetVcfgotnVcConnectionOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

/**
 * @author dzm
 * @since 2025/8/18
 */
public class GetVcfgotnVcConnectionProcessor extends ToasterServiceAbstractProcessor<GetVcfgotnVcConnectionInput, GetVcfgotnVcConnectionOutput> implements GetVcfgotnVcConnection {

    private final ToasterServiceImpl toasterService;

    public GetVcfgotnVcConnectionProcessor(ToasterServiceImpl toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public QName getIdentifier() {
        QName qname = GetVcfgotnVcConnection.QNAME;
        return QName.create(qname.getNamespace(), qname.getLocalName());
    }


    @Override
    public ListenableFuture<RpcResult<GetVcfgotnVcConnectionOutput>> invoke(GetVcfgotnVcConnectionInput input) {
        return toasterService.getVcfgotnVcConnection(input);
    }
}
