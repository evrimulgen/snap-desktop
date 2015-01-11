/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.esa.snap.gui.nodes;

import org.openide.nodes.ChildFactory;

/**
 * A group object serves as a key for {@link org.esa.snap.gui.nodes.PNGroupNode}s and is a child factory for nodes
 * representing {@link org.esa.beam.framework.datamodel.ProductNode}s.
 *
 * @author Norman
 */
abstract class PNGroupBase<T> extends ChildFactory.Detachable<T> {

    void refresh() {
        refresh(true);
    }
}
