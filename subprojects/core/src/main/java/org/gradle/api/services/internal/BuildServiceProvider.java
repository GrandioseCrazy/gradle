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

package org.gradle.api.services.internal;

import org.gradle.api.internal.provider.AbstractReadOnlyProvider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.Try;
import org.gradle.internal.instantiation.InstantiationScheme;
import org.gradle.internal.service.DefaultServiceRegistry;

import javax.annotation.Nullable;

// TODO - complain when used at configuration time, except when opted in to this
public class BuildServiceProvider<T extends BuildService<P>, P extends BuildServiceParameters> extends AbstractReadOnlyProvider<T> {
    private final String name;
    private final Class<T> implementationType;
    private final InstantiationScheme instantiationScheme;
    private final Class<P> parametersType;
    private final P parameters;
    private Try<T> instance;

    public BuildServiceProvider(String name, Class<T> implementationType, Class<P> parametersType, P parameters, InstantiationScheme instantiationScheme) {
        this.name = name;
        this.implementationType = implementationType;
        this.parametersType = parametersType;
        this.parameters = parameters;
        this.instantiationScheme = instantiationScheme;
    }

    public String getName() {
        return name;
    }

    public Class<T> getImplementationType() {
        return implementationType;
    }

    public P getParameters() {
        return parameters;
    }

    @Nullable
    @Override
    public Class<T> getType() {
        return implementationType;
    }

    @Override
    public boolean isPresent() {
        return true;
    }

    // TODO - rename this method
    @Override
    public boolean isValueProducedByTask() {
        return true;
    }

    @Nullable
    @Override
    public T getOrNull() {
        synchronized (this) {
            if (instance == null) {
                // TODO - extract a ServiceLookup implementation to reuse
                DefaultServiceRegistry services = new DefaultServiceRegistry();
                services.add(parametersType, parameters);
                try {
                    instance = Try.successful(instantiationScheme.withServices(services).instantiator().newInstance(implementationType));
                } catch (Exception e) {
                    instance = Try.failure(new ServiceLifecycleException("Failed to create service '" + name + "'.", e));
                }
            }
            return instance.get();
        }
    }

    public void maybeStop() {
        synchronized (this) {
            if (instance != null) {
                instance.ifSuccessful(t -> {
                    if (t instanceof AutoCloseable) {
                        try {
                            ((AutoCloseable) t).close();
                        } catch (Exception e) {
                            throw new ServiceLifecycleException("Failed to stop service '" + name + "'.", e);
                        }
                    }
                });
            }
        }
    }
}
