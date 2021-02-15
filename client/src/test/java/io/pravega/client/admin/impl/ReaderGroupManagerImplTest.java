/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.client.admin.impl;

import com.google.common.collect.ImmutableMap;
import io.pravega.client.control.impl.Controller;
import io.pravega.client.segment.impl.Segment;
import io.pravega.client.state.InitialUpdate;
import io.pravega.client.state.StateSynchronizer;
import io.pravega.client.state.SynchronizerConfig;
import io.pravega.client.stream.ReaderGroupConfig;
import io.pravega.client.stream.Serializer;
import io.pravega.client.stream.Stream;
import io.pravega.client.stream.StreamCut;
import io.pravega.client.stream.impl.ClientFactoryImpl;
import io.pravega.client.stream.impl.ReaderGroupState;
import io.pravega.client.stream.impl.StreamCutImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReaderGroupManagerImplTest {
    private static final String SCOPE = "scope";
    private static final String GROUP_NAME = "readerGroup";
    private ReaderGroupManagerImpl readerGroupManager;
    @Mock
    private ClientFactoryImpl clientFactory;
    @Mock
    private Controller controller;
    @Mock
    private StateSynchronizer<ReaderGroupState> synchronizer;
    @Mock
    private ReaderGroupState state;

    @Before
    public void setUp() throws Exception {
        readerGroupManager = new ReaderGroupManagerImpl(SCOPE, controller, clientFactory);
    }

    @After
    public void shutDown() {
        synchronizer.close();
        controller.close();
        clientFactory.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateReaderGroup() {
        ReaderGroupConfig config = ReaderGroupConfig.builder().startFromStreamCuts(ImmutableMap.<Stream, StreamCut>builder()
                .put(createStream("s1"), createStreamCut("s1", 2))
                .put(createStream("s2"), createStreamCut("s2", 3)).build())
                .retentionType(ReaderGroupConfig.StreamDataRetention.MANUAL_RELEASE_AT_USER_STREAMCUT)
                .build();
        ReaderGroupConfig expectedConfig = config.toBuilder().readerGroupId(UUID.randomUUID()).generation(0L).build();
        when(controller.createReaderGroup(anyString(), anyString(), any(ReaderGroupConfig.class))).thenReturn(CompletableFuture.completedFuture(expectedConfig));
        when(clientFactory.createStateSynchronizer(anyString(), any(Serializer.class), any(Serializer.class),
                any(SynchronizerConfig.class))).thenReturn(synchronizer);
        when(synchronizer.getState()).thenReturn(state);
        when(state.getConfig()).thenReturn(expectedConfig);
        // Create a ReaderGroup
        readerGroupManager.createReaderGroup(GROUP_NAME, config);
        verify(clientFactory, times(1)).createStateSynchronizer(anyString(), any(Serializer.class),
                any(Serializer.class), any(SynchronizerConfig.class));
        verify(synchronizer, times(1)).initialize(any(InitialUpdate.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCreateReaderGroupWithMigration() {
        ReaderGroupConfig config = ReaderGroupConfig.builder().startFromStreamCuts(ImmutableMap.<Stream, StreamCut>builder()
                .put(createStream("s1"), createStreamCut("s1", 2))
                .put(createStream("s2"), createStreamCut("s2", 3)).build())
                .retentionType(ReaderGroupConfig.StreamDataRetention.MANUAL_RELEASE_AT_USER_STREAMCUT)
                .build();
        when(controller.createReaderGroup(anyString(), anyString(), any(ReaderGroupConfig.class)))
                .thenReturn(CompletableFuture.completedFuture(config));
        when(clientFactory.createStateSynchronizer(anyString(), any(Serializer.class), any(Serializer.class),
                any(SynchronizerConfig.class))).thenReturn(synchronizer);
        when(synchronizer.getState()).thenReturn(state);
        when(state.getConfig()).thenReturn(config);
        assertEquals(ReaderGroupConfig.DEFAULT_UUID, config.getReaderGroupId());
        assertEquals(ReaderGroupConfig.DEFAULT_GENERATION, config.getGeneration());
        // Create a ReaderGroup
        readerGroupManager.createReaderGroup(GROUP_NAME, config);
        verify(clientFactory, times(1)).createStateSynchronizer(anyString(), any(Serializer.class),
                any(Serializer.class), any(SynchronizerConfig.class));
        verify(synchronizer, times(1)).initialize(any(InitialUpdate.class));
        verify(synchronizer, times(1)).updateState(any(StateSynchronizer.UpdateGenerator.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeleteReaderGroup() {
        final UUID rgId = UUID.randomUUID();
        ReaderGroupConfig config = ReaderGroupConfig.builder().startFromStreamCuts(ImmutableMap.<Stream, StreamCut>builder()
                .put(createStream("s1"), createStreamCut("s1", 2))
                .put(createStream("s2"), createStreamCut("s2", 3)).build())
                .readerGroupId(rgId).generation(0L)
                .retentionType(ReaderGroupConfig.StreamDataRetention.MANUAL_RELEASE_AT_USER_STREAMCUT)
                .build();
        when(clientFactory.createStateSynchronizer(anyString(), any(Serializer.class), any(Serializer.class),
                any(SynchronizerConfig.class))).thenReturn(synchronizer);
        when(synchronizer.getState()).thenReturn(state);
        when(state.getConfig()).thenReturn(config);
        when(controller.deleteReaderGroup(SCOPE, GROUP_NAME, config.getReaderGroupId(), config.getGeneration())).thenReturn(CompletableFuture.completedFuture(true));
        // Delete ReaderGroup
        readerGroupManager.deleteReaderGroup(GROUP_NAME);
        verify(controller, times(1)).deleteReaderGroup(SCOPE, GROUP_NAME, config.getReaderGroupId(), config.getGeneration());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDeleteReaderGroupWithMigration() {
        ReaderGroupConfig config = ReaderGroupConfig.builder().startFromStreamCuts(ImmutableMap.<Stream, StreamCut>builder()
                .put(createStream("s1"), createStreamCut("s1", 2))
                .put(createStream("s2"), createStreamCut("s2", 3)).build())
                .retentionType(ReaderGroupConfig.StreamDataRetention.MANUAL_RELEASE_AT_USER_STREAMCUT)
                .build();
        when(clientFactory.createStateSynchronizer(anyString(), any(Serializer.class), any(Serializer.class),
                any(SynchronizerConfig.class))).thenReturn(synchronizer);
        when(synchronizer.getState()).thenReturn(state);
        when(state.getConfig()).thenReturn(config);

        ReaderGroupConfig expectedConfig = config.toBuilder().readerGroupId(UUID.randomUUID()).generation(0L).build();
        when(controller.createReaderGroup(anyString(), anyString(), any(ReaderGroupConfig.class)))
               .thenReturn(CompletableFuture.completedFuture(expectedConfig));
        when(controller.deleteReaderGroup(anyString(), anyString(), any(UUID.class), anyLong()))
                .thenReturn(CompletableFuture.completedFuture(true));
        // Delete ReaderGroup
        readerGroupManager.deleteReaderGroup(GROUP_NAME);
        verify(controller, times(1)).deleteReaderGroup(SCOPE, GROUP_NAME, expectedConfig.getReaderGroupId(), expectedConfig.getGeneration());
    }

    private StreamCut createStreamCut(String streamName, int numberOfSegments) {
        Map<Segment, Long> positions = new HashMap<>();
        IntStream.of(numberOfSegments).forEach(segNum -> positions.put(new Segment(SCOPE, streamName, segNum), 10L));
        return new StreamCutImpl(createStream(streamName), positions);
    }

    private Stream createStream(String streamName) {
        return Stream.of(SCOPE, streamName);
    }
}
