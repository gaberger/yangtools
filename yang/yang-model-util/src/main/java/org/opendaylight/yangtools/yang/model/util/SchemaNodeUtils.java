/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.model.util;

import org.opendaylight.yangtools.yang.model.api.DerivableSchemaNode;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;

import com.google.common.base.Optional;

public class SchemaNodeUtils {

    private SchemaNodeUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final Optional<SchemaNode> getOriginalIfPossible(final SchemaNode node) {
        if(node instanceof DerivableSchemaNode) {
            @SuppressWarnings("unchecked")
            final Optional<SchemaNode> ret  = (Optional<SchemaNode>) (((DerivableSchemaNode) node).getOriginal());
            return ret;
        }
        return Optional.absent();
    }

    public static final  SchemaNode getRootOriginalIfPossible(final SchemaNode data) {
        Optional<SchemaNode> previous = Optional.absent();
        Optional<SchemaNode> next = getOriginalIfPossible(data);
        while(next.isPresent()) {
            previous = next;
            next = getOriginalIfPossible(next.get());
        }
        return previous.orNull();
    }
}
