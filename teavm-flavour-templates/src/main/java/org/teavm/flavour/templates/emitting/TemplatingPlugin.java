/*
 *  Copyright 2015 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.flavour.templates.emitting;

import org.teavm.flavour.templates.Fragment;
import org.teavm.flavour.templates.Templates;
import org.teavm.model.MethodReference;
import org.teavm.vm.spi.TeaVMHost;
import org.teavm.vm.spi.TeaVMPlugin;

/**
 *
 * @author Alexey Andreev
 */
public class TemplatingPlugin implements TeaVMPlugin {
    @Override
    public void install(TeaVMHost host) {
        TemplatingDependencyListener dependencyListener = new TemplatingDependencyListener();
        host.add(dependencyListener);
        host.add(new MethodReference(Templates.class, "createImpl", Object.class, String.class, Fragment.class),
                new TemplatingRenderer(dependencyListener.templateMapping));
    }
}
