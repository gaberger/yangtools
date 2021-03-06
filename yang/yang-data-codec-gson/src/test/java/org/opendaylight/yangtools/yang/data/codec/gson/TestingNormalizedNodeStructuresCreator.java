/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.codec.gson;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.CompositeNode;
import org.opendaylight.yangtools.yang.data.api.SimpleNode;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.AugmentationIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifierWithPredicates;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeWithValue;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.AugmentationNode;
import org.opendaylight.yangtools.yang.data.api.schema.ChoiceNode;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.DataContainerChild;
import org.opendaylight.yangtools.yang.data.api.schema.LeafNode;
import org.opendaylight.yangtools.yang.data.api.schema.LeafSetEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.UnkeyedListNode;
import org.opendaylight.yangtools.yang.data.impl.ImmutableCompositeNode;
import org.opendaylight.yangtools.yang.data.impl.NodeFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.Builders;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.CollectionNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeAttrBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.DataContainerNodeBuilder;
import org.opendaylight.yangtools.yang.data.impl.schema.builder.api.ListNodeBuilder;

import java.util.HashMap;
import java.util.Map;

public class TestingNormalizedNodeStructuresCreator {

    static NormalizedNode<?, ?> cont1Node(
            DataContainerChild<? extends YangInstanceIdentifier.PathArgument, ?>... children) {
        DataContainerNodeAttrBuilder<NodeIdentifier, ContainerNode> cont1 = Builders.containerBuilder();
        cont1.withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "cont1")));

        cont1.withValue(Lists.newArrayList(children));
        return cont1.build();
    }

    private static DataContainerChild<? extends PathArgument, ?> lst12Node() {
        CollectionNodeBuilder<UnkeyedListEntryNode, UnkeyedListNode> lst12Builder = Builders.unkeyedListBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lst12")));
        lst12Builder.withChild(lst12Entry1Node());
        return lst12Builder.build();
    }

    private static UnkeyedListEntryNode lst12Entry1Node() {
        DataContainerNodeAttrBuilder<NodeIdentifier, UnkeyedListEntryNode> lst12Entry1Builder = Builders
                .unkeyedListEntryBuilder();
        lst12Entry1Builder
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lst12")));
        lst12Entry1Builder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf121")))
                .withValue("lf121 value").build());
        return lst12Entry1Builder.build();
    }

    private static DataContainerChild<? extends PathArgument, ?> choc12Node() {
        DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> choc12Builder = Builders.choiceBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "choc12")));

