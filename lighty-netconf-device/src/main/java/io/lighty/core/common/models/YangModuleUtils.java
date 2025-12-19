//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package io.lighty.core.common.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import java.util.stream.Collectors;
import org.opendaylight.yangtools.binding.meta.YangModelBindingProvider;
import org.opendaylight.yangtools.binding.meta.YangModuleInfo;
import org.opendaylight.yangtools.yang.common.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class YangModuleUtils {
    private static final Logger LOG = LoggerFactory.getLogger(YangModuleUtils.class);
    private static final String ADDING_MODULE_INTO_KNOWN_MODULES = "Adding [{}] module into known modules";

    private YangModuleUtils() {
        throw new UnsupportedOperationException("do not instantiate utility class");
    }

    public static Set<YangModuleInfo> getAllModelsFromClasspath() {
        Set<YangModuleInfo> moduleInfos = new HashSet();
        ServiceLoader<YangModelBindingProvider> yangProviderLoader = ServiceLoader.load(YangModelBindingProvider.class);
        Iterator var2 = yangProviderLoader.iterator();

        while(var2.hasNext()) {
            YangModelBindingProvider yangModelBindingProvider = (YangModelBindingProvider)var2.next();
            moduleInfos.add(yangModelBindingProvider.getModuleInfo());
            LOG.info("Adding [{}] module into known modules", yangModelBindingProvider.getModuleInfo());
        }

        return Collections.unmodifiableSet(moduleInfos);
    }

    public static Set<YangModuleInfo> filterTopLevelModels(Set<YangModuleInfo> models) {
        Set<YangModuleInfo> result = new HashSet();
        Iterator var2 = models.iterator();

        while(var2.hasNext()) {
            YangModuleInfo yangModuleInfo = (YangModuleInfo)var2.next();
            if (!isDependentModel(models, yangModuleInfo)) {
                result.add(yangModuleInfo);
            }
        }

        return result;
    }

    public static Set<YangModuleInfo> filterUniqueModels(Collection<YangModuleInfo> models) {
        Map<ModuleId, YangModuleInfo> result = new HashMap();
        Iterator var2 = models.iterator();

        while(var2.hasNext()) {
            YangModuleInfo yangModuleInfo = (YangModuleInfo)var2.next();
            result.put(ModuleId.from(yangModuleInfo), yangModuleInfo);
            Iterator var4 = filterUniqueModels(yangModuleInfo.getImportedModules()).iterator();

            while(var4.hasNext()) {
                YangModuleInfo yangModuleInfoDep = (YangModuleInfo)var4.next();
                result.put(ModuleId.from(yangModuleInfoDep), yangModuleInfoDep);
            }
        }

        return new HashSet(result.values());
    }

    public static Set<YangModuleInfo> getModelsFromClasspath(Set<ModuleId> filter) {
        Map<ModuleId, YangModuleInfo> resolvedModules = new HashMap();
        ServiceLoader<YangModelBindingProvider> yangProviderLoader = ServiceLoader.load(YangModelBindingProvider.class);
        Iterator var3 = filter.iterator();

        while(var3.hasNext()) {
            ModuleId moduleId = (ModuleId)var3.next();
            Set<YangModuleInfo> filteredSet = filterYangModelBindingProviders(moduleId, yangProviderLoader);
            Iterator var6 = filteredSet.iterator();

            while(var6.hasNext()) {
                YangModuleInfo yangModuleInfo = (YangModuleInfo)var6.next();
                resolvedModules.put(ModuleId.from(yangModuleInfo), yangModuleInfo);
                LOG.info("Adding [{}] module into known modules", yangModuleInfo);
                addDependencies(resolvedModules, yangModuleInfo.getImportedModules());
            }
        }

        return Collections.unmodifiableSet((Set)resolvedModules.values().stream().collect(Collectors.toSet()));
    }

    private static void addDependencies(Map<ModuleId, YangModuleInfo> resolvedModules, Collection<YangModuleInfo> importedModules) {
        Iterator var2 = importedModules.iterator();

        while(var2.hasNext()) {
            YangModuleInfo yangModuleInfo = (YangModuleInfo)var2.next();
            resolvedModules.put(ModuleId.from(yangModuleInfo), yangModuleInfo);
            LOG.info("Adding [{}] module into known modules", yangModuleInfo);
            addDependencies(resolvedModules, yangModuleInfo.getImportedModules());
        }

    }

    private static Set<YangModuleInfo> filterYangModelBindingProviders(ModuleId moduleId, ServiceLoader<YangModelBindingProvider> yangProviderLoader) {
        Set<YangModuleInfo> filteredSet = new HashSet();
        Iterator var3 = yangProviderLoader.iterator();

        while(var3.hasNext()) {
            YangModelBindingProvider yangModelBindingProvider = (YangModelBindingProvider)var3.next();
            QName qName = moduleId.getQName();
            QName contrast = yangModelBindingProvider.getModuleInfo().getName();
            if (Objects.equals(qName.getNamespace(), contrast.getNamespace()) && Objects.equals(qName.getLocalName(), contrast.getLocalName()) && Objects.equals(Optional.of(qName.getRevision()).orElse(null), Optional.of(contrast.getRevision()).orElse(null))) {
                filteredSet.add(yangModelBindingProvider.getModuleInfo());
                break;
            }
        }

        return filteredSet;
    }

    private static boolean isDependentModel(Set<YangModuleInfo> models, YangModuleInfo yangModuleInfo) {
        Iterator var2 = models.iterator();

        YangModuleInfo moduleInfo;
        do {
            if (!var2.hasNext()) {
                return false;
            }

            moduleInfo = (YangModuleInfo)var2.next();
        } while(!hasDependency(moduleInfo, yangModuleInfo));

        return true;
    }

    private static boolean hasDependency(YangModuleInfo superiorModel, YangModuleInfo dependency) {
        Iterator var2 = superiorModel.getImportedModules().iterator();

        while(var2.hasNext()) {
            YangModuleInfo moduleInfo = (YangModuleInfo)var2.next();
            if (moduleInfo.getName().equals(dependency.getName())) {
                return true;
            }

            hasDependency(moduleInfo, dependency);
        }

        return false;
    }

    public static ArrayNode generateJSONModelSetConfiguration(Set<YangModuleInfo> models) {
        ObjectMapper mapper = new ObjectMapper();
        ArrayNode arrayNode = mapper.createArrayNode();

        ObjectNode modelObject;
        for(Iterator var3 = models.iterator(); var3.hasNext(); arrayNode.add(modelObject)) {
            YangModuleInfo yangModuleInfo = (YangModuleInfo)var3.next();
            modelObject = mapper.createObjectNode();
            ModuleId moduleId = ModuleId.from(yangModuleInfo);
            modelObject.put("nameSpace", moduleId.getNameSpace().toString());
            modelObject.put("name", moduleId.getName());
            if (moduleId.getRevision() != null) {
                modelObject.put("revision", moduleId.getRevision().toString());
            }
        }

        return arrayNode;
    }
}
