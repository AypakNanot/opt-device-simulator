package com.optel.processors;

import com.google.common.util.concurrent.ListenableFuture;
import com.optel.rpcs.ToasterServiceImpl;
import io.lighty.netconf.device.processors.ToasterServiceAbstractProcessor;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.GetCurrentPerformanceMonitoringData;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.GetCurrentPerformanceMonitoringDataInput;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.GetCurrentPerformanceMonitoringDataOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

public class GetCurrentPerformanceMonitoringDataProcessor extends ToasterServiceAbstractProcessor<GetCurrentPerformanceMonitoringDataInput, GetCurrentPerformanceMonitoringDataOutput> implements GetCurrentPerformanceMonitoringData {
    private final ToasterServiceImpl toasterService;
    private final QName qName = QName.create("urn:cmcc:yang:performance", "get-current-performance-monitoring-data");

    public GetCurrentPerformanceMonitoringDataProcessor(ToasterServiceImpl toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public QName getIdentifier() {
        return qName;
    }

    @Override
    public ListenableFuture<RpcResult<GetCurrentPerformanceMonitoringDataOutput>> invoke(GetCurrentPerformanceMonitoringDataInput input) {
        return toasterService.getCurrentPerformanceMonitoringData(input);
    }
}
