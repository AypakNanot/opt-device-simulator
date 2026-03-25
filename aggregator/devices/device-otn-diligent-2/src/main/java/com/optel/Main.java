package com.optel;

import com.optel.rpcs.ToasterServiceImpl;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.lighty.netconf.device.NetconfDevice;
import io.lighty.netconf.device.NetconfDeviceBuilder;
import io.lighty.netconf.device.requests.RequestProcessor;
import io.lighty.netconf.device.utils.ArgumentParser;
import io.lighty.netconf.device.utils.FileUtil;
import io.lighty.netconf.device.utils.YangUtil;
import net.sourceforge.argparse4j.inf.Namespace;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Hello world!
 */
public class Main {
    private static final Logger LOG = LoggerFactory.getLogger(Main.class);
    private static final String JAR_NAME = "otn-diligent-2.jar";
    private static final int DEFAULT_PORT = 17830;
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
        Namespace parseArguments = argumentParser.parseArguments(args, DEFAULT_PORT);

        //parameters are stored as string list
        int port = ArgumentParser.get(parseArguments, "port");
        int devicesCount = ArgumentParser.get(parseArguments, "devices-count");
        int threadCount = ArgumentParser.get(parseArguments, "thread-pool-size");

        //1. Load models from classpath
        Set<YangModuleInfo> models = FileUtil.getModels(this.getClass(), JAR_NAME);

        //2. Initialize RPCs
        ToasterServiceImpl toasterService = new ToasterServiceImpl();
        Map<QName, RequestProcessor> processors = getProcessors(toasterService);

        //3. Initialize Netconf device
        NetconfDevice netconfDevice = new NetconfDeviceBuilder()
                .setCredentials("admin", "admin")
                .setBindingPort(port)
                .withModels(models)
                .withDefaultRequestProcessors()
                .withDefaultNotificationProcessor()
                .withDefaultCapabilities()
                .withRequestProcessors(processors)
                .setThreadPoolSize(threadCount)
                .setDeviceCount(devicesCount)
                .build();
        toasterService.setNotificationPublishService(
                netconfDevice.getNetconfDeviceServices().getNotificationPublishService());
        netconfDevice.start();

        //4. Register shutdown hook
        shutdownHook = new ShutdownHook(netconfDevice, toasterService);
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        }

        //5. Init model
        YangUtil.init(FileUtil.getYangContent(this.getClass(), JAR_NAME));
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

    private static Map<QName, RequestProcessor> getProcessors(ToasterServiceImpl toasterService) {
        Map<QName, RequestProcessor> result = new HashMap<>();
        Reflections reflections = new Reflections("com.optel.processors");
        Set<Class<? extends RequestProcessor>> classes = reflections.getSubTypesOf(RequestProcessor.class);
        for (Class<? extends RequestProcessor> clazz : classes) {
            try {
                RequestProcessor instant = clazz.getDeclaredConstructor(ToasterServiceImpl.class).newInstance(toasterService);
                result.put(instant.getIdentifier(), instant);
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
//                LOG.error("can not get processors: " + clazz.getName());
            }
        }
        return result;
    }

}
