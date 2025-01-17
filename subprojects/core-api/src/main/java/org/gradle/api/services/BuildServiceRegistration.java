/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.services;

import org.gradle.api.Incubating;
import org.gradle.api.Named;
import org.gradle.api.provider.Provider;

/**
 * Details of the registration of a build service.
 *
 * @param <T> the service type.
 * @param <P> the service parameters type.
 * @since 6.1
 */
@Incubating
public interface BuildServiceRegistration<T extends BuildService<P>, P extends BuildServiceParameters> extends Named {
    /**
     * Returns the parameters to use to instantiate the service with.
     */
    P getParameters();

    /**
     * Returns a {@link Provider} that will create the service instance when its value is queried.
     */
    Provider<T> getService();
}
