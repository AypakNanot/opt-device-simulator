/*
 * Copyright (c) 2020 PANTHEON.tech s.r.o. All Rights Reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at https://www.eclipse.org/legal/epl-v10.html
 */
package io.lighty.netconf.device.requests;

import io.lighty.netconf.device.NetconfDeviceServices;
import io.lighty.netconf.device.utils.FileUtil;
import io.lighty.netconf.device.utils.RPCUtil;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.lighty.netconf.device.utils.YangUtil;
import org.opendaylight.netconf.api.xml.XmlElement;
import org.opendaylight.netconf.test.tool.rpchandler.RpcHandler;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class RpcHandlerImpl implements RpcHandler {

    private static final Logger LOG = LoggerFactory.getLogger(RpcHandlerImpl.class);

    private final Map<QName, RequestProcessor> cache;

    public RpcHandlerImpl(final NetconfDeviceServices netconfDeviceServices, final Map<QName, RequestProcessor> cache) {
        Map<QName, RequestProcessor> transCache = new HashMap<>();
        cache.forEach((key, value) -> transCache.put(key.withoutRevision(), value));
        this.cache = transCache;
        this.cache.values().forEach(rp -> rp.init(netconfDeviceServices));
    }

    @Override
    public Optional<Document> getResponse(XmlElement rpcElement) {
        Element element = rpcElement.getDomElement();
        String formattedRequest = RPCUtil.formatXml(element);
        LOG.debug("Received get request with payload:\n{} ", formattedRequest);
        trimFilterLabel(element);
        String moduleName = YangUtil.getModuleName(formattedRequest);
        if (moduleName != null) {
            if ("get".equals(rpcElement.getName())) {
                if (!formattedRequest.contains("netconf-state")) {
                    try (InputStream is = FileUtil.getFile(moduleName)) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document document = builder.parse(is);
                        return Optional.of(document);
                    } catch (Exception e) {
                        return Optional.empty();
                    }
                }
            } else if ("edit".equals(rpcElement.getName())) {
                YangUtil.handle(formattedRequest, moduleName);
                return Optional.empty();
            }
        }
        Optional<RequestProcessor> processorForRequestOpt = getProcessorForRequest(element);
        return processorForRequestOpt.map(requestProcessor -> requestProcessor.processRequest(element));
    }

    private void trimFilterLabel(Element element) {
        if (element == null) {
            return;
        }
        NodeList filterNodes = element.getElementsByTagName("filter");
        for (int i = 0; i < filterNodes.getLength(); i++) {
            Element filterElement = (Element) filterNodes.item(i);
            NodeList children = filterElement.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                Node child = children.item(j);
                element.appendChild(child.cloneNode(true));
            }
            element.removeChild(filterNodes.item(i));
        }
    }

    private Optional<RequestProcessor> getProcessorForRequest(final Element element) {
        final String namespace = element.getNamespaceURI();
        final String localName = element.getLocalName();
        final RequestProcessor foundProcessor = this.cache.get(QName.create(namespace, localName));
        return Optional.ofNullable(foundProcessor);
    }

}
