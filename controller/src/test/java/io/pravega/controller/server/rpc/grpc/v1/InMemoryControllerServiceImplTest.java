/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.controller.server.rpc.grpc.v1;

import io.pravega.auth.AuthHandler;
import io.pravega.auth.AuthorizationException;
import io.pravega.common.cluster.Cluster;
import io.pravega.common.cluster.Host;
import io.pravega.common.concurrent.ExecutorServiceHelpers;
import io.pravega.controller.metrics.StreamMetrics;
import io.pravega.controller.metrics.TransactionMetrics;
import io.pravega.controller.mocks.ControllerEventStreamWriterMock;
import io.pravega.controller.mocks.EventStreamWriterMock;
import io.pravega.controller.mocks.SegmentHelperMock;
import io.pravega.controller.mocks.EventHelperMock;
import io.pravega.controller.mocks.ControllerEventTableWriterMock;
import io.pravega.controller.server.ControllerService;
import io.pravega.controller.server.SegmentHelper;
import io.pravega.controller.server.eventProcessor.requesthandlers.AutoScaleTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.DeleteStreamTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.ScaleOperationTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.SealStreamTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.StreamRequestHandler;
import io.pravega.controller.server.eventProcessor.requesthandlers.TruncateStreamTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.UpdateStreamTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.kvtable.CreateTableTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.kvtable.DeleteTableTask;
import io.pravega.controller.server.eventProcessor.requesthandlers.kvtable.TableRequestHandler;
import io.pravega.controller.server.security.auth.AuthorizationResource;
import io.pravega.controller.server.security.auth.AuthorizationResourceImpl;
import io.pravega.controller.server.security.auth.GrpcAuthHelper;
import io.pravega.controller.store.kvtable.AbstractKVTableMetadataStore;
import io.pravega.controller.store.kvtable.KVTableMetadataStore;
import io.pravega.controller.store.kvtable.KVTableStoreFactory;
import io.pravega.controller.store.stream.BucketStore;
import io.pravega.controller.store.stream.StreamMetadataStore;
import io.pravega.controller.store.stream.StreamStoreFactory;
import io.pravega.controller.store.stream.AbstractStreamMetadataStore;
import io.pravega.controller.store.task.TaskMetadataStore;
import io.pravega.controller.store.task.TaskStoreFactoryForTests;
import io.pravega.controller.stream.api.grpc.v1.Controller;
import io.pravega.controller.task.EventHelper;
import io.pravega.controller.task.KeyValueTable.TableMetadataTasks;
import io.pravega.controller.task.Stream.StreamMetadataTasks;
import io.pravega.controller.task.Stream.StreamTransactionMetadataTasks;

import io.pravega.shared.security.auth.AccessOperation;
import io.pravega.test.common.AssertExtensions;
import org.junit.After;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * InMemory stream store configuration.
 */
public class InMemoryControllerServiceImplTest extends ControllerServiceImplTest {

    private TaskMetadataStore taskMetadataStore;
    private StreamMetadataTasks streamMetadataTasks;
    private StreamRequestHandler streamRequestHandler;

    private ScheduledExecutorService executorService;
    private StreamTransactionMetadataTasks streamTransactionMetadataTasks;
    private StreamMetadataStore streamStore;
    private SegmentHelper segmentHelper;

    private KVTableMetadataStore kvtStore;
    private TableMetadataTasks kvtMetadataTasks;
    private TableRequestHandler tableRequestHandler;
    
