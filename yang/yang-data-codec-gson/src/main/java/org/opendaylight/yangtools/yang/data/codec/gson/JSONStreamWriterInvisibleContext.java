/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.yang.data.codec.gson;

import com.google.common.base.Preconditions;

import java.io.Writer;

/**
 * A virtual recursion level in {@link JSONNormalizedNodeStreamWriter}, used for nodes
 * which are not emitted in the JSON representation.
 */
final class JSONStreamWriterInvisibleContext extends JSONStreamWriterURIContext {
    JSONStreamWriterInvisibleContext(final JSONStreamWriterContext parent) {
        super(Preconditions.checkNotNull(parent), parent.getNamespace());
    }

    @Override
    protected void emitEnd(final Writer writer) {
        // No-op
    }
}