/*
 * Copyright (c) 2024 PANTHEON.tech, s.r.o. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.netconf.device.utils;

import com.google.common.base.Preconditions;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class ArgumentParser {

    private static final Logger LOG = LoggerFactory.getLogger(ArgumentParser.class);
    public static final int DEFAULT_PORT = 17830;
    public static final int DEFAULT_DEVICE_COUNT = 1;
    public static final int DEFAULT_POOL_SIZE = 1;

    private boolean initDatastore;
    private boolean saveDatastore;

    public Namespace parseArguments(String[] args) {
        return parseArguments(args, DEFAULT_PORT);
    }

    public Namespace parseArguments(String[] args, int defaultPort) {
        net.sourceforge.argparse4j.inf.ArgumentParser argumentParser =
                ArgumentParsers.newFor("Lighty-netconf-simulator").build();

        argumentParser.addArgument("-p", "--port")
                .nargs(1)
                .setDefault(defaultPort)
                .help("Sets port. If no value is set, default value is used (" + defaultPort + ").")
                .dest("port");
        argumentParser.addArgument("-i", "--init-datastore")
                .nargs(1)
                .help("Set path for the initial datastore folder which will be loaded. Folder must include two files "
                        + "named initial-network-topo-config-datastore.xml and initial-network-topo-operational-datastore.xml");
        argumentParser.addArgument("-o", "--output-datastore")
                .nargs(1)
                .help("Set path where the output datastore which will be saved.");
        argumentParser.addArgument("-d", "--devices-count")
                .nargs(1)
                .setDefault(DEFAULT_DEVICE_COUNT)
                .help("Number of simulated netconf devices to spin."
                        + " This is the number of actual ports which will be used for the devices.")
                .dest("devices-count");
        argumentParser.addArgument("-t", "--thread-pool-size")
                .nargs(1)
                .setDefault(DEFAULT_POOL_SIZE)
                .help("The number of threads to keep in the pool, "
                        + "when creating a device simulator, even if they are idle.")
                .dest("thread-pool-size");

        Namespace namespace = argumentParser.parseArgsOrFail(args);
        if (!(namespace.getString("init_datastore") == null)) {
            initDatastore = true;
            final String pathDoesNotExist = "Input datastore %s does not exist";
            String filename = namespace.getString("init_datastore").replaceAll("[\\[\\]]", "");
            File file = new File(filename);
            Preconditions.checkArgument(file.exists(), String.format(pathDoesNotExist, filename));
        } else initDatastore = false;
        saveDatastore = !(namespace.get("output_datastore") == null);

        return namespace;
    }

    public static int get(Namespace parseArguments, String field) {
        Object obj = parseArguments.get(field);
        int result = -1;
        if (obj instanceof List<?> o1) {
            if (o1.getFirst() instanceof String o2) {
                result = Integer.parseInt(o2);
            } else if (o1.getFirst() instanceof Integer o2){
                result = o2;
            }
        } else if (obj instanceof Integer o1) {
            result = o1;
        } else if (obj instanceof String o1) {
            result = Integer.parseInt(o1);
        }
        return result;
    }

    public boolean isInitDatastore() {
        return initDatastore;
    }

    public boolean isSaveDatastore() {
        return saveDatastore;
    }
}