//        DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> choc12Augmentation = Builders
//                .augmentationBuilder();
//        choc12Augmentation.withNodeIdentifier(new AugmentationIdentifier(Sets.newHashSet(QName.create(
//                "ns:complex:json", "2014-08-11", "c12B"))));
//        choc12Augmentation.withChild(lf17Node());

        choc12Builder.withChild(lf17Node());
        return choc12Builder.build();
    }

    protected static LeafNode<Object> lf17Node() {
        return Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf17")))
                .withValue("lf17 value").build();
    }

    private static DataContainerChild<? extends PathArgument, ?> externalAugmentC11AWithLf15_11AndLf15_12Node() {
        DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentationBuilder = Builders
                .augmentationBuilder();
        augmentationBuilder.withNodeIdentifier(new AugmentationIdentifier(Sets.newHashSet(
                QName.create("ns:complex:json:augmentation", "2014-08-14", "lf15_11"),
                QName.create("ns:complex:json:augmentation", "2014-08-14", "lf15_12"))));
        augmentationBuilder.withChild(lf15_11NodeExternal());
        augmentationBuilder.withChild(lf15_12NodeExternal());
        return augmentationBuilder.build();
    }

    private static LeafNode<Object> lf15_12NodeExternal() {
        return Builders
                .leafBuilder()
                .withNodeIdentifier(
                        new NodeIdentifier(QName.create("ns:complex:json:augmentation", "2014-08-14", "lf15_12")))
                .withValue("lf15_12 value from augmentation").build();
    }

    private static LeafNode<Object> lf15_11NodeExternal() {
        return Builders
                .leafBuilder()
                .withNodeIdentifier(
                        new NodeIdentifier(QName.create("ns:complex:json:augmentation", "2014-08-14", "lf15_11")))
                .withValue("lf15_11 value from augmentation").build();
    }

    private static DataContainerChild<? extends PathArgument, ?> choc11Node(
            DataContainerChild<? extends PathArgument, ?>... children) {
        DataContainerNodeBuilder<NodeIdentifier, ChoiceNode> choc11Builder = Builders.choiceBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "choc11")));
        choc11Builder.withValue(Lists.newArrayList(children));
        // choc11Builder.addChild(lf13Node());
        // choc11Builder.addChild(augmentChoc11_c11A_lf1511AndLf1512Children());
        // choc11Builder.addChild(augmentChoc11_c11_lf1521Children());
        return choc11Builder.build();
    }

    private static LeafNode<Object> lf13Node() {
        return Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf13")))
                .withValue("lf13 value").build();
    }

    private static DataContainerChild<? extends PathArgument, ?> augmentC11AWithLf15_21Node() {
        DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> choc11_c11AugmentBuilder = Builders
                .augmentationBuilder();
        choc11_c11AugmentBuilder.withNodeIdentifier(new AugmentationIdentifier(Sets.newHashSet(QName.create(
                "ns:complex:json", "2014-08-11", "lf15_21"))));

        choc11_c11AugmentBuilder.withChild(lf15_21Node());
        return choc11_c11AugmentBuilder.build();
    }

    private static LeafNode<Object> lf15_21Node() {
        return Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf15_21")))
                .withValue("lf15_21 value").build();
    }

    private static DataContainerChild<? extends PathArgument, ?> augmentC11AWithLf15_11AndLf15_12Node() {
        DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> choc11_c11AugmentBuilder = Builders
                .augmentationBuilder();
        choc11_c11AugmentBuilder.withNodeIdentifier(new AugmentationIdentifier(Sets.newHashSet(
                QName.create("ns:complex:json", "2014-08-11", "lf15_11"),
                QName.create("ns:complex:json", "2014-08-11", "lf15_12"))));
        choc11_c11AugmentBuilder.withChild(lf15_11Node());
        choc11_c11AugmentBuilder.withChild(lf15_12Node());
        return choc11_c11AugmentBuilder.build();
    }

    private static LeafNode<Object> lf15_12Node() {
        return Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf15_12")))
                .withValue(QName.create("ns:complex:json", "2014-08-11", "lf11")).build();
    }

    private static LeafNode<Object> lf15_11Node() {
        return Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf15_11")))
                .withValue(Sets.newHashSet("one", "two")).build();
    }

    private static DataContainerChild<? extends PathArgument, ?> lf12_1Node() {
        DataContainerNodeBuilder<AugmentationIdentifier, AugmentationNode> augmentBuilder = Builders
                .augmentationBuilder().withNodeIdentifier(
                        new AugmentationIdentifier(Sets.newHashSet(
                                QName.create("ns:complex:json", "2014-08-11", "lf12_1"),
                                QName.create("ns:complex:json", "2014-08-11", "lf12_2"))));
        augmentBuilder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf12_1")))
                .withValue("lf12 value").build());
        return augmentBuilder.build();
    }

    private static DataContainerChild<? extends PathArgument, ?> childLst11() {
        CollectionNodeBuilder<MapEntryNode, MapNode> lst11 = Builders.mapBuilder().withNodeIdentifier(
                new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lst11")));

        DataContainerNodeAttrBuilder<NodeIdentifierWithPredicates, MapEntryNode> lst11Entry1Builder = Builders
                .mapEntryBuilder();

        Map<QName, Object> key = new HashMap<>();
        key.put(QName.create("ns:complex:json", "2014-08-11", "key111"), "key111 value");
        key.put(QName.create("ns:complex:json", "2014-08-11", "lf111"), "lf111 value");

        lst11Entry1Builder.withNodeIdentifier(new NodeIdentifierWithPredicates(QName.create("ns:complex:json",
                "2014-08-11", "lst11"), key));
        lst11Entry1Builder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "key111")))
                .withValue("key111 value").build());
        lst11Entry1Builder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf112")))
                .withValue(lf112Value()).build());
        lst11Entry1Builder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf113")))
                .withValue("lf113 value").build());
        lst11Entry1Builder.withChild(Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf111")))
                .withValue("lf111 value").build());
        lst11.withChild(lst11Entry1Builder.build());
        return lst11.build();
    }

    private static Object lf112Value() {
        InstanceIdentifierBuilder builder = YangInstanceIdentifier.builder();
        builder.node(QName.create("ns:complex:json", "2014-08-11", "cont1"));
        builder.node(QName.create("ns:complex:json", "2014-08-11", "lflst11"));
        return builder.build();
    }

    private static DataContainerChild<? extends PathArgument, ?> childLflst11() {
        ListNodeBuilder<Object, LeafSetEntryNode<Object>> lflst11 = Builders.leafSetBuilder().withNodeIdentifier(
                new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lflst11")));
        lflst11.withChild(Builders
                .leafSetEntryBuilder()
                .withNodeIdentifier(
                        new NodeWithValue(QName.create("ns:complex:json", "2014-08-11", "lflst11"), "lflst11 value1"))
                .withValue("lflst11 value1").build());
        lflst11.withChild(Builders
                .leafSetEntryBuilder()
                .withNodeIdentifier(
                        new NodeWithValue(QName.create("ns:complex:json", "2014-08-11", "lflst11"), "lflst11 value2"))
                .withValue("lflst11 value2").build());
        return lflst11.build();
    }

    private static DataContainerChild<? extends PathArgument, ?> childLflst11Multiline() {
        ListNodeBuilder<Object, LeafSetEntryNode<Object>> lflst11 = Builders.leafSetBuilder().withNodeIdentifier(
                new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lflst11")));
        lflst11.withChild(Builders
                .leafSetEntryBuilder()
                .withNodeIdentifier(
                        new NodeWithValue(QName.create("ns:complex:json", "2014-08-11", "lflst11"), "lflst11 value1\nanother line 1"))
                .withValue("lflst11 value1\nanother line 1").build());
        lflst11.withChild(Builders
                .leafSetEntryBuilder()
                .withNodeIdentifier(
                        new NodeWithValue(QName.create("ns:complex:json", "2014-08-11", "lflst11"), "lflst11 value2\r\nanother line 2"))
                .withValue("lflst11 value2\r\nanother line 2").build());
        return lflst11.build();
    }

    private static CompositeNode prepareLf12Value() {
        SimpleNode<?> anyxmlInData = NodeFactory.createImmutableSimpleNode(
                QName.create("ns:complex:json", "2014-08-11", "anyxml-in-data"), null, "foo");
        return ImmutableCompositeNode.builder().add(anyxmlInData)
                .setQName(QName.create("ns:complex:json", "2014-08-11", "lf12-any")).build();
    }

    private static CompositeNode prepareLf13Value() {
        SimpleNode<?> anyxmlInData = NodeFactory.createImmutableSimpleNode(
                QName.create("ns:complex:json", "2014-08-11", "anyxml-in-data"), null, "foo");
        return ImmutableCompositeNode.builder().add(anyxmlInData)
                .setQName(QName.create("ns:complex:json", "2014-08-11", "lf13-any")).build();
    }

    public static NormalizedNode<?, ?> leafNodeInContainer() {
        LeafNode<Object> lf11 = Builders.leafBuilder()
                .withNodeIdentifier(new NodeIdentifier(QName.create("ns:complex:json", "2014-08-11", "lf11")))
                .withValue((int) 453).build();
        return cont1Node(lf11);
    }

    public static NormalizedNode<?, ?> leafListNodeInContainer() {
        return cont1Node(childLflst11());
    }
    public static NormalizedNode<?, ?> leafListNodeInContainerMultiline() {
        return cont1Node(childLflst11Multiline());
    }

    public static NormalizedNode<?, ?> keyedListNodeInContainer() {
        return cont1Node(childLst11());
    }

    public static NormalizedNode<?, ?> leafNodeViaAugmentationInContainer() {
        return cont1Node(lf12_1Node());
    }

    public static NormalizedNode<?, ?> choiceNodeInContainer() {
        return cont1Node(choc11Node(lf13Node()));
    }

    /**
     * choc11 contains lf13, lf15_11 and lf15_12 are added via external augmentation
     *
     * @return
     */
    public static NormalizedNode<?, ?> caseNodeAugmentationInChoiceInContainer() {
        return cont1Node(choc11Node(lf13Node(), lf15_11Node(), lf15_12Node(), lf15_21Node()));
    }

    public static NormalizedNode<?, ?> caseNodeExternalAugmentationInChoiceInContainer() {
        return cont1Node(choc11Node(lf13Node(), lf15_11Node(), lf15_12Node(), lf15_11NodeExternal(), lf15_12NodeExternal()));
    }

    public static NormalizedNode<?, ?> choiceNodeAugmentationInContainer() {
        return cont1Node(choc12Node());
    }

    public static NormalizedNode<?, ?> unkeyedNodeInContainer() {
        return cont1Node(lst12Node());
    }

}
