/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.spring.cloud.gateway.v412x;

import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.skywalking.apm.agent.core.context.ContextManager;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractSpan;
import org.apache.skywalking.apm.agent.core.context.trace.AbstractTracingSpan;
import org.apache.skywalking.apm.agent.core.context.trace.SpanLayer;
import org.apache.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.test.helper.SegmentHelper;
import org.apache.skywalking.apm.agent.test.tools.AgentServiceRule;
import org.apache.skywalking.apm.agent.test.tools.SegmentStorage;
import org.apache.skywalking.apm.agent.test.tools.SegmentStoragePoint;
import org.apache.skywalking.apm.agent.test.tools.SpanAssert;
import org.apache.skywalking.apm.agent.test.tools.TracingSegmentRunner;
import org.apache.skywalking.apm.network.trace.component.ComponentsDefine;
import org.apache.skywalking.apm.plugin.spring.cloud.gateway.v4x.define.EnhanceObjectCache;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.netty.Connection;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

import java.util.List;
import java.util.function.BiFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(TracingSegmentRunner.class)
public class HttpClientFinalizerV412InterceptorTest {

    private final static String URI = "http://localhost:8080/get";
    private final static String ENTRY_OPERATION_NAME = "/get";
    private final HttpClientFinalizerSendV412Interceptor sendInterceptor = new HttpClientFinalizerSendV412Interceptor();
    private final HttpClientFinalizerResponseConnectionV412Interceptor responseConnectionInterceptor = new HttpClientFinalizerResponseConnectionV412Interceptor();
    private final BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>> originalSendBiFunction = (httpClientRequest, nettyOutbound) -> (Publisher<Void>) s -> {
    };
    private final BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<Void>> originalResponseConnectionBiFunction = (httpClientResponse, connection) -> (Publisher<Void>) s -> {
    };
    private final EnhancedInstance enhancedInstance = new EnhancedInstance() {
        private EnhanceObjectCache enhanceObjectCache;

        @Override
        public Object getSkyWalkingDynamicField() {
            return enhanceObjectCache;
        }

        @Override
        public void setSkyWalkingDynamicField(Object value) {
            this.enhanceObjectCache = (EnhanceObjectCache) value;
        }
    };
    @Rule
    public AgentServiceRule serviceRule = new AgentServiceRule();
    @Rule
    public MockitoRule rule = MockitoJUnit.rule();

    private HttpClientResponse mockResponse;
    private HttpClientRequest mockRequest;
    @SegmentStoragePoint
    private SegmentStorage segmentStorage;
    private AbstractSpan entrySpan;

    @Before
    public void setUp() throws Exception {
        entrySpan = ContextManager.createEntrySpan(ENTRY_OPERATION_NAME, null);
        entrySpan.setLayer(SpanLayer.HTTP);
        entrySpan.setComponent(ComponentsDefine.SPRING_WEBFLUX);
        mockRequest = new MockClientRequest();
        mockResponse = new MockClientResponse();
        final EnhanceObjectCache enhanceObjectCache = new EnhanceObjectCache();
        enhanceObjectCache.setUrl(URI);
        enhanceObjectCache.setContextSnapshot(ContextManager.capture());
        enhancedInstance.setSkyWalkingDynamicField(enhanceObjectCache);
    }

    @Test
    public void testWithDynamicFieldNull() throws Throwable {
        enhancedInstance.setSkyWalkingDynamicField(null);
        executeSendRequest();
        stopEntrySpan();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(1, traceSegments.size());
    }

    @Test
    public void testWithContextSnapshotNull() throws Throwable {
        EnhanceObjectCache objectCache = (EnhanceObjectCache) enhancedInstance.getSkyWalkingDynamicField();
        objectCache.setContextSnapshot(null);
        executeSendRequest();
        stopEntrySpan();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(1, traceSegments.size());
    }

