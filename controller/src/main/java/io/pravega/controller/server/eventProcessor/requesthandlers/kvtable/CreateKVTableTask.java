/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.controller.server.eventProcessor.requesthandlers.kvtable;

import com.google.common.base.Preconditions;
import io.pravega.client.tables.KeyValueTableConfiguration;
import io.pravega.common.Exceptions;
import io.pravega.controller.server.eventProcessor.requesthandlers.EventTask;
import io.pravega.controller.store.OperationContext;
import io.pravega.controller.store.VersionedMetadata;
import io.pravega.controller.store.kvtable.CreateKVTableResponse;
import io.pravega.controller.store.kvtable.KVTableMetadataStore;
import io.pravega.controller.store.kvtable.KVTableState;
import io.pravega.controller.store.stream.CreateStreamResponse;
import io.pravega.controller.store.stream.State;
import io.pravega.controller.store.stream.StoreException;
import io.pravega.controller.stream.api.grpc.v1.Controller;
import io.pravega.controller.task.KeyValueTable.KVTableMetadataTasks;
import io.pravega.shared.NameUtils;
import io.pravega.shared.controller.event.kvtable.CreateKVTableEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.pravega.controller.task.TaskStepsRetryHelper.withRetries;


/**
 * Request handler for performing scale operations received from requeststream.
 */
@Slf4j
public class CreateKVTableTask implements EventTask<CreateKVTableEvent> {

    private final KVTableMetadataTasks kvtMetadataTasks;
    private final KVTableMetadataStore kvtMetadataStore;
    private final ScheduledExecutorService executor;

    public CreateKVTableTask(final KVTableMetadataTasks kvtMetaTasks,
                             final KVTableMetadataStore kvtMetaStore,
                             final ScheduledExecutorService executor) {
        Preconditions.checkNotNull(kvtMetaTasks);
        Preconditions.checkNotNull(kvtMetaStore);
        Preconditions.checkNotNull(executor);
        this.kvtMetadataTasks = kvtMetaTasks;
        this.kvtMetadataStore = kvtMetaStore;
        this.executor = executor;
    }

    @Override
    public CompletableFuture<Void> execute(final CreateKVTableEvent request) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        String scope = request.getScopeName();
        String kvt = request.getKvtName();
        int partitionCount = request.getPartitionCount();
        long creationTime = request.getTimestamp();
        long requestId = request.getRequestId();
        KeyValueTableConfiguration config = new KeyValueTableConfiguration(partitionCount);

        this.kvtMetadataStore.createKeyValueTable(scope, kvt, config, creationTime, null, executor)
                .thenComposeAsync(response -> {
                    //log.info(requestId, "{}/{} created in metadata store", scope, kvt);
                    Controller.CreateKeyValueTableStatus.Status status = translate(response.getStatus());
                    // only if its a new kvtable or an already existing non-active kvtable then we will create
                    // segments and change the state of the kvtable to active.
                    if (response.getStatus().equals(CreateKVTableResponse.CreateStatus.NEW) ||
                            response.getStatus().equals(CreateStreamResponse.CreateStatus.EXISTS_CREATING)) {
                        final int startingSegmentNumber = response.getStartingSegmentNumber();
                        final int minNumSegments = response.getConfiguration().getPartitionCount();
                        List<Long> newSegments = IntStream.range(startingSegmentNumber, startingSegmentNumber + minNumSegments)
                                .boxed()
                                .map(x -> NameUtils.computeSegmentId(x, 0))
                                .collect(Collectors.toList());
                        return kvtMetadataTasks.notifyNewSegments(scope, kvt, newSegments, requestId)
                                .thenCompose(y -> {
                                    final OperationContext context = kvtMetadataStore.createContext(scope, kvt);
                                    //TODO: add withRetries
                                    kvtMetadataStore.getVersionedState(scope, kvt, context, executor)
                                            .thenCompose(state -> {
                                                if (state.getObject().equals(State.CREATING)) {
                                                    kvtMetadataStore.updateVersionedState(scope, kvt, KVTableState.ACTIVE,
                                                            state, context, executor);
                                                }
                                                return CompletableFuture.completedFuture(null);
                                            });
                                    return CompletableFuture.completedFuture(null);
                                });
                    }
                    return CompletableFuture.completedFuture(null);
                });
        return CompletableFuture.completedFuture(null);
    }

    private Controller.CreateKeyValueTableStatus.Status translate(CreateKVTableResponse.CreateStatus status) {
        Controller.CreateKeyValueTableStatus.Status retVal;
        switch (status) {
            case NEW:
                retVal = Controller.CreateKeyValueTableStatus.Status.SUCCESS;
                break;
            case EXISTS_ACTIVE:
            case EXISTS_CREATING:
                retVal = Controller.CreateKeyValueTableStatus.Status.TABLE_EXISTS;
                break;
            case FAILED:
            default:
                retVal = Controller.CreateKeyValueTableStatus.Status.FAILURE;
                break;
        }
        return retVal;
    }

    @Override
    public CompletableFuture<Void> writeBack(CreateKVTableEvent event) {
        return kvtMetadataTasks.writeEvent(event);
    }

    @Override
    public CompletableFuture<Boolean> hasTaskStarted(CreateKVTableEvent event) {
        return kvtMetadataStore.getState(event.getScopeName(), event.getKvtName(), true, null, executor)
                                  .thenApply(state -> state.equals(KVTableState.CREATING));

    }
}
