package com.optel;

import com.optel.processors.GetCurrentPerformanceMonitoringDataProcessor;
import com.optel.processors.GetHistoryAlarmsProcessor;
import com.optel.processors.GetHistoryPerformanceMonitoringDataProcessor;
import com.optel.processors.StartNotificationProcessor;
import com.optel.rpcs.ToasterServiceImpl;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.core.common.models.ModuleId;
import io.lighty.netconf.device.NetconfDevice;
import io.lighty.netconf.device.NetconfDeviceBuilder;
import io.lighty.netconf.device.utils.ArgumentParser;
import io.lighty.netconf.device.utils.ModelUtils;
import io.lighty.netconf.device.utils.YangUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * Hello world!
 */
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private ShutdownHook shutdownHook;

    public static void main(String[] args) {
        Main app = new Main();
        app.start(args, true);
    }

    public void start(String[] args) {
        start(args, false);
    }

    @SuppressFBWarnings({"SLF4J_SIGN_ONLY_FORMAT", "OBL_UNSATISFIED_OBLIGATION"})
    public void start(String[] args, boolean registerShutdownHook) {
        ArgumentParser argumentParser = new ArgumentParser();
        Namespace parseArguments = argumentParser.parseArguments(args);

        //parameters are stored as string list
        List<?> portList = parseArguments.get("port");
        int port = Integer.parseInt(String.valueOf(portList.getFirst()));

        LOG.info("10202 device started at port {}", port);

        //1. Load models from classpath
        Set<YangModuleInfo> toasterModules = ModelUtils.getModelsFromClasspath(
                ModuleId.from("urn:ietf:params:xml:ns:yang:iana-if-type", "iana-if-type", "2017-01-19"),
                ModuleId.from("urn:ietf:params:xml:ns:yang:ietf-inet-types", "ietf-inet-types", "2013-07-15"),
                ModuleId.from("urn:ietf:params:xml:ns:yang:ietf-interfaces", "ietf-interfaces", "2018-02-20"),
                ModuleId.from("urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring", "ietf-netconf-monitoring", "2010-10-04"),
                ModuleId.from("urn:ietf:params:xml:ns:yang:ietf-netconf-notifications", "ietf-netconf-notifications", "2012-02-06"),
                ModuleId.from("urn:ietf:params:xml:ns:yang:ietf-yang-types", "ietf-yang-types", "2013-07-15"),
                ModuleId.from("http://openconfig.net/yang/alarms/types", "openconfig-alarm-types", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/channel-monitor", "openconfig-channel-monitor", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/interfaces/aggregate", "openconfig-if-aggregate", "2020-05-01"),
                ModuleId.from("http://openconfig.net/yang/interfaces/ethernet-ext", "openconfig-if-ethernet-ext", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/interfaces/ethernet", "openconfig-if-ethernet", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/interfaces/ip-ext", "openconfig-if-ip-ext", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/interfaces/ip", "openconfig-if-ip", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/interfaces/tunnel", "openconfig-if-tunnel", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/interfaces", "openconfig-interfaces", "2019-11-19"),
                ModuleId.from("http://openconfig.net/yang/lldp/types", "openconfig-lldp-types", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/lldp", "openconfig-lldp", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/messages", "openconfig-messages", "2018-08-13"),
                ModuleId.from("http://openconfig.net/yang/notification", "openconfig-event", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/openconfig-ext", "openconfig-extensions", "2018-10-17"),
                ModuleId.from("http://openconfig.net/yang/openconfig-if-types", "openconfig-if-types", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/openconfig-transport-line-connectivity", "openconfig-transport-line-connectivity", "2019-06-27"),
                ModuleId.from("http://openconfig.net/yang/openconfig-types", "openconfig-types", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/optical-amplifier", "openconfig-optical-amplifier", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/optical-transport-line-protection", "openconfig-transport-line-protection", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform-types", "openconfig-platform-types", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/chassis", "openconfig-platform-chassis", "2020-06-30"),
                ModuleId.from("http://openconfig.net/yang/platform/cpu", "openconfig-platform-cpu", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/platform/extension", "openconfig-platform-ext", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/platform/fan", "openconfig-platform-fan", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/linecard", "openconfig-platform-linecard", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/mcu", "openconfig-platform-mcu", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/me", "openconfig-platform-me", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/mux", "openconfig-platform-mux", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/port", "openconfig-platform-port", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/psu", "openconfig-platform-psu", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform/transceiver", "openconfig-platform-transceiver", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/platform", "openconfig-platform", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/poe", "openconfig-if-poe", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/system/logging", "openconfig-system-logging", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/system/management", "openconfig-system-management", "2020-01-14"),
                ModuleId.from("http://openconfig.net/yang/system/procmon", "openconfig-procmon", "2019-03-15"),
                ModuleId.from("http://openconfig.net/yang/system/terminal", "openconfig-system-terminal", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/system", "openconfig-system", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/telemetry-types", "openconfig-telemetry-types", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/telemetry", "openconfig-telemetry", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/terminal-device", "openconfig-terminal-device", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/transport-line-common", "openconfig-transport-line-common", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/transport-types", "openconfig-transport-types", "2023-04-26"),
                ModuleId.from("http://openconfig.net/yang/types/inet", "openconfig-inet-types", "2019-04-25"),
                ModuleId.from("http://openconfig.net/yang/types/yang", "openconfig-yang-types", "2018-11-21"),
                ModuleId.from("http://openconfig.net/yang/vlan-types", "openconfig-vlan-types", "2019-01-31"),
                ModuleId.from("http://openconfig.net/yang/vlan", "openconfig-vlan", "2019-04-16"),
                ModuleId.from("urn:cesnet:params:xml:ns:libnetconf:notifications", "libnetconf-notifications", "2016-07-21"),
                ModuleId.from("urn:cmcc:yang:alarms:mask:types", "miniotn-alarm-mask-types", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:alarms:mask", "miniotn-alarms-mask", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:alarms", "miniotn-alarms", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:otdr", "miniotn-otdr", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:performance:types", "miniotn-performance-types", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:performance", "miniotn-performance", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:rpc", "miniotn-rpc", "2023-04-26"),
                ModuleId.from("urn:cmcc:yang:tcas", "miniotn-tcas", "2023-04-26"),
                ModuleId.from("urn:ietf:params:xml:ns:netconf:notification:1.0", "notifications", "2008-07-14"),
                ModuleId.from("urn:ietf:params:xml:ns:netmod:notification", "nc-notifications", "2008-07-14")
                
        );

        //2. Initialize RPCs
        ToasterServiceImpl toasterService = new ToasterServiceImpl();

        //3. Initialize Netconf device
        NetconfDeviceBuilder netconfDeviceBuilder = new NetconfDeviceBuilder()
                .setCredentials("admin", "admin")
                .setBindingPort(port)
                .withModels(toasterModules)
                .withDefaultRequestProcessors()
                .withDefaultNotificationProcessor()
                .withDefaultCapabilities()
                .withRequestProcessor(new GetCurrentPerformanceMonitoringDataProcessor(toasterService))
                .withRequestProcessor(new GetHistoryPerformanceMonitoringDataProcessor(toasterService))
                .withRequestProcessor(new GetHistoryAlarmsProcessor(toasterService))
                .withRequestProcessor(new StartNotificationProcessor(toasterService));

        // Initialize DataStores
        File operationalFile = null;
        File configFile = null;
        String configDir = System.getProperty("config.dir", "./examples/devices/lighty-toaster-device/src/main/resources");
        if (argumentParser.isInitDatastore()) {
            LOG.info("Using initial datastore from: {}", configDir);
            operationalFile = new File(configDir, "initial-toaster-operational-datastore.xml");
            configFile = new File(configDir, "initial-toaster-config-datastore.xml");
        }
        if (argumentParser.isSaveDatastore()) {
            operationalFile = new File(configDir, "initial-toaster-operational-datastore.xml");
            configFile = new File(configDir, "initial-toaster-config-datastore.xml");
        }

        NetconfDevice netconfDevice = netconfDeviceBuilder
                .setOperationalDatastore(operationalFile)
                .setConfigDatastore(configFile)
                .build();
        toasterService.setNotificationPublishService(
                netconfDevice.getNetconfDeviceServices().getNotificationPublishService());

        netconfDevice.start();
        //5. Register shutdown hook
        shutdownHook = new ShutdownHook(netconfDevice, toasterService);
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }
        //6. Init model
        YangUtil.handle(null, null);
    }

    public void shutdown() {
        if (shutdownHook != null) {
            shutdownHook.execute();
        }
    }

    private static class ShutdownHook extends Thread {

        private final NetconfDevice netConfDevice;
        private final ToasterServiceImpl toasterService;

        ShutdownHook(NetconfDevice netConfDevice, ToasterServiceImpl toasterService) {
            this.netConfDevice = netConfDevice;
            this.toasterService = toasterService;
        }

        @Override
        public void run() {
            execute();
        }

        public void execute() {
            Main.LOG.info("Shutting down Lighty-Toaster device.");
            if (toasterService != null) {
                toasterService.close();
            }
            if (netConfDevice != null) {
                try {
                    netConfDevice.close();
                } catch (Exception e) {
                    Main.LOG.error("Failed to close Netconf device properly", e);
                }
            }
        }
    }
}
