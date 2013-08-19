/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.sal.binding.generator.impl;

import static org.opendaylight.yangtools.binding.generator.util.BindingGeneratorUtil.moduleNamespaceToPackageName;
import static org.opendaylight.yangtools.binding.generator.util.BindingGeneratorUtil.packageNameForGeneratedType;
import static org.opendaylight.yangtools.binding.generator.util.BindingGeneratorUtil.parseToClassName;
import static org.opendaylight.yangtools.binding.generator.util.BindingGeneratorUtil.parseToValidParamName;
import static org.opendaylight.yangtools.binding.generator.util.BindingGeneratorUtil.schemaNodeToTransferObjectBuilder;
import static org.opendaylight.yangtools.yang.model.util.SchemaContextUtil.findDataSchemaNode;
import static org.opendaylight.yangtools.yang.model.util.SchemaContextUtil.findParentModule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import org.opendaylight.yangtools.binding.generator.util.ReferencedTypeImpl;
import org.opendaylight.yangtools.binding.generator.util.Types;
import org.opendaylight.yangtools.binding.generator.util.generated.type.builder.GeneratedTOBuilderImpl;
import org.opendaylight.yangtools.binding.generator.util.generated.type.builder.GeneratedTypeBuilderImpl;
import org.opendaylight.yangtools.sal.binding.generator.api.BindingGenerator;
import org.opendaylight.yangtools.sal.binding.generator.spi.TypeProvider;
import org.opendaylight.yangtools.sal.binding.model.api.ConcreteType;
import org.opendaylight.yangtools.sal.binding.model.api.GeneratedTransferObject;
import org.opendaylight.yangtools.sal.binding.model.api.GeneratedType;
import org.opendaylight.yangtools.sal.binding.model.api.ParameterizedType;
import org.opendaylight.yangtools.sal.binding.model.api.Type;
import org.opendaylight.yangtools.sal.binding.model.api.type.builder.EnumBuilder;
import org.opendaylight.yangtools.sal.binding.model.api.type.builder.GeneratedPropertyBuilder;
import org.opendaylight.yangtools.sal.binding.model.api.type.builder.GeneratedTOBuilder;
import org.opendaylight.yangtools.sal.binding.model.api.type.builder.GeneratedTypeBuilder;
import org.opendaylight.yangtools.sal.binding.model.api.type.builder.MethodSignatureBuilder;
import org.opendaylight.yangtools.sal.binding.yang.types.GroupingDefinitionDependencySort;
import org.opendaylight.yangtools.sal.binding.yang.types.TypeProviderImpl;
import org.opendaylight.yangtools.yang.binding.DataRoot;
import org.opendaylight.yangtools.yang.binding.Identifiable;
import org.opendaylight.yangtools.yang.binding.Identifier;
import org.opendaylight.yangtools.yang.binding.RpcService;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.model.api.AugmentationSchema;
import org.opendaylight.yangtools.yang.model.api.ChoiceCaseNode;
import org.opendaylight.yangtools.yang.model.api.ChoiceNode;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataNodeContainer;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.GroupingDefinition;
import org.opendaylight.yangtools.yang.model.api.IdentitySchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.LeafSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.NotificationDefinition;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaPath;
import org.opendaylight.yangtools.yang.model.api.TypeDefinition;
import org.opendaylight.yangtools.yang.model.api.UsesNode;
import org.opendaylight.yangtools.yang.model.api.type.BitsTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition;
import org.opendaylight.yangtools.yang.model.api.type.EnumTypeDefinition.EnumPair;
import org.opendaylight.yangtools.yang.model.util.DataNodeIterator;
import org.opendaylight.yangtools.yang.model.util.ExtendedType;
import org.opendaylight.yangtools.yang.model.util.SchemaContextUtil;
import org.opendaylight.yangtools.yang.model.util.UnionType;

public final class BindingGeneratorImpl implements BindingGenerator {

    /**
     * Outter key represents the package name. Outter value represents map of
     * all builders in the same package. Inner key represents the schema node
     * name (in JAVA class/interface name format). Inner value represents
     * instance of builder for schema node specified in key part.
     */
    private Map<String, Map<String, GeneratedTypeBuilder>> genTypeBuilders;

    /**
     * Provide methods for converting YANG types to JAVA types.
     */
    private TypeProvider typeProvider;

    /**
     * Holds reference to schema context to resolve data of augmented elemnt
     * when creating augmentation builder
     */
    private SchemaContext schemaContext;

    /**
     * Each grouping which is converted from schema node to generated type is
     * added to this map with its Schema path as key to make it easier to get
     * reference to it. In schema nodes in <code>uses</code> attribute there is
     * only Schema Path but when building list of implemented interfaces for
     * Schema node the object of type <code>Type</code> is required. So in this
     * case is used this map.
     */
    private final Map<SchemaPath, GeneratedType> allGroupings = new HashMap<SchemaPath, GeneratedType>();

    /**
     * Only parent constructor is invoked.
     */
    public BindingGeneratorImpl() {
        super();
    }

    /**
     * Resolves generated types from <code>context</code> schema nodes of all
     * modules.
     * 
     * Generated types are created for modules, groupings, types, containers,
     * lists, choices, augments, rpcs, notification, identities.
     * 
     * @param context
     *            schema context which contains data about all schema nodes
     *            saved in modules
     * @return list of types (usually <code>GeneratedType</code>
     *         <code>GeneratedTransferObject</code>which are generated from
     *         <code>context</code> data.
     * @throws IllegalArgumentException
     *             if param <code>context</code> is null
     * @throws IllegalStateException
     *             if <code>context</code> contain no modules
     */
    @Override
    public List<Type> generateTypes(final SchemaContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Schema Context reference cannot be NULL!");
        }
        if (context.getModules() == null) {
            throw new IllegalStateException("Schema Context does not contain defined modules!");
        }

