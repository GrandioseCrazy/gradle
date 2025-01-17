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

import com.google.common.reflect.TypeToken;
import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.api.services.BuildServiceRegistration;
import org.gradle.api.services.BuildServiceSpec;
import org.gradle.internal.Cast;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.reflect.ParameterizedType;

public class DefaultBuildServicesRegistry implements BuildServiceRegistryInternal {
    private final NamedDomainObjectSet<BuildServiceRegistration<?, ?>> registrations;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry services;
    private final ListenerManager listenerManager;

    public DefaultBuildServicesRegistry(DomainObjectCollectionFactory factory, InstantiatorFactory instantiatorFactory, ServiceRegistry services, ListenerManager listenerManager) {
        this.registrations = Cast.uncheckedCast(factory.newNamedDomainObjectSet(BuildServiceRegistration.class));
        this.instantiatorFactory = instantiatorFactory;
        this.services = services;
        this.listenerManager = listenerManager;
    }

    @Override
    public NamedDomainObjectSet<BuildServiceRegistration<?, ?>> getRegistrations() {
        return registrations;
    }

    @Override
    public <T extends BuildService<P>, P extends BuildServiceParameters> Provider<T> maybeRegister(String name, Class<T> implementationType, Action<? super BuildServiceSpec<P>> configureAction) {
        BuildServiceRegistration<?, ?> existing = registrations.findByName(name);
        if (existing != null) {
            // TODO - assert same type
            // TODO - assert same parameters
            return Cast.uncheckedCast(existing.getService());
        }

        // TODO - extract some shared infrastructure for this
        Class<P> parameterType = parameterTypeOf(implementationType);
        P parameters = instantiatorFactory.decorateScheme().withServices(services).instantiator().newInstance(parameterType);
        configureAction.execute(new BuildServiceSpec<P>() {
            @Override
            public P getParameters() {
                return parameters;
            }

            @Override
            public void parameters(Action<? super P> configureAction) {
                configureAction.execute(parameters);
            }
        });
        // TODO - Add BuildServiceParameters.NONE marker and skip some work when using this
        // TODO - isolate parameters
        // TODO - defer isolation of parameters until execution time
        // TODO - finalize the parameters during isolation
        return doRegister(name, implementationType, parameterType, parameters);
    }

    @Override
    public BuildServiceProvider<?, ?> register(String name, Class<? extends BuildService> implementationType, BuildServiceParameters parameters) {
        if (registrations.findByName(name) != null) {
            throw new IllegalArgumentException("Service '%s' has already been registered.");
        }
        return doRegister(name, implementationType, parameterTypeOf(implementationType), parameters);
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> BuildServiceProvider<T, P> doRegister(String name, Class<T> implementationType, Class<P> parameterType, P parameters) {
        BuildServiceProvider<T, P> provider = new BuildServiceProvider<>(name, implementationType, parameterType, parameters, instantiatorFactory.injectScheme());

        registrations.add(new BuildServiceRegistration<T, P>() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public P getParameters() {
                return parameters;
            }

            @Override
            public Provider<T> getService() {
                return provider;
            }
        });

        // TODO - should stop the service after last usage (ie after the last task that uses it) instead of at the end of the build
        // TODO - should reuse service across build invocations, until the parameters change
        listenerManager.addListener(new BuildAdapter() {
            @Override
            public void buildFinished(BuildResult result) {
                provider.maybeStop();
            }
        });
        return provider;
    }

    private <T extends BuildService<P>, P extends BuildServiceParameters> Class<P> parameterTypeOf(Class<T> implementationType) {
        ParameterizedType superType = (ParameterizedType) TypeToken.of(implementationType).getSupertype(BuildService.class).getType();
        return Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[0]).getRawType());
    }
}