    @Override
    public ControllerService getControllerService() {
        executorService = ExecutorServiceHelpers.newScheduledThreadPool(20, "testpool");
    
        taskMetadataStore = TaskStoreFactoryForTests.createInMemoryStore(executorService);
        streamStore = StreamStoreFactory.createInMemoryStore(executorService);
        BucketStore bucketStore = StreamStoreFactory.createInMemoryBucketStore();
        StreamMetrics.initialize();
        TransactionMetrics.initialize();

        segmentHelper = SegmentHelperMock.getSegmentHelperMockForTables(executorService);
        EventHelper helperMock = EventHelperMock.getEventHelperMock(executorService, "host", ((AbstractStreamMetadataStore) streamStore).getHostTaskIndex());
        streamMetadataTasks = new StreamMetadataTasks(streamStore, bucketStore, taskMetadataStore, segmentHelper,
                executorService, "host", GrpcAuthHelper.getDisabledAuthHelper(), requestTracker, helperMock);
        streamTransactionMetadataTasks = new StreamTransactionMetadataTasks(streamStore, segmentHelper,
                executorService, "host", GrpcAuthHelper.getDisabledAuthHelper());
        this.streamRequestHandler = new StreamRequestHandler(new AutoScaleTask(streamMetadataTasks, streamStore, executorService),
                new ScaleOperationTask(streamMetadataTasks, streamStore, executorService),
                new UpdateStreamTask(streamMetadataTasks, streamStore, bucketStore, executorService),
                new SealStreamTask(streamMetadataTasks, streamTransactionMetadataTasks, streamStore, executorService),
                new DeleteStreamTask(streamMetadataTasks, streamStore, bucketStore, executorService),
                new TruncateStreamTask(streamMetadataTasks, streamStore, executorService),
                streamStore,
                executorService);
        streamMetadataTasks.setRequestEventWriter(new ControllerEventStreamWriterMock(streamRequestHandler, executorService));
        streamTransactionMetadataTasks.initializeStreamWriters(new EventStreamWriterMock<>(), new EventStreamWriterMock<>());

        this.kvtStore = KVTableStoreFactory.createInMemoryStore(streamStore, executorService);
        EventHelper tableEventHelper = EventHelperMock.getEventHelperMock(executorService, "host",
                ((AbstractKVTableMetadataStore) kvtStore).getHostTaskIndex());
        this.kvtMetadataTasks = new TableMetadataTasks(kvtStore, segmentHelper, executorService, executorService,
                "host", GrpcAuthHelper.getDisabledAuthHelper(), requestTracker, tableEventHelper);
        this.tableRequestHandler = new TableRequestHandler(new CreateTableTask(this.kvtStore, this.kvtMetadataTasks,
                executorService), new DeleteTableTask(this.kvtStore, this.kvtMetadataTasks,
                executorService), this.kvtStore, executorService);
        tableEventHelper.setRequestEventWriter(new ControllerEventTableWriterMock(tableRequestHandler, executorService));

        Cluster mockCluster = mock(Cluster.class);
        when(mockCluster.getClusterMembers()).thenReturn(Collections.singleton(new Host("localhost", 9090, null)));
        return new ControllerService(kvtStore, kvtMetadataTasks, streamStore, StreamStoreFactory.createInMemoryBucketStore(), streamMetadataTasks, streamTransactionMetadataTasks,
                SegmentHelperMock.getSegmentHelperMock(), executorService, mockCluster);
    }

    @After
    public void tearDown() throws Exception {
        ExecutorServiceHelpers.shutdown(executorService);
        if (streamMetadataTasks != null) {
            streamMetadataTasks.close();
        }
        if (streamTransactionMetadataTasks != null) {
            streamTransactionMetadataTasks.close();
        }
        streamStore.close();
        StreamMetrics.reset();
        TransactionMetrics.reset();
    }

    @Test
    public void supplierReturnsEmptyTokenWhenAuthIsDisabled() {
        assertEquals("", this.controllerService.delegationTokenSupplier(Controller.StreamInfo.newBuilder().build()).get());
    }

