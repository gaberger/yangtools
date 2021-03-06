/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.impl.schema.transform.base.serializer;

import org.opendaylight.yangtools.yang.data.api.schema.MapEntryNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.FromNormalizedNodeSerializer;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

/**
 * Abstract(base) serializer for MapNodes, serializes elements of type E.
 *
 * @param <E> type of serialized elements
 */
public abstract class MapNodeBaseSerializer<E> implements FromNormalizedNodeSerializer<E, MapNode, ListSchemaNode> {

    @Override
    public final Iterable<E> serialize(final ListSchemaNode schema, final MapNode node) {
        return Iterables.concat(Iterables.transform(node.getValue(), new Function<MapEntryNode, Iterable<E>>() {
            @Override
            public Iterable<E> apply(MapEntryNode input) {
                final Iterable<E> serializedChild = getMapEntryNodeDomSerializer().serialize(schema, input);
                final int size = Iterables.size(serializedChild);

                Preconditions.checkState(size == 1,
                        "Unexpected count of entries  for list serialized from: %s, should be 1, was: %s",
                        input, size);
                return serializedChild;
            }
        }));
    }

    /**
     *
     * @return serializer for inner MapEntryNodes used to serialize every entry of MapNode, might be the same instance in case its immutable
     */
    protected abstract FromNormalizedNodeSerializer<E, MapEntryNode, ListSchemaNode> getMapEntryNodeDomSerializer();
}