        final List<Type> generatedTypes = new ArrayList<>();
        schemaContext = context;
        typeProvider = new TypeProviderImpl(context);
        final Set<Module> modules = context.getModules();
        genTypeBuilders = new HashMap<>();
        for (final Module module : modules) {

            generatedTypes.addAll(allGroupingsToGenTypes(module));

            if (false == module.getChildNodes().isEmpty()) {
                generatedTypes.add(moduleToDataType(module));
            }
            generatedTypes.addAll(allTypeDefinitionsToGenTypes(module));
            generatedTypes.addAll(allContainersToGenTypes(module));
            generatedTypes.addAll(allListsToGenTypes(module));
            generatedTypes.addAll(allChoicesToGenTypes(module));
            generatedTypes.addAll(allAugmentsToGenTypes(module));
            generatedTypes.addAll(allRPCMethodsToGenType(module));
            generatedTypes.addAll(allNotificationsToGenType(module));
            generatedTypes.addAll(allIdentitiesToGenTypes(module, context));

        }
        return generatedTypes;
    }

    /**
     * Resolves generated types from <code>context</code> schema nodes only for
     * modules specified in <code>modules</code>
     * 
     * Generated types are created for modules, groupings, types, containers,
     * lists, choices, augments, rpcs, notification, identities.
     * 
     * @param context
     *            schema context which contains data about all schema nodes
     *            saved in modules
     * @param modules
     *            set of modules for which schema nodes should be generated
     *            types
     * @return list of types (usually <code>GeneratedType</code> or
     *         <code>GeneratedTransferObject</code>) which:
     *         <ul>
     *         <li>are generated from <code>context</code> schema nodes and</li>
     *         <li>are also part of some of the module in <code>modules</code>
     *         set</li>.
     *         </ul>
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if param <code>context</code> is null or</li>
     *             <li>if param <code>modules</code> is null</li>
     *             </ul>
     * @throws IllegalStateException
     *             if <code>context</code> contain no modules
     */
    @Override
    public List<Type> generateTypes(final SchemaContext context, final Set<Module> modules) {
        if (context == null) {
            throw new IllegalArgumentException("Schema Context reference cannot be NULL!");
        }
        if (context.getModules() == null) {
            throw new IllegalStateException("Schema Context does not contain defined modules!");
        }
        if (modules == null) {
            throw new IllegalArgumentException("Sef of Modules cannot be NULL!");
        }

        final List<Type> filteredGenTypes = new ArrayList<>();
        schemaContext = context;
        typeProvider = new TypeProviderImpl(context);
        final Set<Module> contextModules = context.getModules();
        genTypeBuilders = new HashMap<>();
        for (final Module contextModule : contextModules) {
            final List<Type> generatedTypes = new ArrayList<>();

            generatedTypes.addAll(allGroupingsToGenTypes(contextModule));
            if (false == contextModule.getChildNodes().isEmpty()) {
                generatedTypes.add(moduleToDataType(contextModule));
            }
            generatedTypes.addAll(allTypeDefinitionsToGenTypes(contextModule));
            generatedTypes.addAll(allContainersToGenTypes(contextModule));
            generatedTypes.addAll(allListsToGenTypes(contextModule));
            generatedTypes.addAll(allChoicesToGenTypes(contextModule));
            generatedTypes.addAll(allAugmentsToGenTypes(contextModule));
            generatedTypes.addAll(allRPCMethodsToGenType(contextModule));
            generatedTypes.addAll(allNotificationsToGenType(contextModule));
            generatedTypes.addAll(allIdentitiesToGenTypes(contextModule, context));

            if (modules.contains(contextModule)) {
                filteredGenTypes.addAll(generatedTypes);
            }
        }
        return filteredGenTypes;
    }

    /**
     * Converts all extended type definitions of module to the list of
     * <code>Type</code> objects.
     * 
     * @param module
     *            module from which is obtained set of type definitions
     * @return list of <code>Type</code> which are generated from extended
     *         definition types (object of type <code>ExtendedType</code>)
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if module equals null</li>
     *             <li>if name of module equals null</li>
     *             <li>if type definitions of module equal null</li>
     *             </ul>
     * 
     */
    private List<Type> allTypeDefinitionsToGenTypes(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }
        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }
        if (module.getTypeDefinitions() == null) {
            throw new IllegalArgumentException("Type Definitions for module " + module.getName() + " cannot be NULL!");
        }

        final Set<TypeDefinition<?>> typeDefinitions = module.getTypeDefinitions();
        final List<Type> generatedTypes = new ArrayList<>();
        for (final TypeDefinition<?> typedef : typeDefinitions) {
            if (typedef != null) {
                final Type type = ((TypeProviderImpl) typeProvider).generatedTypeForExtendedDefinitionType(typedef);
                if ((type != null) && !generatedTypes.contains(type)) {
                    generatedTypes.add(type);
                }
            }
        }
        return generatedTypes;
    }

    /**
     * Converts all <b>containers</b> of the module to the list of
     * <code>Type</code> objects.
     * 
     * @param module
     *            module from which is obtained DataNodeIterator to iterate over
     *            all containers
     * @return list of <code>Type</code> which are generated from containers
     *         (objects of type <code>ContainerSchemaNode</code>)
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the name of module equals null</li>
     *             <li>if the set of child nodes equals null</li>
     *             </ul>
     * 
     */
    private List<Type> allContainersToGenTypes(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }

        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }

        if (module.getChildNodes() == null) {
            throw new IllegalArgumentException("Reference to Set of Child Nodes in module " + module.getName()
                    + " cannot be NULL!");
        }

        final List<Type> generatedTypes = new ArrayList<>();
        final DataNodeIterator it = new DataNodeIterator(module);
        final List<ContainerSchemaNode> schemaContainers = it.allContainers();
        final String basePackageName = moduleNamespaceToPackageName(module);
        for (final ContainerSchemaNode container : schemaContainers) {
            if (!container.isAddedByUses()) {
                generatedTypes.add(containerToGenType(basePackageName, container));
            }
        }
        return generatedTypes;
    }

    /**
     * Converts all <b>lists</b> of the module to the list of <code>Type</code>
     * objects.
     * 
     * @param module
     *            module from which is obtained DataNodeIterator to iterate over
     *            all lists
     * @return list of <code>Type</code> which are generated from lists (objects
     *         of type <code>ListSchemaNode</code>)
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the name of module equals null</li>
     *             <li>if the set of child nodes equals null</li>
     *             </ul>
     * 
     */
    private List<Type> allListsToGenTypes(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }

        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }

        if (module.getChildNodes() == null) {
            throw new IllegalArgumentException("Reference to Set of Child Nodes in module " + module.getName()
                    + " cannot be NULL!");
        }

        final List<Type> generatedTypes = new ArrayList<>();
        final DataNodeIterator it = new DataNodeIterator(module);
        final List<ListSchemaNode> schemaLists = it.allLists();
        final String basePackageName = moduleNamespaceToPackageName(module);
        if (schemaLists != null) {
            for (final ListSchemaNode list : schemaLists) {
                if (!list.isAddedByUses()) {
                    generatedTypes.addAll(listToGenType(basePackageName, list));
                }
            }
        }
        return generatedTypes;
    }

    /**
     * Converts all <b>choices</b> of the module to the list of
     * <code>Type</code> objects.
     * 
     * @param module
     *            module from which is obtained DataNodeIterator to iterate over
     *            all choices
     * @return list of <code>Type</code> which are generated from choices
     *         (objects of type <code>ChoiceNode</code>)
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the name of module equals null</li> *
     *             </ul>
     * 
     */
    private List<GeneratedType> allChoicesToGenTypes(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }
        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }

        final DataNodeIterator it = new DataNodeIterator(module);
        final List<ChoiceNode> choiceNodes = it.allChoices();
        final String basePackageName = moduleNamespaceToPackageName(module);

        final List<GeneratedType> generatedTypes = new ArrayList<>();
        for (final ChoiceNode choice : choiceNodes) {
            if ((choice != null) && !choice.isAddedByUses()) {
                generatedTypes.addAll(choiceToGeneratedType(basePackageName, choice));
            }
        }
        return generatedTypes;
    }

    /**
     * Converts all <b>augmentation</b> of the module to the list
     * <code>Type</code> objects.
     * 
     * @param module
     *            module from which is obtained list of all augmentation objects
     *            to iterate over them
     * @return list of <code>Type</code> which are generated from augments
     *         (objects of type <code>AugmentationSchema</code>)
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the name of module equals null</li>
     *             <li>if the set of child nodes equals null</li>
     *             </ul>
     * 
     */
    private List<Type> allAugmentsToGenTypes(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }
        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }
        if (module.getChildNodes() == null) {
            throw new IllegalArgumentException("Reference to Set of Augmentation Definitions in module "
                    + module.getName() + " cannot be NULL!");
        }

        final List<Type> generatedTypes = new ArrayList<>();
        final String basePackageName = moduleNamespaceToPackageName(module);
        final List<AugmentationSchema> augmentations = resolveAugmentations(module);
        for (final AugmentationSchema augment : augmentations) {
            generatedTypes.addAll(augmentationToGenTypes(basePackageName, augment));
        }
        return generatedTypes;
    }

    /**
     * Returns list of <code>AugmentationSchema</code> objects. The objects are
     * sorted according to the length of their target path from the shortest to
     * the longest.
     * 
     * @param module
     *            module from which is obtained list of all augmentation objects
     * @return list of sorted <code>AugmentationSchema</code> objects obtained
     *         from <code>module</code>
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the set of augmentation equals null</li>
     *             </ul>
     * 
     */
    private List<AugmentationSchema> resolveAugmentations(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }
        if (module.getAugmentations() == null) {
            throw new IllegalStateException("Augmentations Set cannot be NULL!");
        }

        final Set<AugmentationSchema> augmentations = module.getAugmentations();
        final List<AugmentationSchema> sortedAugmentations = new ArrayList<>(augmentations);
        Collections.sort(sortedAugmentations, new Comparator<AugmentationSchema>() {

            @Override
            public int compare(AugmentationSchema augSchema1, AugmentationSchema augSchema2) {

                if (augSchema1.getTargetPath().getPath().size() > augSchema2.getTargetPath().getPath().size()) {
                    return 1;
                } else if (augSchema1.getTargetPath().getPath().size() < augSchema2.getTargetPath().getPath().size()) {
                    return -1;
                }
                return 0;

            }
        });

        return sortedAugmentations;
    }

    /**
     * Converts whole <b>module</b> to <code>GeneratedType</code> object.
     * Firstly is created the module builder object from which is finally
     * obtained reference to <code>GeneratedType</code> object.
     * 
     * @param module
     *            module from which are obtained the module name, child nodes,
     *            uses and is derived package name
     * @return <code>GeneratedType</code> which is internal representation of
     *         the module
     * @throws IllegalArgumentException
     *             if the module equals null
     * 
     */
    private GeneratedType moduleToDataType(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }

        final GeneratedTypeBuilder moduleDataTypeBuilder = moduleTypeBuilder(module, "Data");
        addImplementedInterfaceFromUses(module, moduleDataTypeBuilder);
        moduleDataTypeBuilder.addImplementsType(Types.typeForClass(DataRoot.class));

        final String basePackageName = moduleNamespaceToPackageName(module);
        if (moduleDataTypeBuilder != null) {
            final Set<DataSchemaNode> dataNodes = module.getChildNodes();
            resolveDataSchemaNodes(basePackageName, moduleDataTypeBuilder, dataNodes);
        }
        return moduleDataTypeBuilder.toInstance();
    }

    /**
     * Converts all <b>rpcs</b> inputs and outputs substatements of the module
     * to the list of <code>Type</code> objects. In addition are to containers
     * and lists which belong to input or output also part of returning list.
     * 
     * @param module
     *            module from which is obtained set of all rpc objects to
     *            iterate over them
     * @return list of <code>Type</code> which are generated from rpcs inputs,
     *         outputs + container and lists which are part of inputs or outputs
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the name of module equals null</li>
     *             <li>if the set of child nodes equals null</li>
     *             </ul>
     * 
     */
    private List<Type> allRPCMethodsToGenType(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }

        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }

        if (module.getChildNodes() == null) {
            throw new IllegalArgumentException("Reference to Set of RPC Method Definitions in module "
                    + module.getName() + " cannot be NULL!");
        }

        final String basePackageName = moduleNamespaceToPackageName(module);
        final Set<RpcDefinition> rpcDefinitions = module.getRpcs();

        if (rpcDefinitions.isEmpty()) {
            return Collections.emptyList();
        }

        final List<Type> genRPCTypes = new ArrayList<>();
        final GeneratedTypeBuilder interfaceBuilder = moduleTypeBuilder(module, "Service");
        interfaceBuilder.addImplementsType(Types.typeForClass(RpcService.class));
        final Type future = Types.typeForClass(Future.class);
        for (final RpcDefinition rpc : rpcDefinitions) {
            if (rpc != null) {

                String rpcName = parseToClassName(rpc.getQName().getLocalName());
                String rpcMethodName = parseToValidParamName(rpcName);
                MethodSignatureBuilder method = interfaceBuilder.addMethod(rpcMethodName);

                final List<DataNodeIterator> rpcInOut = new ArrayList<>();

                ContainerSchemaNode input = rpc.getInput();
                ContainerSchemaNode output = rpc.getOutput();

                if (input != null) {
                    rpcInOut.add(new DataNodeIterator(input));
                    GeneratedTypeBuilder inType = addRawInterfaceDefinition(basePackageName, input, rpcName);
                    addImplementedInterfaceFromUses(input, inType);
                    inType.addImplementsType(Types.DATA_OBJECT);
                    resolveDataSchemaNodes(basePackageName, inType, input.getChildNodes());
                    Type inTypeInstance = inType.toInstance();
                    genRPCTypes.add(inTypeInstance);
                    method.addParameter(inTypeInstance, "input");
                }

                Type outTypeInstance = Types.typeForClass(Void.class);
                if (output != null) {
                    rpcInOut.add(new DataNodeIterator(output));
                    GeneratedTypeBuilder outType = addRawInterfaceDefinition(basePackageName, output, rpcName);
                    addImplementedInterfaceFromUses(output, outType);
                    outType.addImplementsType(Types.DATA_OBJECT);
                    resolveDataSchemaNodes(basePackageName, outType, output.getChildNodes());
                    outTypeInstance = outType.toInstance();
                    genRPCTypes.add(outTypeInstance);

                }

                final Type rpcRes = Types.parameterizedTypeFor(Types.typeForClass(RpcResult.class), outTypeInstance);
                method.setReturnType(Types.parameterizedTypeFor(future, rpcRes));
                for (DataNodeIterator it : rpcInOut) {
                    List<ContainerSchemaNode> nContainers = it.allContainers();
                    if ((nContainers != null) && !nContainers.isEmpty()) {
                        for (final ContainerSchemaNode container : nContainers) {
                            if (!container.isAddedByUses()) {
                                genRPCTypes.add(containerToGenType(basePackageName, container));
                            }
                        }
                    }
                    List<ListSchemaNode> nLists = it.allLists();
                    if ((nLists != null) && !nLists.isEmpty()) {
                        for (final ListSchemaNode list : nLists) {
                            if (!list.isAddedByUses()) {
                                genRPCTypes.addAll(listToGenType(basePackageName, list));
                            }
                        }
                    }
                }
            }
        }
        genRPCTypes.add(interfaceBuilder.toInstance());
        return genRPCTypes;
    }

    /**
     * Converts all <b>notifications</b> of the module to the list of
     * <code>Type</code> objects. In addition are to this list added containers
     * and lists which are part of this notification.
     * 
     * @param module
     *            module from which is obtained set of all notification objects
     *            to iterate over them
     * @return list of <code>Type</code> which are generated from notification
     *         (object of type <code>NotificationDefinition</code>
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if the module equals null</li>
     *             <li>if the name of module equals null</li>
     *             <li>if the set of child nodes equals null</li>
     *             </ul>
     * 
     */
    private List<Type> allNotificationsToGenType(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }

        if (module.getName() == null) {
            throw new IllegalArgumentException("Module name cannot be NULL!");
        }

        if (module.getChildNodes() == null) {
            throw new IllegalArgumentException("Reference to Set of Notification Definitions in module "
                    + module.getName() + " cannot be NULL!");
        }

        final String basePackageName = moduleNamespaceToPackageName(module);
        final List<Type> genNotifyTypes = new ArrayList<>();
        final Set<NotificationDefinition> notifications = module.getNotifications();

        for (final NotificationDefinition notification : notifications) {
            if (notification != null) {
                DataNodeIterator it = new DataNodeIterator(notification);

                // Containers
                for (ContainerSchemaNode node : it.allContainers()) {
                    if (!node.isAddedByUses()) {
                        genNotifyTypes.add(containerToGenType(basePackageName, node));
                    }
                }
                // Lists
                for (ListSchemaNode node : it.allLists()) {
                    if (!node.isAddedByUses()) {
                        genNotifyTypes.addAll(listToGenType(basePackageName, node));
                    }
                }
                final GeneratedTypeBuilder notificationTypeBuilder = addDefaultInterfaceDefinition(basePackageName,
                        notification);
                notificationTypeBuilder.addImplementsType(Types
                        .typeForClass(org.opendaylight.yangtools.yang.binding.Notification.class));
                // Notification object
                resolveDataSchemaNodes(basePackageName, notificationTypeBuilder, notification.getChildNodes());
                genNotifyTypes.add(notificationTypeBuilder.toInstance());
            }
        }
        return genNotifyTypes;
    }

    /**
     * Converts all <b>identities</b> of the module to the list of
     * <code>Type</code> objects.
     * 
     * @param module
     *            module from which is obtained set of all identity objects to
     *            iterate over them
     * @param context
     *            schema context only used as input parameter for method
     *            {@link identityToGenType}
     * @return list of <code>Type</code> which are generated from identities
     *         (object of type <code>IdentitySchemaNode</code>
     * 
     */
    private List<Type> allIdentitiesToGenTypes(final Module module, final SchemaContext context) {
        List<Type> genTypes = new ArrayList<>();

        final Set<IdentitySchemaNode> schemaIdentities = module.getIdentities();

        final String basePackageName = moduleNamespaceToPackageName(module);

        if (schemaIdentities != null && !schemaIdentities.isEmpty()) {
            for (final IdentitySchemaNode identity : schemaIdentities) {
                genTypes.add(identityToGenType(basePackageName, identity, context));
            }
        }
        return genTypes;
    }

    /**
     * Converts the <b>identity</b> object to GeneratedType. Firstly it is
     * created transport object builder. If identity contains base identity then
     * reference to base identity is added to superior identity as its extend.
     * If identity doesn't contain base identity then only reference to abstract
     * class {@link org.opendaylight.yangtools.yang.model.api.BaseIdentity
     * BaseIdentity} is added
     * 
     * @param basePackageName
     *            string containing package name to which identity belongs
     * @param identity
     *            IdentitySchemaNode which contains data about identity
     * @param context
     *            SchemaContext which is used to get package and name
     *            information about base of identity
     * 
     * @return GeneratedType which is generated from identity (object of type
     *         <code>IdentitySchemaNode</code>
     * 
     */
    private GeneratedType identityToGenType(final String basePackageName, final IdentitySchemaNode identity,
            final SchemaContext context) {
        if (identity == null) {
            return null;
        }

        final String packageName = packageNameForGeneratedType(basePackageName, identity.getPath());
        final String genTypeName = parseToClassName(identity.getQName().getLocalName());
        final GeneratedTOBuilderImpl newType = new GeneratedTOBuilderImpl(packageName, genTypeName);

        IdentitySchemaNode baseIdentity = identity.getBaseIdentity();
        if (baseIdentity != null) {
            Module baseIdentityParentModule = SchemaContextUtil.findParentModule(context, baseIdentity);

            final String returnTypePkgName = moduleNamespaceToPackageName(baseIdentityParentModule);
            final String returnTypeName = parseToClassName(baseIdentity.getQName().getLocalName());

            GeneratedTransferObject gto = new GeneratedTOBuilderImpl(returnTypePkgName, returnTypeName).toInstance();
            newType.setExtendsType(gto);
        } else {
            newType.setExtendsType(Types.getBaseIdentityTO());
        }
        newType.setAbstract(true);
        return newType.toInstance();
    }

    /**
     * Converts all <b>groupings</b> of the module to the list of
     * <code>Type</code> objects. Firstly are groupings sorted according mutual
     * dependencies. At least dependend (indepedent) groupings are in the list
     * saved at first positions. For every grouping the record is added to map
     * {@link BindingGeneratorImpl#allGroupings allGroupings}
     * 
     * @param module
     *            module from which is obtained set of all grouping objects to
     *            iterate over them
     * @return list of <code>Type</code> which are generated from groupings
     *         (object of type <code>GroupingDefinition</code>)
     * 
     */
    private List<Type> allGroupingsToGenTypes(final Module module) {
        if (module == null) {
            throw new IllegalArgumentException("Module parameter can not be null");
        }
        final List<Type> genTypes = new ArrayList<>();
        final String basePackageName = moduleNamespaceToPackageName(module);
        final Set<GroupingDefinition> groupings = module.getGroupings();
        List<GroupingDefinition> groupingsSortedByDependencies;

        groupingsSortedByDependencies = GroupingDefinitionDependencySort.sort(groupings);

        for (final GroupingDefinition grouping : groupingsSortedByDependencies) {
            GeneratedType genType = groupingToGenType(basePackageName, grouping);
            genTypes.add(genType);
            SchemaPath schemaPath = grouping.getPath();
            allGroupings.put(schemaPath, genType);
        }
        return genTypes;
    }

    /**
     * Converts individual grouping to GeneratedType. Firstly generated type
     * builder is created and every child node of grouping is resolved to the
     * method.
     * 
     * @param basePackageName
     *            string containing name of package to which grouping belongs.
     * @param grouping
     *            GroupingDefinition which contains data about grouping
     * @return GeneratedType which is generated from grouping (object of type
     *         <code>GroupingDefinition</code>)
     */
    private GeneratedType groupingToGenType(final String basePackageName, GroupingDefinition grouping) {
        if (grouping == null) {
            return null;
        }

        final String packageName = packageNameForGeneratedType(basePackageName, grouping.getPath());
        final Set<DataSchemaNode> schemaNodes = grouping.getChildNodes();
        final GeneratedTypeBuilder typeBuilder = addDefaultInterfaceDefinition(packageName, grouping);

        resolveDataSchemaNodes(basePackageName, typeBuilder, schemaNodes);
        return typeBuilder.toInstance();
    }

    /**
     * Tries to find EnumTypeDefinition in <code>typeDefinition</code>. If base
     * type of <code>typeDefinition</code> is of the type ExtendedType then this
     * method is recursivelly called with this base type.
     * 
     * @param typeDefinition
     *            TypeDefinition in which should be EnumTypeDefinition found as
     *            base type
     * @return EnumTypeDefinition if it is found inside
     *         <code>typeDefinition</code> or <code>null</code> in other case
     */
    private EnumTypeDefinition enumTypeDefFromExtendedType(final TypeDefinition<?> typeDefinition) {
        if (typeDefinition != null) {
            if (typeDefinition.getBaseType() instanceof EnumTypeDefinition) {
                return (EnumTypeDefinition) typeDefinition.getBaseType();
            } else if (typeDefinition.getBaseType() instanceof ExtendedType) {
                return enumTypeDefFromExtendedType(typeDefinition.getBaseType());
            }
        }
        return null;
    }

    /**
     * Adds enumeration builder created from <code>enumTypeDef</code> to
     * <code>typeBuilder</code>.
     * 
     * Each <code>enumTypeDef</code> item is added to builder with its name and
     * value.
     * 
     * @param enumTypeDef
     *            EnumTypeDefinition contains enum data
     * @param enumName
     *            string contains name which will be assigned to enumeration
     *            builder
     * @param typeBuilder
     *            GeneratedTypeBuilder to which will be enum builder assigned
     * @return enumeration builder which contais data from
     *         <code>enumTypeDef</code>
     */
    private EnumBuilder resolveInnerEnumFromTypeDefinition(final EnumTypeDefinition enumTypeDef, final String enumName,
            final GeneratedTypeBuilder typeBuilder) {
        if ((enumTypeDef != null) && (typeBuilder != null) && (enumTypeDef.getQName() != null)
                && (enumTypeDef.getQName().getLocalName() != null)) {

            final String enumerationName = parseToClassName(enumName);
            final EnumBuilder enumBuilder = typeBuilder.addEnumeration(enumerationName);

            if (enumBuilder != null) {
                final List<EnumPair> enums = enumTypeDef.getValues();
                if (enums != null) {
                    int listIndex = 0;
                    for (final EnumPair enumPair : enums) {
                        if (enumPair != null) {
                            final String enumPairName = parseToClassName(enumPair.getName());
                            Integer enumPairValue = enumPair.getValue();

                            if (enumPairValue == null) {
                                enumPairValue = listIndex;
                            }
                            enumBuilder.addValue(enumPairName, enumPairValue);
                            listIndex++;
                        }
                    }
                }
                return enumBuilder;
            }
        }
        return null;
    }

    /**
     * Generates type builder for <code>module</code>.
     * 
     * @param module
     *            Module which is source of package name for generated type
     *            builder
     * @param postfix
     *            string which is added to the module class name representation
     *            as suffix
     * @return instance of GeneratedTypeBuilder which represents
     *         <code>module</code>.
     * @throws IllegalArgumentException
     *             if <code>module</code> equals null
     */
    private GeneratedTypeBuilder moduleTypeBuilder(final Module module, final String postfix) {
        if (module == null) {
            throw new IllegalArgumentException("Module reference cannot be NULL!");
        }
        String packageName = moduleNamespaceToPackageName(module);
        final String moduleName = parseToClassName(module.getName()) + postfix;

        return new GeneratedTypeBuilderImpl(packageName, moduleName);

    }

    /**
     * Converts <code>augSchema</code> to list of <code>Type</code> which
     * contains generated type for augmentation. In addition there are also
     * generated types for all containers, list and choices which are child of
     * <code>augSchema</code> node or a generated types for cases are added if
     * augmented node is choice.
     * 
     * @param augmentPackageName
     *            string with the name of the package to which the augmentation
     *            belongs
     * @param augSchema
     *            AugmentationSchema which is contains data about agumentation
     *            (target path, childs...)
     * @return list of <code>Type</code> objects which contains generated type
     *         for augmentation and for container, list and choice child nodes
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if <code>augmentPackageName</code> equals null</li>
     *             <li>if <code>augSchema</code> equals null</li>
     *             <li>if target path of <code>augSchema</code> equals null</li>
     *             </ul>
     */
    private List<Type> augmentationToGenTypes(final String augmentPackageName, final AugmentationSchema augSchema) {
        if (augmentPackageName == null) {
            throw new IllegalArgumentException("Package Name cannot be NULL!");
        }
        if (augSchema == null) {
            throw new IllegalArgumentException("Augmentation Schema cannot be NULL!");
        }
        if (augSchema.getTargetPath() == null) {
            throw new IllegalStateException("Augmentation Schema does not contain Target Path (Target Path is NULL).");
        }

        final List<Type> genTypes = new ArrayList<>();

        // EVERY augmented interface will extends Augmentation<T> interface
        // and DataObject interface!!!
        final SchemaPath targetPath = augSchema.getTargetPath();
        final DataSchemaNode targetSchemaNode = findDataSchemaNode(schemaContext, targetPath);
        if ((targetSchemaNode != null) && (targetSchemaNode.getQName() != null)
                && (targetSchemaNode.getQName().getLocalName() != null)) {
            final Module targetModule = findParentModule(schemaContext, targetSchemaNode);
            final String targetBasePackage = moduleNamespaceToPackageName(targetModule);
            final String targetPackageName = packageNameForGeneratedType(targetBasePackage, targetSchemaNode.getPath());
            final String targetSchemaNodeName = targetSchemaNode.getQName().getLocalName();
            final Set<DataSchemaNode> augChildNodes = augSchema.getChildNodes();

            if (!(targetSchemaNode instanceof ChoiceNode)) {
                final GeneratedTypeBuilder augTypeBuilder = addRawAugmentGenTypeDefinition(augmentPackageName,
                        targetPackageName, targetSchemaNodeName, augSchema);
                final GeneratedType augType = augTypeBuilder.toInstance();
                genTypes.add(augType);
            } else {
                final Type refChoiceType = new ReferencedTypeImpl(targetPackageName,
                        parseToClassName(targetSchemaNodeName));
                final ChoiceNode choiceTarget = (ChoiceNode) targetSchemaNode;
                final Set<ChoiceCaseNode> choiceCaseNodes = choiceTarget.getCases();
                genTypes.addAll(generateTypesFromAugmentedChoiceCases(augmentPackageName, refChoiceType,
                        choiceCaseNodes));
            }
            genTypes.addAll(augmentationBodyToGenTypes(augmentPackageName, augChildNodes));
        }
        return genTypes;
    }

    /**
     * Returns a generated type builder for an augmentation.
     * 
     * The name of the type builder is equal to the name of augmented node with
     * serial number as suffix.
     * 
     * @param augmentPackageName
     *            string with contains the package name to which the augment
     *            belongs
     * @param targetPackageName
     *            string with the package name to which the augmented node
     *            belongs
     * @param targetSchemaNodeName
     *            string with the name of the augmented node
     * @param augSchema
     *            augmentation schema which contains data about the child nodes
     *            and uses of augment
     * @return generated type builder for augment
     */
    private GeneratedTypeBuilder addRawAugmentGenTypeDefinition(final String augmentPackageName,
            final String targetPackageName, final String targetSchemaNodeName, final AugmentationSchema augSchema) {
        final String targetTypeName = parseToClassName(targetSchemaNodeName);
        Map<String, GeneratedTypeBuilder> augmentBuilders = genTypeBuilders.get(augmentPackageName);
        if (augmentBuilders == null) {
            augmentBuilders = new HashMap<>();
            genTypeBuilders.put(augmentPackageName, augmentBuilders);
        }

        final String augTypeName = augGenTypeName(augmentBuilders, targetTypeName);
        final Type targetTypeRef = new ReferencedTypeImpl(targetPackageName, targetTypeName);
        final Set<DataSchemaNode> augChildNodes = augSchema.getChildNodes();

        final GeneratedTypeBuilder augTypeBuilder = new GeneratedTypeBuilderImpl(augmentPackageName, augTypeName);

        augTypeBuilder.addImplementsType(Types.DATA_OBJECT);
        augTypeBuilder.addImplementsType(Types.augmentationTypeFor(targetTypeRef));
        addImplementedInterfaceFromUses(augSchema, augTypeBuilder);

        augSchemaNodeToMethods(augmentPackageName, augTypeBuilder, augChildNodes);
        augmentBuilders.put(augTypeName, augTypeBuilder);
        return augTypeBuilder;
    }

    /**
     * Convert a container, list and choice subnodes (and recursivelly their
     * subnodes) of augment to generated types
     * 
     * @param augBasePackageName
     *            string with the augment package name
     * @param augChildNodes
     *            set of data schema nodes which represents child nodes of the
     *            augment
     * 
     * @return list of <code>Type</code> which represents container, list and
     *         choice subnodes of augment
     */
    private List<Type> augmentationBodyToGenTypes(final String augBasePackageName,
            final Set<DataSchemaNode> augChildNodes) {
        final List<Type> genTypes = new ArrayList<>();
        final List<DataNodeIterator> augSchemaIts = new ArrayList<>();
        for (final DataSchemaNode childNode : augChildNodes) {
            if (childNode instanceof DataNodeContainer) {
                augSchemaIts.add(new DataNodeIterator((DataNodeContainer) childNode));

                if (childNode instanceof ContainerSchemaNode) {
                    genTypes.add(containerToGenType(augBasePackageName, (ContainerSchemaNode) childNode));
                } else if (childNode instanceof ListSchemaNode) {
                    genTypes.addAll(listToGenType(augBasePackageName, (ListSchemaNode) childNode));
                }
            } else if (childNode instanceof ChoiceNode) {
                final ChoiceNode choice = (ChoiceNode) childNode;
                for (final ChoiceCaseNode caseNode : choice.getCases()) {
                    augSchemaIts.add(new DataNodeIterator(caseNode));
                }
                genTypes.addAll(choiceToGeneratedType(augBasePackageName, (ChoiceNode) childNode));
            }
        }

        for (final DataNodeIterator it : augSchemaIts) {
            final List<ContainerSchemaNode> augContainers = it.allContainers();
            final List<ListSchemaNode> augLists = it.allLists();
            final List<ChoiceNode> augChoices = it.allChoices();

            if (augContainers != null) {
                for (final ContainerSchemaNode container : augContainers) {
                    genTypes.add(containerToGenType(augBasePackageName, container));
                }
            }
            if (augLists != null) {
                for (final ListSchemaNode list : augLists) {
                    genTypes.addAll(listToGenType(augBasePackageName, list));
                }
            }
            if (augChoices != null) {
                for (final ChoiceNode choice : augChoices) {
                    genTypes.addAll(choiceToGeneratedType(augBasePackageName, choice));
                }
            }
        }
        return genTypes;
    }

    /**
     * Returns first unique name for the augment generated type builder. The
     * generated type builder name for augment consists from name of augmented
     * node and serial number of its augmentation.
     * 
     * @param builders
     *            map of builders which were created in the package to which the
     *            augmentation belongs
     * @param genTypeName
     *            string with name of augmented node
     * @return string with unique name for augmentation builder
     */
    private String augGenTypeName(final Map<String, GeneratedTypeBuilder> builders, final String genTypeName) {
        String augTypeName = genTypeName;

        int index = 1;
        while ((builders != null) && builders.containsKey(genTypeName + index)) {
            index++;
        }
        augTypeName += index;
        return augTypeName;
    }

    /**
     * Converts <code>containerNode</code> to generated type. Firstly the
     * generated type builder is created. The subnodes of
     * <code>containerNode</code> are added as methods and the instance of
     * <code>GeneratedType</code> is returned.
     * 
     * @param basePackageName
     *            string with name of the package to which the superior node
     *            belongs
     * @param containerNode
     *            container schema node with the data about childs nodes and
     *            schema paths
     * @return generated type for <code>containerNode</code>
     */
    private GeneratedType containerToGenType(final String basePackageName, ContainerSchemaNode containerNode) {
        if (containerNode == null) {
            return null;
        }

        final String packageName = packageNameForGeneratedType(basePackageName, containerNode.getPath());
        final Set<DataSchemaNode> schemaNodes = containerNode.getChildNodes();
        final GeneratedTypeBuilder typeBuilder = addDefaultInterfaceDefinition(packageName, containerNode);

        resolveDataSchemaNodes(basePackageName, typeBuilder, schemaNodes);
        return typeBuilder.toInstance();
    }

    /**
     * 
     * @param basePackageName
     * @param typeBuilder
     * @param schemaNodes
     * @return
     */
    private GeneratedTypeBuilder resolveDataSchemaNodes(final String basePackageName,
            final GeneratedTypeBuilder typeBuilder, final Set<DataSchemaNode> schemaNodes) {
        if ((schemaNodes != null) && (typeBuilder != null)) {
            for (final DataSchemaNode schemaNode : schemaNodes) {
                if (schemaNode.isAugmenting() || schemaNode.isAddedByUses()) {
                    continue;
                }
                addSchemaNodeToBuilderAsMethod(basePackageName, schemaNode, typeBuilder);
            }
        }
        return typeBuilder;
    }

    private GeneratedTypeBuilder augSchemaNodeToMethods(final String basePackageName,
            final GeneratedTypeBuilder typeBuilder, final Set<DataSchemaNode> schemaNodes) {
        if ((schemaNodes != null) && (typeBuilder != null)) {
            for (final DataSchemaNode schemaNode : schemaNodes) {
                if (schemaNode.isAugmenting()) {
                    addSchemaNodeToBuilderAsMethod(basePackageName, schemaNode, typeBuilder);
                }
            }
        }
        return typeBuilder;
    }

    private void addSchemaNodeToBuilderAsMethod(final String basePackageName, final DataSchemaNode schemaNode,
            final GeneratedTypeBuilder typeBuilder) {
        if (schemaNode != null && typeBuilder != null) {
            if (schemaNode instanceof LeafSchemaNode) {
                resolveLeafSchemaNodeAsMethod(typeBuilder, (LeafSchemaNode) schemaNode);
            } else if (schemaNode instanceof LeafListSchemaNode) {
                resolveLeafListSchemaNode(typeBuilder, (LeafListSchemaNode) schemaNode);
            } else if (schemaNode instanceof ContainerSchemaNode) {
                resolveContainerSchemaNode(basePackageName, typeBuilder, (ContainerSchemaNode) schemaNode);
            } else if (schemaNode instanceof ListSchemaNode) {
                resolveListSchemaNode(basePackageName, typeBuilder, (ListSchemaNode) schemaNode);
            } else if (schemaNode instanceof ChoiceNode) {
                resolveChoiceSchemaNode(basePackageName, typeBuilder, (ChoiceNode) schemaNode);
            }
        }
    }

    private void resolveChoiceSchemaNode(final String basePackageName, final GeneratedTypeBuilder typeBuilder,
            final ChoiceNode choiceNode) {
        if (basePackageName == null) {
            throw new IllegalArgumentException("Base Package Name cannot be NULL!");
        }
        if (typeBuilder == null) {
            throw new IllegalArgumentException("Generated Type Builder cannot be NULL!");
        }
        if (choiceNode == null) {
            throw new IllegalArgumentException("Choice Schema Node cannot be NULL!");
        }

        final String choiceName = choiceNode.getQName().getLocalName();
        if (choiceName != null && !choiceNode.isAddedByUses()) {
            final String packageName = packageNameForGeneratedType(basePackageName, choiceNode.getPath());
            final GeneratedTypeBuilder choiceType = addDefaultInterfaceDefinition(packageName, choiceNode);
            constructGetter(typeBuilder, choiceName, choiceNode.getDescription(), choiceType);
        }
    }

    private List<GeneratedType> choiceToGeneratedType(final String basePackageName, final ChoiceNode choiceNode) {
        if (basePackageName == null) {
            throw new IllegalArgumentException("Base Package Name cannot be NULL!");
        }
        if (choiceNode == null) {
            throw new IllegalArgumentException("Choice Schema Node cannot be NULL!");
        }

        final List<GeneratedType> generatedTypes = new ArrayList<>();
        final String packageName = packageNameForGeneratedType(basePackageName, choiceNode.getPath());
        final GeneratedTypeBuilder choiceTypeBuilder = addRawInterfaceDefinition(packageName, choiceNode);
        choiceTypeBuilder.addImplementsType(Types.DATA_OBJECT);
        final GeneratedType choiceType = choiceTypeBuilder.toInstance();

        generatedTypes.add(choiceType);
        final Set<ChoiceCaseNode> caseNodes = choiceNode.getCases();
        if ((caseNodes != null) && !caseNodes.isEmpty()) {
            generatedTypes.addAll(generateTypesFromChoiceCases(basePackageName, choiceType, caseNodes));
        }
        return generatedTypes;
    }

    private List<GeneratedType> generateTypesFromChoiceCases(final String basePackageName, final Type refChoiceType,
            final Set<ChoiceCaseNode> caseNodes) {
        if (basePackageName == null) {
            throw new IllegalArgumentException("Base Package Name cannot be NULL!");
        }
        if (refChoiceType == null) {
            throw new IllegalArgumentException("Referenced Choice Type cannot be NULL!");
        }
        if (caseNodes == null) {
            throw new IllegalArgumentException("Set of Choice Case Nodes cannot be NULL!");
        }

        final List<GeneratedType> generatedTypes = new ArrayList<>();
        for (final ChoiceCaseNode caseNode : caseNodes) {
            if (caseNode != null && !caseNode.isAddedByUses() && !caseNode.isAugmenting()) {
                final String packageName = packageNameForGeneratedType(basePackageName, caseNode.getPath());
                final GeneratedTypeBuilder caseTypeBuilder = addDefaultInterfaceDefinition(packageName, caseNode);
                caseTypeBuilder.addImplementsType(refChoiceType);

                final Set<DataSchemaNode> childNodes = caseNode.getChildNodes();
                if (childNodes != null) {
                    resolveDataSchemaNodes(basePackageName, caseTypeBuilder, childNodes);
                }
                generatedTypes.add(caseTypeBuilder.toInstance());
            }
        }

        return generatedTypes;
    }

    /**
     * Generates list of generated types for all the cases of a choice which are
     * added to the choice through the augment.
     * 
     * 
     * @param basePackageName
     *            string contains name of package to which augment belongs. If
     *            an augmented choice is from an other package (pcg1) than an
     *            augmenting choice (pcg2) then case's of the augmenting choice
     *            will belong to pcg2.
     * @param refChoiceType
     *            Type which represents the choice to which case belongs. Every
     *            case has to contain its choice in extend part.
     * @param caseNodes
     *            set of choice case nodes for which is checked if are/aren't
     *            added to choice through augmentation
     * @return list of generated types which represents augmented cases of
     *         choice <code>refChoiceType</code>
     * @throws IllegalArgumentException
     *             <ul>
     *             <li>if <code>basePackageName</code> equals null</li>
     *             <li>if <code>refChoiceType</code> equals null</li>
     *             <li>if <code>caseNodes</code> equals null</li>
     *             </ul>
     */
    private List<GeneratedType> generateTypesFromAugmentedChoiceCases(final String basePackageName,
            final Type refChoiceType, final Set<ChoiceCaseNode> caseNodes) {
        if (basePackageName == null) {
            throw new IllegalArgumentException("Base Package Name cannot be NULL!");
        }
        if (refChoiceType == null) {
            throw new IllegalArgumentException("Referenced Choice Type cannot be NULL!");
        }
        if (caseNodes == null) {
            throw new IllegalArgumentException("Set of Choice Case Nodes cannot be NULL!");
        }

        final List<GeneratedType> generatedTypes = new ArrayList<>();
        for (final ChoiceCaseNode caseNode : caseNodes) {
            if (caseNode != null && caseNode.isAugmenting()) {
                final String packageName = packageNameForGeneratedType(basePackageName, caseNode.getPath());
                final GeneratedTypeBuilder caseTypeBuilder = addDefaultInterfaceDefinition(packageName, caseNode);
                caseTypeBuilder.addImplementsType(refChoiceType);

                final Set<DataSchemaNode> childNodes = caseNode.getChildNodes();
                if (childNodes != null) {
                    resolveDataSchemaNodes(basePackageName, caseTypeBuilder, childNodes);
                }
                generatedTypes.add(caseTypeBuilder.toInstance());
            }
        }

        return generatedTypes;
    }

    private boolean resolveLeafSchemaNodeAsMethod(final GeneratedTypeBuilder typeBuilder, final LeafSchemaNode leaf) {
        if ((leaf != null) && (typeBuilder != null)) {
            final String leafName = leaf.getQName().getLocalName();
            String leafDesc = leaf.getDescription();
            if (leafDesc == null) {
                leafDesc = "";
            }

            if (leafName != null && !leaf.isAddedByUses()) {
                final TypeDefinition<?> typeDef = leaf.getType();

                Type returnType = null;
                if (typeDef instanceof EnumTypeDefinition) {
                    returnType = typeProvider.javaTypeForSchemaDefinitionType(typeDef);
                    final EnumTypeDefinition enumTypeDef = enumTypeDefFromExtendedType(typeDef);
                    final EnumBuilder enumBuilder = resolveInnerEnumFromTypeDefinition(enumTypeDef, leafName,
                            typeBuilder);

                    if (enumBuilder != null) {
                        returnType = new ReferencedTypeImpl(enumBuilder.getPackageName(), enumBuilder.getName());
                    }
                    ((TypeProviderImpl) typeProvider).putReferencedType(leaf.getPath(), returnType);
                } else if (typeDef instanceof UnionType) {
                    GeneratedTOBuilder genTOBuilder = addEnclosedTOToTypeBuilder(typeDef, typeBuilder, leafName);
                    if (genTOBuilder != null) {
                        returnType = new ReferencedTypeImpl(genTOBuilder.getPackageName(), genTOBuilder.getName());
                    }
                } else if (typeDef instanceof BitsTypeDefinition) {
                    GeneratedTOBuilder genTOBuilder = addEnclosedTOToTypeBuilder(typeDef, typeBuilder, leafName);
                    if (genTOBuilder != null) {
                        returnType = new ReferencedTypeImpl(genTOBuilder.getPackageName(), genTOBuilder.getName());
                    }
                } else {
                    returnType = typeProvider.javaTypeForSchemaDefinitionType(typeDef);
                }
                if (returnType != null) {
                    constructGetter(typeBuilder, leafName, leafDesc, returnType);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean resolveLeafSchemaNodeAsProperty(final GeneratedTOBuilder toBuilder, final LeafSchemaNode leaf,
            boolean isReadOnly) {
        if ((leaf != null) && (toBuilder != null)) {
            final String leafName = leaf.getQName().getLocalName();
            String leafDesc = leaf.getDescription();
            if (leafDesc == null) {
                leafDesc = "";
            }

            if (leafName != null && !leaf.isAddedByUses()) {
                final TypeDefinition<?> typeDef = leaf.getType();

                // TODO: properly resolve enum types
                final Type returnType = typeProvider.javaTypeForSchemaDefinitionType(typeDef);

                if (returnType != null) {
                    final GeneratedPropertyBuilder propBuilder = toBuilder.addProperty(parseToClassName(leafName));

                    propBuilder.setReadOnly(isReadOnly);
                    propBuilder.setReturnType(returnType);
                    propBuilder.setComment(leafDesc);

                    toBuilder.addEqualsIdentity(propBuilder);
                    toBuilder.addHashIdentity(propBuilder);
                    toBuilder.addToStringProperty(propBuilder);

                    return true;
                }
            }
        }
        return false;
    }

    private boolean resolveLeafListSchemaNode(final GeneratedTypeBuilder typeBuilder, final LeafListSchemaNode node) {
        if ((node != null) && (typeBuilder != null)) {
            final String nodeName = node.getQName().getLocalName();
            String nodeDesc = node.getDescription();
            if (nodeDesc == null) {
                nodeDesc = "";
            }

            if (nodeName != null && !node.isAddedByUses()) {
                final TypeDefinition<?> type = node.getType();
                final Type listType = Types.listTypeFor(typeProvider.javaTypeForSchemaDefinitionType(type));

                constructGetter(typeBuilder, nodeName, nodeDesc, listType);
                return true;
            }
        }
        return false;
    }

    private boolean resolveContainerSchemaNode(final String basePackageName, final GeneratedTypeBuilder typeBuilder,
            final ContainerSchemaNode containerNode) {
        if ((containerNode != null) && (typeBuilder != null)) {
            final String nodeName = containerNode.getQName().getLocalName();

            if (nodeName != null && !containerNode.isAddedByUses()) {
                final String packageName = packageNameForGeneratedType(basePackageName, containerNode.getPath());

                final GeneratedTypeBuilder rawGenType = addDefaultInterfaceDefinition(packageName, containerNode);
                constructGetter(typeBuilder, nodeName, containerNode.getDescription(), rawGenType);

                return true;
            }
        }
        return false;
    }

    private boolean resolveListSchemaNode(final String basePackageName, final GeneratedTypeBuilder typeBuilder,
            final ListSchemaNode schemaNode) {
        if ((schemaNode != null) && (typeBuilder != null)) {
            final String listName = schemaNode.getQName().getLocalName();

            if (listName != null && !schemaNode.isAddedByUses()) {
                final String packageName = packageNameForGeneratedType(basePackageName, schemaNode.getPath());
                final GeneratedTypeBuilder rawGenType = addDefaultInterfaceDefinition(packageName, schemaNode);
                constructGetter(typeBuilder, listName, schemaNode.getDescription(), Types.listTypeFor(rawGenType));
                return true;
            }
        }
        return false;
    }

    /**
     * Method instantiates new Generated Type Builder and sets the implements
     * definitions of Data Object and Augmentable.
     * 
     * @param packageName
     *            Generated Type Package Name
     * @param schemaNode
     *            Schema Node definition
     * @return Generated Type Builder instance for Schema Node definition
     */
    private GeneratedTypeBuilder addDefaultInterfaceDefinition(final String packageName, final SchemaNode schemaNode) {
        final GeneratedTypeBuilder builder = addRawInterfaceDefinition(packageName, schemaNode, "");
        builder.addImplementsType(Types.DATA_OBJECT);
        if (!(schemaNode instanceof GroupingDefinition)) {
            builder.addImplementsType(Types.augmentableTypeFor(builder));
        }

        if (schemaNode instanceof DataNodeContainer) {
            addImplementedInterfaceFromUses((DataNodeContainer) schemaNode, builder);
        }

        return builder;
    }

    /**
     * 
     * @param packageName
     * @param schemaNode
     * @return
     */
    private GeneratedTypeBuilder addRawInterfaceDefinition(final String packageName, final SchemaNode schemaNode) {
        return addRawInterfaceDefinition(packageName, schemaNode, "");
    }

    private GeneratedTypeBuilder addRawInterfaceDefinition(final String packageName, final SchemaNode schemaNode,
            final String prefix) {
        if (schemaNode == null) {
            throw new IllegalArgumentException("Data Schema Node cannot be NULL!");
        }
        if (packageName == null) {
            throw new IllegalArgumentException("Package Name for Generated Type cannot be NULL!");
        }
        if (schemaNode.getQName() == null) {
            throw new IllegalArgumentException("QName for Data Schema Node cannot be NULL!");
        }
        final String schemaNodeName = schemaNode.getQName().getLocalName();
        if (schemaNodeName == null) {
            throw new IllegalArgumentException("Local Name of QName for Data Schema Node cannot be NULL!");
        }

        final String genTypeName;
        if (prefix == null) {
            genTypeName = parseToClassName(schemaNodeName);
        } else {
            genTypeName = prefix + parseToClassName(schemaNodeName);
        }

        final GeneratedTypeBuilder newType = new GeneratedTypeBuilderImpl(packageName, genTypeName);
        if (!genTypeBuilders.containsKey(packageName)) {
            final Map<String, GeneratedTypeBuilder> builders = new HashMap<>();
            builders.put(genTypeName, newType);
            genTypeBuilders.put(packageName, builders);
        } else {
            final Map<String, GeneratedTypeBuilder> builders = genTypeBuilders.get(packageName);
            if (!builders.containsKey(genTypeName)) {
                builders.put(genTypeName, newType);
            }
        }
        return newType;
    }

    private String getterMethodName(final String methodName) {
        final StringBuilder method = new StringBuilder();
        method.append("get");
        method.append(parseToClassName(methodName));
        return method.toString();
    }

    private String setterMethodName(final String methodName) {
        final StringBuilder method = new StringBuilder();
        method.append("set");
        method.append(parseToClassName(methodName));
        return method.toString();
    }

    private MethodSignatureBuilder constructGetter(final GeneratedTypeBuilder interfaceBuilder,
            final String schemaNodeName, final String comment, final Type returnType) {
        final MethodSignatureBuilder getMethod = interfaceBuilder.addMethod(getterMethodName(schemaNodeName));

        getMethod.setComment(comment);
        getMethod.setReturnType(returnType);

        return getMethod;
    }

    private MethodSignatureBuilder constructSetter(final GeneratedTypeBuilder interfaceBuilder,
            final String schemaNodeName, final String comment, final Type parameterType) {
        final MethodSignatureBuilder setMethod = interfaceBuilder.addMethod(setterMethodName(schemaNodeName));

        setMethod.setComment(comment);
        setMethod.addParameter(parameterType, parseToValidParamName(schemaNodeName));
        setMethod.setReturnType(Types.voidType());

        return setMethod;
    }

    private List<Type> listToGenType(final String basePackageName, final ListSchemaNode list) {
        if (basePackageName == null) {
            throw new IllegalArgumentException("Package Name for Generated Type cannot be NULL!");
        }
        if (list == null) {
            throw new IllegalArgumentException("List Schema Node cannot be NULL!");
        }

        final String packageName = packageNameForGeneratedType(basePackageName, list.getPath());
        final GeneratedTypeBuilder typeBuilder = resolveListTypeBuilder(packageName, list);
        final List<String> listKeys = listKeys(list);
        GeneratedTOBuilder genTOBuilder = resolveListKeyTOBuilder(packageName, list, listKeys);
        
        
        if(genTOBuilder != null) {
            ParameterizedType identifierMarker = Types.parameterizedTypeFor( Types.typeForClass(Identifier.class), typeBuilder);
            ParameterizedType identifiableMarker = Types.parameterizedTypeFor(Types.typeForClass(Identifiable.class), genTOBuilder);
            genTOBuilder.addImplementsType(identifierMarker);
            typeBuilder.addImplementsType(identifiableMarker);
        }
        final Set<DataSchemaNode> schemaNodes = list.getChildNodes();

        for (final DataSchemaNode schemaNode : schemaNodes) {
            if (schemaNode.isAugmenting()) {
                continue;
            }
            addSchemaNodeToListBuilders(basePackageName, schemaNode, typeBuilder, genTOBuilder, listKeys);
        }
        return typeBuildersToGenTypes(typeBuilder, genTOBuilder);
    }

    private void addSchemaNodeToListBuilders(final String basePackageName, final DataSchemaNode schemaNode,
            final GeneratedTypeBuilder typeBuilder, final GeneratedTOBuilder genTOBuilder, final List<String> listKeys) {
        if (schemaNode == null) {
            throw new IllegalArgumentException("Data Schema Node cannot be NULL!");
        }

        if (typeBuilder == null) {
            throw new IllegalArgumentException("Generated Type Builder cannot be NULL!");
        }

        if (schemaNode instanceof LeafSchemaNode) {
            final LeafSchemaNode leaf = (LeafSchemaNode) schemaNode;
            if (!isPartOfListKey(leaf, listKeys)) {
                resolveLeafSchemaNodeAsMethod(typeBuilder, leaf);
            } else {
                resolveLeafSchemaNodeAsProperty(genTOBuilder, leaf, true);
            }
        } else if (schemaNode instanceof LeafListSchemaNode) {
            resolveLeafListSchemaNode(typeBuilder, (LeafListSchemaNode) schemaNode);
        } else if (schemaNode instanceof ContainerSchemaNode) {
            resolveContainerSchemaNode(basePackageName, typeBuilder, (ContainerSchemaNode) schemaNode);
        } else if (schemaNode instanceof ListSchemaNode) {
            resolveListSchemaNode(basePackageName, typeBuilder, (ListSchemaNode) schemaNode);
        }
    }

    private List<Type> typeBuildersToGenTypes(final GeneratedTypeBuilder typeBuilder, GeneratedTOBuilder genTOBuilder) {
        final List<Type> genTypes = new ArrayList<>();
        if (typeBuilder == null) {
            throw new IllegalArgumentException("Generated Type Builder cannot be NULL!");
        }

        if (genTOBuilder != null) {
            final GeneratedTransferObject genTO = genTOBuilder.toInstance();
            constructGetter(typeBuilder, "key", "Returns Primary Key of Yang List Type", genTO);
            genTypes.add(genTO);
        }
        genTypes.add(typeBuilder.toInstance());
        return genTypes;
    }

    /**
     * @param list
     * @return
     */
    private GeneratedTOBuilder resolveListKey(final String packageName, final ListSchemaNode list) {
        final String listName = list.getQName().getLocalName() + "Key";
        return schemaNodeToTransferObjectBuilder(packageName, list, listName);
    }

    private boolean isPartOfListKey(final LeafSchemaNode leaf, final List<String> keys) {
        if ((leaf != null) && (keys != null) && (leaf.getQName() != null)) {
            final String leafName = leaf.getQName().getLocalName();
            if (keys.contains(leafName)) {
                return true;
            }
        }
        return false;
    }

    private List<String> listKeys(final ListSchemaNode list) {
        final List<String> listKeys = new ArrayList<>();

        if (list.getKeyDefinition() != null) {
            final List<QName> keyDefinitions = list.getKeyDefinition();

            for (final QName keyDefinition : keyDefinitions) {
                listKeys.add(keyDefinition.getLocalName());
            }
        }
        return listKeys;
    }

    private GeneratedTypeBuilder resolveListTypeBuilder(final String packageName, final ListSchemaNode list) {
        if (packageName == null) {
            throw new IllegalArgumentException("Package Name for Generated Type cannot be NULL!");
        }
        if (list == null) {
            throw new IllegalArgumentException("List Schema Node cannot be NULL!");
        }

        final String schemaNodeName = list.getQName().getLocalName();
        final String genTypeName = parseToClassName(schemaNodeName);

        GeneratedTypeBuilder typeBuilder = null;
        final Map<String, GeneratedTypeBuilder> builders = genTypeBuilders.get(packageName);
        if (builders != null) {
            typeBuilder = builders.get(genTypeName);
        }
        if (typeBuilder == null) {
            typeBuilder = addDefaultInterfaceDefinition(packageName, list);
        }
        return typeBuilder;
    }

    private GeneratedTOBuilder resolveListKeyTOBuilder(final String packageName, final ListSchemaNode list,
            final List<String> listKeys) {
        GeneratedTOBuilder genTOBuilder = null;
        if (listKeys.size() > 0) {
            genTOBuilder = resolveListKey(packageName, list);
        }
        return genTOBuilder;
    }

    private GeneratedTOBuilder addEnclosedTOToTypeBuilder(TypeDefinition<?> typeDef, GeneratedTypeBuilder typeBuilder,
            String leafName) {
        String className = parseToClassName(leafName);
        GeneratedTOBuilder genTOBuilder = null;
        if (typeDef instanceof UnionType) {
            genTOBuilder = ((TypeProviderImpl) typeProvider).addUnionGeneratedTypeDefinition(
                    typeBuilder.getFullyQualifiedName(), typeDef, className);
        } else if (typeDef instanceof BitsTypeDefinition) {
            genTOBuilder = ((TypeProviderImpl) typeProvider).bitsTypedefToTransferObject(
                    typeBuilder.getFullyQualifiedName(), typeDef, className);
        }
        if (genTOBuilder != null) {
            typeBuilder.addEnclosingTransferObject(genTOBuilder);
            return genTOBuilder;
        }
        return null;

    }

    /**
     * Adds the implemented types to type builder. The method passes through the
     * list of elements which contains {@code dataNodeContainer} and adds them
     * as <i>implements type</i> to <code>builder</code>
     * 
     * @param dataNodeContainer
     *            element which contains the list of used YANG groupings
     * @param builder
     *            builder to which are added implemented types according to
     *            <code>dataNodeContainer</code>
     * @return generated type builder which contains implemented types
     */
    private GeneratedTypeBuilder addImplementedInterfaceFromUses(final DataNodeContainer dataNodeContainer,
            final GeneratedTypeBuilder builder) {
        for (UsesNode usesNode : dataNodeContainer.getUses()) {
            if (usesNode.getGroupingPath() != null) {
                GeneratedType genType = allGroupings.get(usesNode.getGroupingPath());
                if (genType == null) {
                    throw new IllegalStateException("Grouping " + usesNode.getGroupingPath() + "is not resolved for "
                            + builder.getName());
                }
                builder.addImplementsType(genType);
            }
        }
        return builder;
    }

}
