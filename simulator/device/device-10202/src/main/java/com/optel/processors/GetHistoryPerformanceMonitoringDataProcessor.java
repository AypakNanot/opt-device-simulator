package com.optel.processors;

import com.google.common.util.concurrent.ListenableFuture;
import com.optel.rpcs.ToasterServiceImpl;
import io.lighty.netconf.device.processors.ToasterServiceAbstractProcessor;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.GetHistoryPerformanceMonitoringData;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.GetHistoryPerformanceMonitoringDataInput;
import org.opendaylight.yang.gen.v1.urn.cmcc.yang.performance.rev230426.GetHistoryPerformanceMonitoringDataOutput;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;

public class GetHistoryPerformanceMonitoringDataProcessor extends ToasterServiceAbstractProcessor<GetHistoryPerformanceMonitoringDataInput, GetHistoryPerformanceMonitoringDataOutput> implements GetHistoryPerformanceMonitoringData {
    private final ToasterServiceImpl toasterService;
    private final QName qName = QName.create("urn:cmcc:yang:performance", "get-history-performance-monitoring-data");

    public GetHistoryPerformanceMonitoringDataProcessor(ToasterServiceImpl toasterService) {
        this.toasterService = toasterService;
    }

    @Override
    public QName getIdentifier() {
        return qName;
    }

    @Override
    public ListenableFuture<RpcResult<GetHistoryPerformanceMonitoringDataOutput>> invoke(GetHistoryPerformanceMonitoringDataInput input) {
        return toasterService.getHistoryPerformanceMonitoringData(input);
    }
}
