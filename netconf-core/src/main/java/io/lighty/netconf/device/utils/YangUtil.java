package io.lighty.netconf.device.utils;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.text.CharSequenceUtil;
import com.google.common.collect.Sets;
import io.lighty.netconf.device.dto.LabelKey;
import io.lighty.netconf.device.dto.XmlDataNode;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.*;
import org.opendaylight.yangtools.yang.model.api.source.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.ri.stmt.impl.eff.*;
import org.opendaylight.yangtools.yang.model.spi.source.StringYangTextSource;
import org.opendaylight.yangtools.yang.parser.api.YangParser;
import org.opendaylight.yangtools.yang.parser.impl.DefaultYangParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class YangUtil {
    public static final String DIR = System.getProperty("user.dir");
    private static final Logger LOG = LoggerFactory.getLogger(YangUtil.class);
    private static final Map<LabelKey, String> map = new HashMap<>();
    private static final Map<String, String> moduleMap = new HashMap<>();
    private static final Map<String, String> namespaceMap = new HashMap<>();
    private static final Map<String, String> importMap = new HashMap<>();
    private static final Map<String, String> leafMap = new HashMap<>();
    private static final Map<String, Map<String, Set<String>>> keyMap = new HashMap<>();
    private static final Pattern pattern = Pattern.compile("name=([a-zA-Z0-9_-]+), value=0");
    private static final Pattern numPattern = Pattern.compile("(u?int(8|16|32|64))\\s*");

    public static void handle(String xml, String moduleName) {
        try {
            YangParser parser = new DefaultYangParserFactory().createParser();
            try (InputStream is = YangUtil.class.getClassLoader().getResourceAsStream("yang/index.txt");
                 BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                String filename;
                while ((filename = br.readLine()) != null) {
                    if (CharSequenceUtil.isBlank(filename) || !filename.endsWith(".yang")) {
                        continue;
                    }
                    try (InputStream yangIs = YangUtil.class.getClassLoader().getResourceAsStream("yang/" + filename)) {
                        String content = IoUtil.readUtf8(yangIs);
                        importMap.putAll(parseYangImports(content));
                        leafMap.putAll(parseLeafTypes(content));
                        parser.addSource(new StringYangTextSource(new SourceIdentifier(filename.substring(0, filename.indexOf(filename.contains("@") ? "@" : "."))), content));
                    }
                }
            }
            EffectiveModelContext context = parser.buildEffectiveModel();
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            docFactory.setNamespaceAware(true);
            Document doc = docFactory.newDocumentBuilder().newDocument();
            Element rootElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "rpc-reply");
            Element dataElement = doc.createElementNS("urn:ietf:params:xml:ns:netconf:base:1.0", "data");
            rootElement.appendChild(dataElement);
            doc.appendChild(rootElement);
            Collection<? extends @NonNull Module> modules = context.getModules();
            modules.forEach(module -> moduleMap.put(module.getName(), module.getNamespace().toString()));
            modules.forEach(module -> namespaceMap.put(module.getNamespace().toString(), module.getName()));
            leafMap.forEach((leaf, type) -> {
                String prefix = importMap.get(type);
                String namespace = moduleMap.get(prefix);
                map.put(new LabelKey(leaf, namespace), type);
            });
            Iterator<? extends Module> iterator = modules.iterator();
            while (iterator.hasNext()) {
                Module module = iterator.next();
                for (@NonNull DataSchemaNode node : module.getChildNodes()) {
                    if (node instanceof ContainerSchemaNode schemaNode) {
                        Element element = doc.createElementNS(node.getQName().getNamespace().toString(), node.getQName().getLocalName());
                        dataElement.appendChild(element);
                        generateData(doc, element, schemaNode, module.getName());
                    }
                }
            }
            if (CharSequenceUtil.isNotBlank(xml) && CharSequenceUtil.isNotBlank(moduleName)) {
                Path originPath = io.lighty.netconf.device.utils.FileUtil.isPackage() ? Path.of(DIR, "xml", moduleName + ".xml") : Path.of(YangUtil.class.getClassLoader().getResource("xml/" + moduleName + ".xml").toURI());
                Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(originPath.toFile());
                if (document != null) {
                    NodeList nodeList = document.getElementsByTagName("data");
                    if (nodeList != null && nodeList.getLength() > 0) {
                        NodeList childNodes = nodeList.item(0).getChildNodes();
                        if (childNodes != null && childNodes.getLength() > 2) {
                            Node topNode = childNodes.item(1);
                            NamedNodeMap attributes = topNode.getAttributes();
                            if (attributes != null) {
                                Node xmlns = attributes.getNamedItem("xmlns");
                                if (xmlns != null) {
                                    Map<String, Set<String>> moduleKeyMap = keyMap.get(xmlns.getNodeValue());
                                    if (xml.contains("op:operation=\"delete\"")) {
                                        delete(moduleKeyMap, nodeList.item(0), xml);
                                    } else {
                                        modify(moduleKeyMap, nodeList.item(0), xml, document);
                                    }
                                }
                            }
                        }
                    }
                    Files.writeString(originPath, xmlToString(document));
                }
            }
        } catch (Exception e) {
            LOG.error("Yang handle error.", e);
        }

    }

    private static void copyConfig(XmlDataNode parentNode) {
        Map<String, XmlDataNode> children = parentNode.getChildren();
        List<XmlDataNode> stateNodes = new ArrayList<>();
        for (XmlDataNode node : children.values()) {
            if ("config".equals(node.getName())) {
                XmlDataNode stateNode = new XmlDataNode("state", null, node.getChildren());
                stateNodes.add(stateNode);
            } else {
                copyConfig(node);
            }
        }
        for (XmlDataNode stateNode : stateNodes) {
            parentNode.addChild(stateNode);
        }
    }

    private static void delete(Map<String, Set<String>> moduleKeyMap, Node targetNode, String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new StringReader(xml));
        Document doc = builder.parse(inputSource);
        NodeList nodeList = doc.getElementsByTagName("config");
        Node node = nodeList.item(0);
        XmlDataNode root = new XmlDataNode("config");
        setValue(node, root);
        copyConfig(root);
        deleteDocument(root.getChildren().values().iterator().next(), targetNode, moduleKeyMap);
    }

    private static void deleteDocument(XmlDataNode root, Node parentNode, Map<String, Set<String>> moduleKeyMap) {
        String name = root.getName();
        String value = root.getValue();
        if (value == null) {
            NodeList upperNodeList = parentNode.getChildNodes();
            if (upperNodeList != null && upperNodeList.getLength() > 1) {
                List<Node> nodeList = new ArrayList<>();
                for (int i = 1; i < upperNodeList.getLength(); i += 2) {
                    if (Objects.equals(upperNodeList.item(i).getNodeName(), name)) {
                        nodeList.add(upperNodeList.item(i));
                    }
                }
                if (nodeList.size() > 1) {
                    Set<String> key = moduleKeyMap.get(name);
                    if (CollUtil.isNotEmpty(key)) {
                        Node target = null;
                        for (int j = 0; j < nodeList.size(); j++) {
                            boolean flag = true;
                            Node node = nodeList.get(j);
                            NodeList childNodes = node.getChildNodes();
                            for (int x = 1; x < childNodes.getLength(); x += 2) {
                                String nodeName = childNodes.item(x).getNodeName();
                                for (Map.Entry<String, XmlDataNode> entry : root.getChildren().entrySet()) {
                                    if (Objects.equals(nodeName, entry.getKey())) {
                                        if (key.contains(entry.getKey()) && !Objects.equals(childNodes.item(x).getTextContent(), entry.getValue().getValue())) {
                                            flag = false;
                                        }
                                        break;
                                    }
                                }
                            }
                            if (!flag) {
                                continue;
                            }
                            target = node;
                            break;
                        }
                        List<XmlDataNode> setNode = root.getChildren().values().stream().filter(item -> CharSequenceUtil.isNotBlank(item.getValue()) && !key.contains(item.getName())).collect(Collectors.toList());
                        List<XmlDataNode> innerNode = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isBlank(ele.getValue())).collect(Collectors.toList());
                        if (target != null) {
                            if (CollUtil.isEmpty(innerNode)) {
                                for (int i = 0; i < parentNode.getChildNodes().getLength(); i++) {
                                    Node node = parentNode.getChildNodes().item(i);
                                    if (node == target) {
                                        parentNode.removeChild(target);
                                        break;
                                    }
                                }
                            } else {
                                NodeList childNodes = target.getChildNodes();
                                for (int j = 1; j < childNodes.getLength(); j += 2) {
                                    Node node = childNodes.item(j);
                                    String nodeName = node.getNodeName();
                                    for (XmlDataNode field : innerNode) {
                                        if (Objects.equals(nodeName, field.getName())) {
                                            deleteDocument(root.getChildren().get(field.getName()), target, moduleKeyMap);
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (nodeList.size() == 1) {
                    NodeList childNodes = nodeList.get(0).getChildNodes();
                    if (childNodes != null && childNodes.getLength() > 1) {
                        List<XmlDataNode> setNode = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isNotBlank(ele.getValue())).collect(Collectors.toList());
                        List<XmlDataNode> innerNode = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isBlank(ele.getValue())).collect(Collectors.toList());
                        if (CollUtil.isEmpty(innerNode)) {
                            Set<String> key = moduleKeyMap.get(name);
                            if (CollUtil.isNotEmpty(key)) {
                                boolean flag = true;
                                for (int x = 1; x < childNodes.getLength(); x += 2) {
                                    String nodeName = childNodes.item(x).getNodeName();
                                    for (Map.Entry<String, XmlDataNode> entry : root.getChildren().entrySet()) {
                                        if (Objects.equals(nodeName, entry.getKey())) {
                                            if (key.contains(entry.getKey()) && !Objects.equals(childNodes.item(x).getTextContent(), entry.getValue().getValue())) {
                                                flag = false;
                                            }
                                            break;
                                        }
                                    }
                                }
                                if (flag) {
                                    for (int i = 0; i < parentNode.getChildNodes().getLength(); i++) {
                                        Node node = parentNode.getChildNodes().item(i);
                                        if (node == childNodes) {
                                            parentNode.removeChild(nodeList.get(0));
                                            break;
                                        }
                                    }
                                }
                            }
                        } else {
                            for (int j = 1; j < childNodes.getLength(); j += 2) {
                                Node node = childNodes.item(j);
                                String nodeName = node.getNodeName();
                                for (XmlDataNode field : innerNode) {
                                    if (Objects.equals(nodeName, field.getName())) {
                                        deleteDocument(root.getChildren().get(field.getName()), nodeList.get(0), moduleKeyMap);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


    }

    private static void modify(Map<String, Set<String>> moduleKeyMap, Node targetNode, String xml, Document document) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource inputSource = new InputSource(new StringReader(xml));
        Document doc = builder.parse(inputSource);
        NodeList nodeList = doc.getElementsByTagName("config");
        Node node = nodeList.item(0);
        XmlDataNode root = new XmlDataNode("config");
        setValue(node, root);
        copyConfig(root);
        modifyDocument(root.getChildren().values().iterator().next(), targetNode, moduleKeyMap, document);
    }

    private static void modifyDocument(XmlDataNode root, Node parentNode, Map<String, Set<String>> moduleKeyMap, Document document) {
        String name = root.getName();
        String value = root.getValue();
        String namespace = root.getNamespace();
        if (value == null) {
            NodeList upperNodeList = parentNode.getChildNodes();
            if (upperNodeList != null && upperNodeList.getLength() > 1) {
                List<Node> nodeList = new ArrayList<>();
                for (int i = 1; i < upperNodeList.getLength(); i += 2) {
                    if (Objects.equals(upperNodeList.item(i).getNodeName(), name)) {
                        nodeList.add(upperNodeList.item(i));
                    }
                }
                if (nodeList.size() > 1) {
                    Set<String> key = moduleKeyMap.get(name);
                    if (CollUtil.isNotEmpty(key)) {
                        Node target = null;
                        for (int j = 0; j < nodeList.size(); j++) {
                            boolean flag = true;
                            Node node = nodeList.get(j);
                            NodeList childNodes = node.getChildNodes();
                            for (int x = 1; x < childNodes.getLength(); x += 2) {
                                String nodeName = childNodes.item(x).getNodeName();
                                for (Map.Entry<String, XmlDataNode> entry : root.getChildren().entrySet()) {
                                    if (Objects.equals(nodeName, entry.getKey())) {
                                        if (key.contains(entry.getKey()) && !Objects.equals(childNodes.item(x).getTextContent(), entry.getValue().getValue())) {
                                            flag = false;
                                        }
                                        break;
                                    }
                                }
                            }
                            if (!flag) {
                                continue;
                            }
                            target = node;
                            break;
                        }
                        List<XmlDataNode> setNode = root.getChildren().values().stream().filter(item -> CharSequenceUtil.isNotBlank(item.getValue()) && !key.contains(item.getName())).collect(Collectors.toList());
                        List<XmlDataNode> innerNode = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isBlank(ele.getValue())).collect(Collectors.toList());
                        List<XmlDataNode> innerNodeCopy = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isBlank(ele.getValue())).collect(Collectors.toList());
                        if (target != null) {
                            NodeList childNodes = target.getChildNodes();
                            for (int j = 1; j < childNodes.getLength(); j += 2) {
                                Node node = childNodes.item(j);
                                String nodeName = node.getNodeName();
                                for (XmlDataNode field : setNode) {
                                    if (Objects.equals(nodeName, field.getName())) {
                                        childNodes.item(j).setTextContent(field.getValue());
                                        break;
                                    }
                                }
                                for (XmlDataNode field : innerNode) {
                                    if (Objects.equals(nodeName, field.getName())) {
                                        Optional<XmlDataNode> optional = innerNodeCopy.stream().filter(item -> Objects.equals(item.getName(), nodeName)).findFirst();
                                        if (optional.isPresent()) {
                                            innerNodeCopy.remove(optional.get());
                                        }
                                        modifyDocument(root.getChildren().get(field.getName()), target, moduleKeyMap, document);
                                        break;
                                    }
                                }
                            }
                            for (XmlDataNode xmlDataNode : innerNodeCopy) {
                                addNode(xmlDataNode, target, document);
                            }
                        } else {
                            addNode(root, parentNode, document);
                        }
                    }
                } else if (nodeList.size() == 1) {
                    Set<String> key = moduleKeyMap.get(name);
                    Node uniqueNode = nodeList.get(0);
                    String keyNodeName = uniqueNode.getNodeName();
                    NodeList childNodes = uniqueNode.getChildNodes();
                    Set<String> fields = new HashSet<>();
                    for (int i = 1; i < childNodes.getLength(); i += 2) {
                        fields.add(childNodes.item(i).getNodeName());
                    }
                    if (CollUtil.isEmpty(fields)) {
                        root.getChildren().values().forEach(item -> addNode(item, uniqueNode, document));
                        return;
                    } else if (CollUtil.isNotEmpty(key) && Sets.intersection(fields, key).size() > 0) {
                        Sets.SetView<String> common = Sets.intersection(fields, key);
                        boolean flag = true;
                        Iterator<String> iterator = common.stream().iterator();
                        while (iterator.hasNext()) {
                            String next = iterator.next();
                            for (int x = 1; x < childNodes.getLength(); x += 2) {
                                String nodeName = childNodes.item(x).getNodeName();
                                if (Objects.equals(nodeName, next)) {
                                    if (!Objects.equals(childNodes.item(x).getTextContent(), root.getChildren().get(next).getValue())) {
                                        flag = false;
                                    }
                                    break;
                                }
                            }
                        }
                        if (!flag) {
                            addNode(root, parentNode, document);
                            return;
                        }
                    }
                    if (childNodes != null && childNodes.getLength() > 1) {
                        List<XmlDataNode> setNode = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isNotBlank(ele.getValue())).collect(Collectors.toList());
                        List<XmlDataNode> innerNode = root.getChildren().values().stream().filter(ele -> CharSequenceUtil.isBlank(ele.getValue())).collect(Collectors.toList());
                        for (int j = 1; j < childNodes.getLength(); j += 2) {
                            Node node = childNodes.item(j);
                            String nodeName = node.getNodeName();
                            for (XmlDataNode field : setNode) {
                                if (Objects.equals(nodeName, field.getName())) {
                                    childNodes.item(j).setTextContent(field.getValue());
                                    break;
                                }
                            }
                            for (XmlDataNode field : innerNode) {
                                if (Objects.equals(nodeName, field.getName())) {
                                    modifyDocument(root.getChildren().get(field.getName()), uniqueNode, moduleKeyMap, document);
                                    innerNode.remove(field);
                                    break;
                                }
                            }
                        }
                        innerNode.forEach(item -> addNode(item, uniqueNode, document));
                    } else {
                        root.getChildren().values().forEach(item -> addNode(item, uniqueNode, document));
                    }


                } else {
                    addNode(root, parentNode, document);
                }
            } else {
                addNode(root, parentNode, document);
            }
        }

    }

    private static void addNode(XmlDataNode root, Node parentNode, Document document) {
        String name = root.getName();
        String value = root.getValue();
        String namespace = root.getNamespace();
        Map<String, XmlDataNode> children = root.getChildren();
        String alias = map.get(new LabelKey(name, namespace));
        Element element;
        if (CharSequenceUtil.isEmpty(namespace)) {
            element = document.createElement(name);
        } else if (CharSequenceUtil.isNotBlank(alias)) {
            element = document.createElement(name);
            element.setAttributeNS("http://www.w3.org/2000/xmlns/", String.format("xmlns:%s", alias), namespace);
        } else {
            element = document.createElementNS(namespace, name);
        }
        element.setTextContent(value);
        parentNode.appendChild(element);
        if (CollUtil.isEmpty(children)) {
            return;
        }
        children.values().forEach(item -> addNode(item, element, document));
    }


    private static void setValue(Node parentNode, XmlDataNode xmlDataNode) {
        NodeList childNodes = parentNode.getChildNodes();
        if (childNodes.getLength() > 2) {
            for (int i = 1; i < childNodes.getLength() - 1; i += 2) {
                Node node = childNodes.item(i);
                String namespace = node.getNamespaceURI();
                if (CharSequenceUtil.isEmpty(namespace)) {
                    namespace = Optional.ofNullable(node.getAttributes()).map(item -> item.item(0)).map(Node::getNodeValue).orElse(null);
                }
                if (node.getChildNodes().getLength() == 1) {
                    String nodeName = node.getNodeName();
                    String nodeValue = node.getTextContent();
                    XmlDataNode addNode = new XmlDataNode(nodeName, nodeValue, namespace);
                    xmlDataNode.addChild(addNode);
                } else if (node.getChildNodes().getLength() > 1) {
                    XmlDataNode addNode = new XmlDataNode(node.getNodeName(), null, namespace);
                    xmlDataNode.addChild(addNode);
                    setValue(node, addNode);
                }
            }
        }
    }


    private static void generateData(Document doc, Element parentElement, DataNodeContainer container, String
            moduleName) {
        for (@NonNull DataSchemaNode node : container.getChildNodes()) {
            String namespace = node.getQName().getNamespace().toString();
            String localName = node.getQName().getLocalName();
            if (node instanceof LeafSchemaNode leaf) {
                Element element = doc.createElementNS(namespace, localName);
                element.setTextContent(generateMockValue(leaf.getType()));
                String typeNamespace = null;
                TypeDefinition<?> type;
                if (node instanceof EmptyLeafEffectiveStatement statement) {
                    type = statement.getType().getBaseType();
                    if (type != null) {
                        typeNamespace = type.getQName().getNamespace().toString();
                    } else {
                        String typeContent = statement.getType().toString();
                        int index = typeContent.indexOf("argument=(");
                        if (index != -1) {
                            String cut = typeContent.substring(index + 10);
                            index = cut.indexOf("?");
                            if (index != -1) {
                                typeNamespace = cut.substring(0, index);
                            }
                        }
                    }
                } else if (node instanceof RegularLeafEffectiveStatement statement) {
                    type = statement.getType().getBaseType();
                    if (type != null) {
                        typeNamespace = type.getQName().getNamespace().toString();
                    }
                }
                if ("".equals(element.getTextContent())) {
                    String target = map.getOrDefault(new LabelKey(node.getQName().getLocalName(), typeNamespace), "");
                    String result = "";
                    Matcher matcher = pattern.matcher(leaf.getType().toString());
                    if (matcher.find()) {
                        result = matcher.group(1);
                    } else if (leaf.getType().getBaseType() != null) {
                        matcher = pattern.matcher(leaf.getType().getBaseType().toString());
                        if (matcher.find()) {
                            result = matcher.group(1);
                        }
                    }
                    if (target.length() > 0) {
                        element.setAttributeNS("http://www.w3.org/2000/xmlns/", String.format("xmlns:%s", target), typeNamespace);
                    }
                    element.setTextContent(result);
                }
                parentElement.appendChild(element);
            } else if (node instanceof ListSchemaNode list) {
                if (list instanceof EmptyListEffectiveStatement listEffectiveStatement) {
                    if (listEffectiveStatement.getDeclared() != null && listEffectiveStatement.getDeclared().declaredSubstatements() != null &&
                            listEffectiveStatement.getDeclared().declaredSubstatements().size() > 0) {
                        String keyString = listEffectiveStatement.getDeclared().declaredSubstatements().get(0).rawArgument();
                        if (keyString != null) {
                            String moduleNamespace = moduleMap.get(moduleName);
                            Map<String, Set<String>> value = keyMap.getOrDefault(moduleMap.get(moduleName), new HashMap<>());
                            Set<String> keys = value.getOrDefault(localName, new HashSet<>());
                            keys.addAll(Stream.of(keyString.split(" ")).map(String::trim).collect(Collectors.toSet()));
                            value.put(localName, keys);
                            keyMap.put(moduleNamespace, value);
                        }
                    }
                } else if (list instanceof RegularListEffectiveStatement listEffectiveStatement) {
                    if (listEffectiveStatement.getDeclared() != null && listEffectiveStatement.getDeclared().declaredSubstatements() != null &&
                            listEffectiveStatement.getDeclared().declaredSubstatements().size() > 0) {
                        String keyString = listEffectiveStatement.getDeclared().declaredSubstatements().get(0).rawArgument();
                        if (keyString != null) {
                            String moduleNamespace = moduleMap.get(moduleName);
                            Map<String, Set<String>> value = keyMap.getOrDefault(moduleMap.get(moduleName), new HashMap<>());
                            Set<String> keys = value.getOrDefault(localName, new HashSet<>());
                            keys.addAll(Stream.of(keyString.split(" ")).map(String::trim).collect(Collectors.toSet()));
                            value.put(localName, keys);
                            keyMap.put(moduleNamespace, value);
                        }
                    }
                }
                Element element = doc.createElementNS(namespace, localName);
                parentElement.appendChild(element);
                generateData(doc, element, list, moduleName);
            } else if (node instanceof ContainerSchemaNode containerSchemaNode) {
                Element element = doc.createElementNS(namespace, localName);
                parentElement.appendChild(element);
                generateData(doc, element, containerSchemaNode, moduleName);
            } else if (node instanceof SlimLeafListEffectiveStatement leafList) {
                Element element = doc.createElementNS(namespace, localName);
                element.setTextContent(generateMockValue(leafList.getType()));
                String typeNamespace = null;
                TypeDefinition<?> type;
                if (node instanceof EmptyLeafEffectiveStatement statement) {
                    type = statement.getType().getBaseType();
                    if (type != null) {
                        typeNamespace = type.getQName().getNamespace().toString();
                    } else {
                        String typeContent = statement.getType().toString();
                        int index = typeContent.indexOf("argument=(");
                        if (index != -1) {
                            String cut = typeContent.substring(index + 10);
                            index = cut.indexOf("?");
                            if (index != -1) {
                                typeNamespace = cut.substring(0, index);
                            }
                        }
                    }
                } else if (node instanceof RegularLeafEffectiveStatement statement) {
                    type = statement.getType().getBaseType();
                    if (type != null) {
                        typeNamespace = type.getQName().getNamespace().toString();
                    }
                }
                if ("".equals(element.getTextContent())) {
                    String target = map.getOrDefault(new LabelKey(node.getQName().getLocalName(), typeNamespace), "");
                    String result = "";
                    Matcher matcher = pattern.matcher(leafList.getType().toString());
                    if (matcher.find()) {
                        result = matcher.group(1);
                    } else if (leafList.getType().getBaseType() != null) {
                        matcher = pattern.matcher(leafList.getType().getBaseType().toString());
                        if (matcher.find()) {
                            result = matcher.group(1);
                        }
                    }
                    if (target.length() > 0) {
                        element.setTextContent(String.format("%s:%s", target, result));
                        element.setAttributeNS("http://www.w3.org/2000/xmlns/", String.format("xmlns:%s", target), typeNamespace);
                    } else {
                        element.setTextContent(result);
                    }
                }
                parentElement.appendChild(element);
            }
        }
    }

    private static String generateMockValue(TypeDefinition<?> type) {
        String localName = type.getQName().getLocalName();
        String fieldType = "";
        if (type.getBaseType() != null) {
            fieldType = type.getBaseType().getQName().getLocalName();
        }
        Optional<?> defaultValue = type.getDefaultValue();
        if (defaultValue.isPresent()) {
            return defaultValue.get().toString();
        } else if ("string".equals(localName)) {
            return "xxx";
        } else if (numPattern.matcher(fieldType).matches() || numPattern.matcher(localName).matches()) {
            return "0";
        } else if (fieldType.contains("decimal") || fieldType.contains("float") || fieldType.contains("double") || localName.contains("decimal") || localName.contains("float") || localName.contains("double")) {
            return "0.0";
        } else if ("boolean".equals(fieldType) || "boolean".equals(localName)) {
            return "true";
        } else {
            return "";
        }
    }

    public static String xmlToString(Document doc) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        String result = "";
        try (StringWriter writer = new StringWriter()) {
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            result = writer.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String regex = "<([a-zA-Z0-9-]+)([^>]*)\\s*/>";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(result);
        result = matcher.replaceAll("<$1$2></$1>").replaceAll("(?m)^[ \t]*\r?\n", "");
        return result;
    }

    private static Map<String, String> parseYangImports(String input) {
        Map<String, String> importMap = new LinkedHashMap<>();
        Pattern pattern = Pattern.compile(
                "import\\s+(\\S+)\\s*\\{\\s*prefix\\s+(.*?)}", Pattern.DOTALL
        );
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            String prefix = matcher.group(2).replace(";", "").replace("\r", "").replace("\n", "").replace("\"", "").trim();
            int index = prefix.indexOf(" ");
            if (index != -1) {
                prefix = prefix.substring(0, index);
            }
            String module = matcher.group(1);
            importMap.put(prefix, module);
        }
        return importMap;
    }

    public static Map<String, String> parseLeafTypes(String yangText) {
        Map<String, String> leafMap = new LinkedHashMap<>();
        String currentLeaf = null;
        String[] lines = yangText.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("leaf ")) {
                currentLeaf = line.split("\\s+")[1];
            } else if (currentLeaf != null && (line.startsWith("type ") || line.startsWith("base "))) {
                String[] typeParts = line.split("\\s+")[1].split(":");
                if (typeParts.length == 2) {
                    String typePrefix = typeParts[0];
                    leafMap.put(currentLeaf, typePrefix);
                    currentLeaf = null;
                }
            }
        }
        return leafMap;
    }

    public static String getModuleName(String xml) {
        String result = null;
        try (StringReader stringReader = new StringReader(xml)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource inputSource = new InputSource(stringReader);
            Document document = builder.parse(inputSource);
            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();
            String xpathExpression = "//*[namespace-uri() != 'urn:ietf:params:xml:ns:netconf:base:1.0' and namespace-uri() !=  'urn:ietf:params:xml:ns:yang:ietf-netconf-monitoring' and namespace-uri() != '']";
            XPathExpression expr = xpath.compile(xpathExpression);
            NodeList nodeList = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
            if (nodeList.getLength() > 0) {
                result = namespaceMap.get(nodeList.item(0).getNamespaceURI());
            }
        } catch (Exception e) {
            LOG.error("Parse request xml error.", e);
        }
        return result;
    }

}