    @Test
    public void supplierThrowsExceptionForMarkStreamsForWriteAccessOperations() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);

        AssertExtensions.assertThrows(AuthorizationException.class, () -> objectUnderTest.delegationTokenSupplier(
                createStreamInfoProtobufMessage("testScope", "_MARKtestStream", AccessOperation.READ_WRITE)).get());
        AssertExtensions.assertThrows(AuthorizationException.class, () -> objectUnderTest.delegationTokenSupplier(
                createStreamInfoProtobufMessage("testScope", "_MARKtestStream", AccessOperation.WRITE)).get());
    }

    @Test
    public void supplierForMarkStreamsForReadAccessOperations() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);

        AuthorizationResource authResource = new AuthorizationResourceImpl();
        String markStreamResource = authResource.ofStreamInScope("testScope", "_MARKtestStream");
        String streamResource = authResource.ofStreamInScope("testScope", "testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "_MARKtestStream", AccessOperation.READ);

        // For mark streams, authorization is done against the corresponding stream
        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(markStreamResource, AuthHandler.Permissions.READ);
        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesTokenWithRequestedReadPermission() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String streamResource = new AuthorizationResourceImpl().ofStreamInScope("testScope", "testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "testStream", AccessOperation.READ);

        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(streamResource, AuthHandler.Permissions.READ);

        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesTokenWithReadWritePermissionByDefault() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String streamResource = new AuthorizationResourceImpl().ofStreamInScope("testScope", "testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "testStream", null);
        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ_UPDATE);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(streamResource, AuthHandler.Permissions.READ_UPDATE);

        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesTokenWithReadWhenRequestedPermissionIsUnexpected() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String streamResource = new AuthorizationResourceImpl().ofStreamInScope("testScope", "testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "testStream", AccessOperation.NONE);

        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.NONE);
        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(streamResource, AuthHandler.Permissions.NONE);

        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesTokenWithReadUpdateForInternalStreamsByDefault() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String streamResource = new AuthorizationResourceImpl().ofStreamInScope("testScope", "_testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "_testStream", null);
        System.out.println(request.getAccessOperation().name());

        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ_UPDATE);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(streamResource, AuthHandler.Permissions.READ_UPDATE);
        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesTokenWithReadForInternalStreamsWhenRequestedIsSame() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String streamResource = new AuthorizationResourceImpl().ofStreamInScope("testScope", "_testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "_testStream",
                AccessOperation.READ);

        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(streamResource, AuthHandler.Permissions.READ);
        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesAppropriateTokenForInternalStreamsBasedOnAccessOperation() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String streamResource = new AuthorizationResourceImpl().ofInternalStream("testScope", "_testStream");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "_testStream",
                AccessOperation.READ_WRITE);

        doReturn("").when(mockAuthHelper).checkAuthorization(streamResource, AuthHandler.Permissions.READ_UPDATE);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken(streamResource, AuthHandler.Permissions.READ_UPDATE);
        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    @Test
    public void supplierCreatesAppropriateTokenForRGStreamsBasedOnAccessOperation() {
        GrpcAuthHelper mockAuthHelper = spy(new GrpcAuthHelper(true, "tokenSigningKey", 600));
        ControllerServiceImpl objectUnderTest = new ControllerServiceImpl(null, mockAuthHelper, requestTracker, true, true, 200);
        String resource = new AuthorizationResourceImpl().ofInternalStream("testScope", "_RGtestApp");
        Controller.StreamInfo request = createStreamInfoProtobufMessage("testScope", "_RGtestApp",
                AccessOperation.READ_WRITE);

        doReturn("").when(mockAuthHelper).checkAuthorization(resource, AuthHandler.Permissions.READ);
        doReturn("dummy.delegation.token").when(mockAuthHelper).createDelegationToken("prn::/scope:testScope/stream:_RGtestApp", AuthHandler.Permissions.READ_UPDATE);
        assertEquals("dummy.delegation.token", objectUnderTest.delegationTokenSupplier(request).get());
    }

    private Controller.StreamInfo createStreamInfoProtobufMessage(String scope, String stream, AccessOperation accessOperation) {
        Controller.StreamInfo.Builder builder = Controller.StreamInfo.newBuilder();
        if (scope != null) {
            builder.setScope(scope);
        }
        if (stream != null) {
            builder.setStream(stream);
        }
        if (accessOperation != null) {
            builder.setAccessOperation(Controller.StreamInfo.AccessOperation.valueOf(accessOperation.name()));
        }
        return builder.build();
    }
}
