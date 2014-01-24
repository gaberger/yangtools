package org.opendaylight.yangtools.yang.data.impl.codec;

import org.opendaylight.yangtools.yang.binding.Augmentation;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.CompositeNode;
import org.opendaylight.yangtools.yang.data.api.Node;

public interface AugmentationCodec<A extends Augmentation<?>> extends DomCodec<A> {


    @Override
    public CompositeNode serialize(ValueWithQName<A> input);

    @Override
    public ValueWithQName<A> deserialize(Node<?> input);

    public QName getAugmentationQName();
}
