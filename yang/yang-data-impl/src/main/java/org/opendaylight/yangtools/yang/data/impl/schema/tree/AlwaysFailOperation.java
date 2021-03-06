package org.opendaylight.yangtools.yang.data.impl.schema.tree;


import com.google.common.base.Optional;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.data.api.schema.tree.spi.TreeNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.spi.Version;

/**
 * An implementation of apply operation which fails to do anything,
 * consistently. An instance of this class is used by the data tree
 * if it does not have a SchemaContext attached and hence cannot
 * perform anything meaningful.
 */
final class AlwaysFailOperation implements ModificationApplyOperation {
    public static final ModificationApplyOperation INSTANCE = new AlwaysFailOperation();

    private AlwaysFailOperation() {

    }

    @Override
    public Optional<TreeNode> apply(final ModifiedNode modification,
            final Optional<TreeNode> storeMeta, final Version version) {
        throw new IllegalStateException("Schema Context is not available.");
    }

    @Override
    public void checkApplicable(final YangInstanceIdentifier path,final NodeModification modification, final Optional<TreeNode> storeMetadata) {
        throw new IllegalStateException("Schema Context is not available.");
    }

    @Override
    public Optional<ModificationApplyOperation> getChild(final PathArgument child) {
        throw new IllegalStateException("Schema Context is not available.");
    }

    @Override
    public void verifyStructure(final ModifiedNode modification) {
        throw new IllegalStateException("Schema Context is not available.");
    }
}