    @Test
    public void testWithEmptyUri() throws Throwable {
        final EnhanceObjectCache objectCache = (EnhanceObjectCache) enhancedInstance.getSkyWalkingDynamicField();
        objectCache.setUrl("");
        executeSendRequest();
        stopEntrySpan();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(1, traceSegments.size());
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        assertNotNull(spans);
        assertEquals(2, spans.size());
        assertNotNull(spans.get(1));
        assertUpstreamSpan(spans.get(1));
        assertNotNull(spans.get(0));
        assertSendSpan(spans.get(0));
        assertNotNull(objectCache.getSpan1());
        assertEquals(objectCache.getSpan1().getSpanId(), spans.get(0).getSpanId());
    }

    @Test
    public void testWithUri() throws Throwable {
        executeSendRequest();
        stopEntrySpan();
        final List<TraceSegment> traceSegments = segmentStorage.getTraceSegments();
        assertEquals(1, traceSegments.size());
        final EnhanceObjectCache objectCache = (EnhanceObjectCache) enhancedInstance
                .getSkyWalkingDynamicField();
        assertNotNull(objectCache.getSpan1());
        assertNotNull(objectCache.getSpan());
        assertTrue(objectCache.getSpan().isExit());
        assertEquals(objectCache.getSpan().getOperationName(), "SpringCloudGateway/sendRequest");
        assertEquals(objectCache.getSpan1().getOperationName(), "SpringCloudGateway/send");
        final List<AbstractTracingSpan> spans = SegmentHelper.getSpans(traceSegments.get(0));
        assertNotNull(spans);
        assertEquals(3, spans.size());
        assertUpstreamSpan(spans.get(2));
        assertSendSpan(spans.get(1));
        assertDownstreamSpan(spans.get(0));
    }

    private void executeSendRequest() throws Throwable {
        Object[] sendArguments = new Object[]{originalSendBiFunction};
        sendInterceptor.beforeMethod(enhancedInstance, null, sendArguments, null, null);
        sendInterceptor.afterMethod(enhancedInstance, null, new Object[0], null, enhancedInstance);
        ((BiFunction<? super HttpClientRequest, ? super NettyOutbound, ? extends Publisher<Void>>) sendArguments[0])
                .apply(mockRequest, null);
        Object[] responseConnectionArguments = new Object[]{originalResponseConnectionBiFunction};
        responseConnectionInterceptor
                .beforeMethod(enhancedInstance, null, responseConnectionArguments, null, null);
        Flux flux = Flux.just(0);

        flux = (Flux) responseConnectionInterceptor.afterMethod(enhancedInstance, null, new Object[0], null, flux);

        ((BiFunction<? super HttpClientResponse, ? super Connection, ? extends Publisher<Void>>) responseConnectionArguments[0])
                .apply(mockResponse, null);
        flux.blockFirst();
    }

    private void assertUpstreamSpan(AbstractSpan span) {
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
        SpanAssert.assertComponent(span, ComponentsDefine.SPRING_WEBFLUX);
    }

    private void assertSendSpan(AbstractSpan span) {
        SpanAssert.assertComponent(span, ComponentsDefine.SPRING_CLOUD_GATEWAY);
        assertEquals(span.getOperationName(), "SpringCloudGateway/send");
    }

    private void assertDownstreamSpan(AbstractSpan span) {
        SpanAssert.assertLayer(span, SpanLayer.HTTP);
        SpanAssert.assertComponent(span, ComponentsDefine.SPRING_CLOUD_GATEWAY);
        SpanAssert.assertTagSize(span, 2);
        SpanAssert.assertTag(span, 0, URI);
        SpanAssert.assertTag(span, 1, String.valueOf(HttpResponseStatus.OK.code()));
    }

    private void stopEntrySpan() {
        if (ContextManager.isActive() && ContextManager.activeSpan() == entrySpan) {
            ContextManager.stopSpan(entrySpan);
        }
    }
}
