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

package org.gradle.api.services.internal

import org.gradle.BuildListener
import org.gradle.BuildResult
import org.gradle.api.GradleException
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultBuildServicesRegistryTest extends Specification {
    def listenerManager = new DefaultListenerManager()
    def registry = new DefaultBuildServicesRegistry(TestUtil.domainObjectCollectionFactory(), TestUtil.instantiatorFactory(), TestUtil.services(), listenerManager)

    def setup() {
        ServiceImpl.reset()
    }

    def "can lazily create service instance"() {
        when:
        def provider = registry.maybeRegister("service", ServiceImpl) {}

        then:
        ServiceImpl.instances.empty

        when:
        def service = provider.get()

        then:
        service instanceof ServiceImpl
        ServiceImpl.instances == [service]

        when:
        def service2 = provider.get()

        then:
        service2.is(service)
        ServiceImpl.instances == [service]
    }

    def "service provider always has value present"() {
        when:
        def provider = registry.maybeRegister("service", ServiceImpl) {}

        then:
        provider.present
        ServiceImpl.instances.empty
    }

    def "wraps and memoizes service instantiation failure"() {
        when:
        def provider = registry.maybeRegister("service", BrokenServiceImpl) {}

        then:
        noExceptionThrown()

        when:
        provider.get()

        then:
        def e = thrown(RuntimeException)
        e.message == "Failed to create service 'service'."
        e.cause.cause.is(BrokenServiceImpl.failure)
        BrokenServiceImpl.attempts == 1

        when:
        provider.get()

        then:
        def e2 = thrown(RuntimeException)
        e2.is(e)
        BrokenServiceImpl.attempts == 1
    }

    def "can locate registration by name"() {
        when:
        def provider = registry.maybeRegister("service", ServiceImpl) {}
        def registration = registry.registrations.getByName("service")

        then:
        registration.service.is(provider)
        ServiceImpl.instances.empty
    }

    def "reuses registration with same name"() {
        when:
        def provider1 = registry.maybeRegister("service", ServiceImpl) {}
        def provider2 = registry.maybeRegister("service", ServiceImpl) {}

        then:
        provider1.is(provider2)
    }

    def "can provide parameters to the service"() {
        when:
        def provider = registry.maybeRegister("service", ServiceImpl) {
            it.parameters.prop = "value"
        }
        def service = provider.get()

        then:
        service.prop == "value"
    }

    def "can tweak parameters via the registration"() {
        when:
        def provider = registry.maybeRegister("service", ServiceImpl) {
            it.parameters.prop = "value 1"
        }
        def parameters = registry.registrations.getByName("service").parameters

        then:
        parameters.prop == "value 1"

        when:
        parameters.prop = "value 2"
        def service = provider.get()

        then:
        service.prop == "value 2"
    }

    def "stops service at end of build if it implements AutoCloseable"() {
        def provider1 = registry.maybeRegister("one", ServiceImpl) {}
        def provider2 = registry.maybeRegister("two", StoppableServiceImpl) {}
        def provider3 = registry.maybeRegister("three", StoppableServiceImpl) {}

        when:
        def notStoppable = provider1.get()
        def stoppable1 = provider2.get()
        def stoppable2 = provider3.get()

        then:
        ServiceImpl.instances == [notStoppable, stoppable1, stoppable2]

        when:
        buildFinished()

        then:
        ServiceImpl.instances == [notStoppable]
    }

    def "does not attempt to stop an unused service at the end of build"() {
        registry.maybeRegister("service", ServiceImpl) {}

        when:
        buildFinished()

        then:
        ServiceImpl.instances.empty
    }

    def "reports failure to stop service"() {
        def provider = registry.maybeRegister("service", BrokenStopServiceImpl) {}
        provider.get()

        when:
        buildFinished()

        then:
        def e = thrown(GradleException)
        e.message == "Failed to stop service 'service'."
        e.cause.is(BrokenStopServiceImpl.failure)
    }

    private buildFinished() {
        listenerManager.getBroadcaster(BuildListener).buildFinished(Stub(BuildResult))
    }

    interface Params extends BuildServiceParameters {
        String getProp()

        void setProp(String value)
    }

    static abstract class ServiceImpl implements BuildService<Params> {
        static List<ServiceImpl> instances = []

        String getProp() {
            return getParameters().prop
        }

        static void reset() {
            instances.clear()
        }

        ServiceImpl() {
            instances.add(this)
        }
    }

    static abstract class StoppableServiceImpl extends ServiceImpl implements AutoCloseable {
        @Override
        void close() {
            instances.remove(this)
        }
    }

    static abstract class BrokenServiceImpl implements BuildService<Params> {
        static int attempts = 0
        static RuntimeException failure = new RuntimeException("broken")

        BrokenServiceImpl() {
            attempts++
            throw failure
        }
    }

    static abstract class BrokenStopServiceImpl implements BuildService<Params>, AutoCloseable {
        static int attempts = 0
        static RuntimeException failure = new RuntimeException("broken")

        @Override
        void close() {
            attempts++
            throw failure
        }
    }
}
