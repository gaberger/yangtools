/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.yangtools.restconf.client.api.event;

import java.util.Date;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;

public interface EventStreamContext extends AutoCloseable {
	ListenableFuture<Optional<EventStreamReplay>> getReplay(Optional<Date> startTime, Optional<Date> endTime);

	@Override
	void close();
}
