/**
 * Copyright (c) Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.controller.store.kvtable;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import io.pravega.client.stream.StreamConfiguration;
import io.pravega.client.tables.KeyValueTableConfiguration;
import io.pravega.common.Exceptions;
import io.pravega.common.concurrent.Futures;
import io.pravega.common.util.CollectionHelpers;
import io.pravega.controller.metrics.TransactionMetrics;
import io.pravega.controller.store.Version;
import io.pravega.controller.store.VersionedMetadata;
import io.pravega.controller.store.kvtable.records.KVTableStateRecord;
import io.pravega.controller.store.stream.*;
import io.pravega.controller.store.stream.StoreException.DataNotFoundException;
import io.pravega.controller.store.stream.records.*;
import io.pravega.controller.stream.api.grpc.v1.Controller;
import io.pravega.shared.NameUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.pravega.controller.store.stream.AbstractStreamMetadataStore.DATA_NOT_FOUND_PREDICATE;
import static io.pravega.shared.NameUtils.computeSegmentId;
import static io.pravega.shared.NameUtils.getSegmentNumber;
import static java.util.stream.Collectors.groupingBy;

@Slf4j
public abstract class PersistentKeyValueTableBase implements KeyValueTable {
    private final String scope;
    private final String name;

    PersistentKeyValueTableBase(final String scope, final String name) {
        this.scope = scope;
        this.name = name;
    }

    @Override
    public String getScope() {
        return this.scope;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getScopeName() {
        return this.scope;
    }

    @Override
    public CompletableFuture<CreateKVTableResponse> create(final KeyValueTableConfiguration configuration, long createTimestamp, int startingSegmentNumber) {
        /*
        return checkKeyValueTableExists(configuration, createTimestamp, startingSegmentNumber)
                .thenCompose(createKVTResponse -> createKVTableMetadata()
                        .thenCompose((Void v) -> storeCreationTimeIfAbsent(createKVTResponse.getTimestamp()))
                        .thenCompose((Void v) -> createConfigurationIfAbsent(StreamConfigurationRecord.complete(
                                scope, name, createKVTResponse.getConfiguration())))
                        .thenCompose((Void v) -> createStateIfAbsent(StateRecord.builder().state(State.CREATING).build()))
                        .thenCompose((Void v) -> createHistoryRecords(startingSegmentNumber, createKVTResponse))
                        .thenApply((Void v) -> createKVTResponse));
                        */
        KeyValueTableConfiguration config = new KeyValueTableConfiguration(5);
        CreateKVTableResponse createResponse = new CreateKVTableResponse(CreateKVTableResponse.KVTCreateStatus.NEW,
                                                                configuration, createTimestamp, startingSegmentNumber);
        return CompletableFuture.completedFuture(createResponse);

    }

    /*
    private CompletionStage<Void> createHistoryRecords(int startingSegmentNumber, CreateStreamResponse createStreamResponse) {
        final int numSegments = createStreamResponse.getConfiguration().getScalingPolicy().getMinNumSegments();
        // create epoch 0 record
        final double keyRangeChunk = 1.0 / numSegments;

        long creationTime = createStreamResponse.getTimestamp();
        final ImmutableList.Builder<StreamSegmentRecord> builder = ImmutableList.builder();
        
        IntStream.range(0, numSegments).boxed()
                 .forEach(x -> builder.add(newSegmentRecord(0, startingSegmentNumber + x, creationTime,
                                                                    x * keyRangeChunk, (x + 1) * keyRangeChunk)));

        EpochRecord epoch0 = new EpochRecord(0, 0, builder.build(), creationTime);

        return createEpochRecord(epoch0)
                .thenCompose(r -> createHistoryChunk(epoch0))
                .thenCompose(r -> createSealedSegmentSizeMapShardIfAbsent(0))
                .thenCompose(r -> createRetentionSetDataIfAbsent(new RetentionSet(ImmutableList.of())))
                .thenCompose(r -> createCurrentEpochRecordDataIfAbsent(epoch0));
    }

    private CompletionStage<Void> createHistoryChunk(EpochRecord epoch0) {
        HistoryTimeSeriesRecord record = new HistoryTimeSeriesRecord(0, 0, 
                ImmutableList.of(), epoch0.getSegments(), epoch0.getCreationTime());
        return createHistoryTimeSeriesChunk(0, record);
    }

    private CompletableFuture<Void> createHistoryTimeSeriesChunk(int chunkNumber, HistoryTimeSeriesRecord epoch) {
        ImmutableList.Builder<HistoryTimeSeriesRecord> builder = ImmutableList.builder();
        HistoryTimeSeries timeSeries = new HistoryTimeSeries(builder.add(epoch).build());
        return createHistoryTimeSeriesChunkDataIfAbsent(chunkNumber, timeSeries);
    }

*/
    /**
     * Fetch configuration at configurationPath.
     *
     * @return Future of stream configuration
     */
    /*
    @Override
    public CompletableFuture<KeyValueTableConfiguration> getConfiguration() {
        return getConfigurationData(false).thenApply(x -> x.getObject().getStreamConfiguration());
    }

    @Override
    public CompletableFuture<VersionedMetadata<KeyValueTableConfigurationRecord>> getVersionedConfigurationRecord() {
        return getConfigurationData(true)
                .thenApply(data -> new VersionedMetadata<>(data.getObject(), data.getVersion()));
    }

    @Override
    public CompletableFuture<Void> updateState(final State state) {
        return getStateData(true)
                .thenCompose(currState -> {
                    VersionedMetadata<State> currentState = new VersionedMetadata<State>(currState.getObject().getState(), currState.getVersion());
                    return Futures.toVoid(updateVersionedState(currentState, state));
                });
    }

    @Override
    public CompletableFuture<VersionedMetadata<KVTableState>> getVersionedState() {
        return getStateData(true)
                .thenApply(x -> new VersionedMetadata<>(x.getObject().getState(), x.getVersion()));
    }

    @Override
    public CompletableFuture<VersionedMetadata<State>> updateVersionedState(final VersionedMetadata<State> previous, final State newState) {
        if (State.isTransitionAllowed(previous.getObject(), newState)) {
            return setStateData(new VersionedMetadata<>(KVTableStateRecord.builder().state(newState).build(), previous.getVersion()))
                    .thenApply(updatedVersion -> new VersionedMetadata<>(newState, updatedVersion));
        } else {
            return Futures.failedFuture(StoreException.create(
                    StoreException.Type.OPERATION_NOT_ALLOWED,
                    "KeyValueTable: " + getName() + " State: " + newState.name() + " current state = " +
                            previous.getObject()));
        }
    }

    @Override
    public CompletableFuture<State> getState(boolean ignoreCached) {
        return getStateData(ignoreCached)
                .thenApply(x -> x.getObject().getState());
    }

*/
    /**
     * Fetches Segment metadata from the epoch in which segment was created.
     *
     * @param segmentId segment id.
     * @return : Future, which when complete contains segment object
     */
    /*
    @Override
    public CompletableFuture<StreamSegmentRecord> getSegment(final long segmentId) {
        // extract epoch from segment id.
        // fetch epoch record for the said epoch
        // extract segment record from it.
        int epoch = NameUtils.getEpoch(segmentId);
        return getEpochRecord(epoch)
                .thenApply(epochRecord -> {
                    Optional<StreamSegmentRecord> segmentRecord = epochRecord.getSegments().stream()
                                                                             .filter(x -> x.segmentId() == segmentId).findAny();
                    return segmentRecord
                            .orElseThrow(() -> StoreException.create(StoreException.Type.DATA_NOT_FOUND,
                                    "segment not found in epoch"));
                });
    }

    @Override
    public CompletableFuture<Set<Long>> getAllSegmentIds() {
        CompletableFuture<Map<StreamSegmentRecord, Integer>> fromSpanFuture = getTruncationRecord()
                .thenCompose(truncationRecord -> {
                    if (truncationRecord.getObject().equals(StreamTruncationRecord.EMPTY)) {
                        return getEpochRecord(0)
                                .thenApply(this::convertToSpan);
                    } else {
                        return CompletableFuture.completedFuture(truncationRecord.getObject().getSpan());
                    }
                });
        CompletableFuture<Map<StreamSegmentRecord, Integer>> toSpanFuture = getActiveEpoch(true)
                .thenApply(this::convertToSpan);

        return CompletableFuture.allOf(fromSpanFuture, toSpanFuture)
                                .thenCompose(v -> {
                                    Map<StreamSegmentRecord, Integer> fromSpan = fromSpanFuture.join();
                                    Map<StreamSegmentRecord, Integer> toSpan = toSpanFuture.join();
                                    return segmentsBetweenStreamCutSpans(fromSpan, toSpan)
                                            .thenApply(x -> x.stream().map(StreamSegmentRecord::segmentId).collect(Collectors.toSet()));
                                });
    }



    private CompletableFuture<EpochRecord> getActiveEpochRecord(boolean ignoreCached) {
        return getCurrentEpochRecordData(ignoreCached).thenApply(VersionedMetadata::getObject);
    }

    @Override
    public CompletableFuture<List<StreamSegmentRecord>> getActiveSegments() {
        // read current epoch record
        return verifyLegalState()
                .thenCompose(v -> getActiveEpochRecord(true).thenApply(epochRecord -> epochRecord.getSegments()));
    }



    @Override
    public CompletableFuture<List<StreamSegmentRecord>> getSegmentsInEpoch(final int epoch) {
        return getEpochRecord(epoch)
                .thenApply(epochRecord -> epochRecord.getSegments());
    }
 
    private CompletableFuture<Void> updateHistoryTimeSeries(HistoryTimeSeriesRecord record) {
        int historyChunk = record.getEpoch() / historyChunkSize.get();
        boolean isFirst = record.getEpoch() % historyChunkSize.get() == 0;

        if (isFirst) {
            return createHistoryTimeSeriesChunk(historyChunk, record);
        } else {
            return getHistoryTimeSeriesChunkData(historyChunk, true)
                    .thenCompose(x -> {
                        HistoryTimeSeries historyChunkTimeSeries = x.getObject();
                        if (historyChunkTimeSeries.getLatestRecord().getEpoch() < record.getEpoch()) {
                            HistoryTimeSeries update = HistoryTimeSeries.addHistoryRecord(historyChunkTimeSeries, record);
                            return Futures.toVoid(updateHistoryTimeSeriesChunkData(historyChunk, new VersionedMetadata<>(update, x.getVersion())));
                        } else {
                            return CompletableFuture.completedFuture(null);
                        }
                    });
        }
    }



    @Override
    public CompletableFuture<VersionedMetadata<EpochTransitionRecord>> getEpochTransition() {
        return getEpochTransitionNode()
                .thenApply(x -> new VersionedMetadata<>(x.getObject(), x.getVersion()));
    }


    @SneakyThrows
    private TxnStatus handleDataNotFoundException(Throwable ex) {
        if (Exceptions.unwrap(ex) instanceof DataNotFoundException) {
            return TxnStatus.UNKNOWN;
        } else {
            throw ex;
        }
    }
    
    @Override
    public CompletableFuture<EpochRecord> getActiveEpoch(boolean ignoreCached) {
        return getCurrentEpochRecordData(ignoreCached).thenApply(VersionedMetadata::getObject);
    }

    @Override
    public CompletableFuture<EpochRecord> getEpochRecord(int epoch) {
        return getEpochRecordData(epoch).thenApply(VersionedMetadata::getObject);
    }



    @Override
    public CompletableFuture<Void> createWaitingRequestIfAbsent(String processorName) {
        return createWaitingRequestNodeIfAbsent(processorName);
    }

    @Override
    public CompletableFuture<String> getWaitingRequestProcessor() {
        return getWaitingRequestNode()
                .handle((data, e) -> {
                    if (e != null) {
                        if (Exceptions.unwrap(e) instanceof DataNotFoundException) {
                            return null;
                        } else {
                            throw new CompletionException(e);
                        }
                    } else {
                        return data;
                    }
                });
    }

    @Override
    public CompletableFuture<Void> deleteWaitingRequestConditionally(String processorName) {
        return getWaitingRequestProcessor()
                .thenCompose(waitingRequest -> {
                    if (waitingRequest != null && waitingRequest.equals(processorName)) {
                        return deleteWaitingRequestNode();
                    } else {
                        return CompletableFuture.completedFuture(null);
                    }
                });
    }



    private CompletableFuture<Void> verifyLegalState() {
        return getState(false).thenApply(state -> {
            if (state == null || state.equals(State.UNKNOWN) || state.equals(State.CREATING)) {
                throw StoreException.create(StoreException.Type.ILLEGAL_STATE,
                        "Stream: " + getName() + " State: " + state.name());
            }
            return null;
        });
    }

    private CompletableFuture<Void> createEpochRecord(EpochRecord epoch) {
        return createEpochRecordDataIfAbsent(epoch.getEpoch(), epoch);
    }

    private CompletableFuture<Void> updateCurrentEpochRecord(int newActiveEpoch) {
        return getEpochRecord(newActiveEpoch)
                .thenCompose(epochRecord -> getCurrentEpochRecordData(true)
                        .thenCompose(currentEpochRecordData -> {
                            EpochRecord existing = currentEpochRecordData.getObject();
                            if (existing.getEpoch() < newActiveEpoch) {
                                return Futures.toVoid(updateCurrentEpochRecordData(
                                        new VersionedMetadata<>(epochRecord, currentEpochRecordData.getVersion())));
                            } else {
                                return CompletableFuture.completedFuture(null);
                            }
                        }));
    }





    private int getShardNumber(long segmentId) {
        return NameUtils.getEpoch(segmentId) / shardSize.get();
    }

    private ImmutableMap<StreamSegmentRecord, Integer> convertToSpan(EpochRecord epochRecord) {
        ImmutableMap.Builder<StreamSegmentRecord, Integer> builder = ImmutableMap.builder();
        epochRecord.getSegments().forEach(x -> builder.put(x, epochRecord.getEpoch()));
        return builder.build();
    }

    private Segment transform(StreamSegmentRecord segmentRecord) {
        return new Segment(segmentRecord.segmentId(), segmentRecord.getCreationTime(),
                segmentRecord.getKeyStart(), segmentRecord.getKeyEnd());
    }

    private List<Segment> transform(List<StreamSegmentRecord> segmentRecords) {
        return segmentRecords.stream().map(this::transform).collect(Collectors.toList());
    }
    
    @VisibleForTesting
    CompletableFuture<List<EpochRecord>> fetchEpochs(int fromEpoch, int toEpoch, boolean ignoreCache) {
        // fetch history time series chunk corresponding to from.
        // read entries till either last entry or till to
        // if to is not in this chunk fetch the next chunk and read till to
        // keep doing this until all records till to have been read.
        // keep computing history record from history time series by applying delta on previous.
        return getActiveEpochRecord(ignoreCache)
                .thenApply(currentEpoch -> currentEpoch.getEpoch() / historyChunkSize.get())
                .thenCompose(latestChunkNumber -> Futures.allOfWithResults(
                        IntStream.range(fromEpoch / historyChunkSize.get(), toEpoch / historyChunkSize.get() + 1)
                                 .mapToObj(i -> {
                                     int firstEpoch = i * historyChunkSize.get() > fromEpoch ? i * historyChunkSize.get() : fromEpoch;

                                     boolean ignoreCached = i >= latestChunkNumber;
                                     return getEpochsFromHistoryChunk(i, firstEpoch, toEpoch, ignoreCached);
                                 }).collect(Collectors.toList())))
                .thenApply(c -> c.stream().flatMap(Collection::stream).collect(Collectors.toList()));
    }

    private CompletableFuture<List<EpochRecord>> getEpochsFromHistoryChunk(int chunk, int firstEpoch, int toEpoch, boolean ignoreCached) {
        return getEpochRecord(firstEpoch)
                .thenCompose(first -> getHistoryTimeSeriesChunk(chunk, ignoreCached)
                        .thenCompose(x -> {
                            List<CompletableFuture<EpochRecord>> identity = new ArrayList<>();
                            identity.add(CompletableFuture.completedFuture(first));
                            return Futures.allOfWithResults(x.getHistoryRecords().stream()
                                                             .filter(r -> r.getEpoch() > firstEpoch && r.getEpoch() <= toEpoch)
                                                             .reduce(identity, (r, s) -> {
                                                                 CompletableFuture<EpochRecord> next = newEpochRecord(r.get(r.size() - 1),
                                                                         s.getEpoch(), s.getReferenceEpoch(), s.getSegmentsCreated(),
                                                                         s.getSegmentsSealed().stream().map(StreamSegmentRecord::segmentId)
                                                                          .collect(Collectors.toList()), s.getScaleTime());
                                                                 ArrayList<CompletableFuture<EpochRecord>> list = new ArrayList<>(r);
                                                                 list.add(next);
                                                                 return list;
                                                             }, (r, s) -> {
                                                                 ArrayList<CompletableFuture<EpochRecord>> list = new ArrayList<>(r);
                                                                 list.addAll(s);
                                                                 return list;
                                                             }));
                        }));
    }

    private CompletableFuture<EpochRecord> newEpochRecord(final CompletableFuture<EpochRecord> lastRecordFuture,
                                                          final int epoch, final int referenceEpoch,
                                                          final Collection<StreamSegmentRecord> createdSegments,
                                                          final Collection<Long> sealedSegments, final long time) {
        if (epoch == referenceEpoch) {
            return lastRecordFuture.thenApply(lastRecord -> {
                assert lastRecord.getEpoch() == epoch - 1;
                ImmutableList.Builder<StreamSegmentRecord> segmentsBuilder = ImmutableList.builder();
                lastRecord.getSegments().forEach(segment -> {
                   if (!sealedSegments.contains(segment.segmentId())) {
                       segmentsBuilder.add(segment);
                   }
                });
                segmentsBuilder.addAll(createdSegments);
                return new EpochRecord(epoch, referenceEpoch, segmentsBuilder.build(), time);
            });
        } else {
            return getEpochRecord(epoch);
        }
    }

    private StreamSegmentRecord newSegmentRecord(long segmentId, long time, Double low, Double high) {
        return newSegmentRecord(NameUtils.getEpoch(segmentId), NameUtils.getSegmentNumber(segmentId),
                time, low, high);
    }

    private StreamSegmentRecord newSegmentRecord(int epoch, int segmentNumber, long time, Double low, Double high) {
        return StreamSegmentRecord.builder().creationEpoch(epoch).segmentNumber(segmentNumber).creationTime(time)
                                  .keyStart(low).keyEnd(high).build();
    }

    @VisibleForTesting
    CompletableFuture<Integer> findEpochAtTime(long timestamp, boolean ignoreCached) {
        return getActiveEpoch(ignoreCached)
                .thenCompose(activeEpoch -> searchEpochAtTime(0, activeEpoch.getEpoch() / historyChunkSize.get(),
                        x -> x == activeEpoch.getEpoch() / historyChunkSize.get(), timestamp)
                        .thenApply(epoch -> {
                            if (epoch == -1) {
                                if (timestamp > activeEpoch.getCreationTime()) {
                                    return activeEpoch.getEpoch();
                                } else {
                                    return 0;
                                }

                            } else {
                                return epoch;
                            }
                        }));
    }

    private CompletableFuture<Integer> searchEpochAtTime(int lowest, int highest, Predicate<Integer> ignoreCached, long timestamp) {
        final int middle = (lowest + highest) / 2;

        if (lowest > highest) {
            // either return epoch 0 or latest epoch
            return CompletableFuture.completedFuture(-1);
        }

        return getHistoryTimeSeriesChunk(middle, ignoreCached.test(middle))
                .thenCompose(chunk -> {
                    List<HistoryTimeSeriesRecord> historyRecords = chunk.getHistoryRecords();
                    long rangeLow = historyRecords.get(0).getScaleTime();
                    long rangeHigh = historyRecords.get(historyRecords.size() - 1).getScaleTime();
                    if (timestamp >= rangeLow && timestamp <= rangeHigh) {
                        // found
                        int index = CollectionHelpers.findGreatestLowerBound(historyRecords, x -> Long.compare(timestamp, x.getScaleTime()));
                        assert index >= 0;
                        return CompletableFuture.completedFuture(historyRecords.get(index).getEpoch());
                    } else if (timestamp < rangeLow) {
                        return searchEpochAtTime(lowest, middle - 1, ignoreCached, timestamp);
                    } else {
                        return searchEpochAtTime(middle + 1, highest, ignoreCached, timestamp);
                    }
                });
    }

    @Override
    public CompletableFuture<HistoryTimeSeries> getHistoryTimeSeriesChunk(int chunkNumber) {
        return getHistoryTimeSeriesChunk(chunkNumber, true);
    }
    
    private CompletableFuture<HistoryTimeSeries> getHistoryTimeSeriesChunk(int chunkNumber, boolean ignoreCached) {
        return getHistoryTimeSeriesChunkData(chunkNumber, ignoreCached)
                .thenCompose(x -> {
                    HistoryTimeSeries timeSeries = x.getObject();
                    // we should only retrieve the chunk from cache once the chunk is full to capacity and hence immutable. 
                    if (!ignoreCached && timeSeries.getHistoryRecords().size() < historyChunkSize.get()) {
                        return getHistoryTimeSeriesChunk(chunkNumber, true);
                    }
                    return CompletableFuture.completedFuture(timeSeries);
                });
    }
*/
    // region abstract methods
    /*
    abstract CompletableFuture<CreateKVTableResponse> checkKeyValueTableExists(final StreamConfiguration configuration,
                                                                       final long creationTime, final int startingSegmentNumber);

    abstract CompletableFuture<Void> createKVTableMetadata();
    
    abstract CompletableFuture<Void> storeCreationTimeIfAbsent(final long creationTime);
*/

    // endregion

    // region configuration
    /*
    abstract CompletableFuture<Void> createConfigurationIfAbsent(final StreamConfigurationRecord data);



    // region state
    abstract CompletableFuture<Void> createStateIfAbsent(final StateRecord state);
*/
    /*
    abstract CompletableFuture<Void> deleteKeyValueTable();
    abstract CompletableFuture<Version> setConfigurationData(final VersionedMetadata<StreamConfigurationRecord> configuration);

    abstract CompletableFuture<VersionedMetadata<StreamConfigurationRecord>> getConfigurationData(boolean ignoreCached);
    // endregion

    abstract CompletableFuture<Version> setStateData(final VersionedMetadata<KVTableStateRecord> state);

    abstract CompletableFuture<VersionedMetadata<StateRecord>> getStateData(boolean ignoreCached);
    // endregion

    // region history
    abstract CompletableFuture<Void> createHistoryTimeSeriesChunkDataIfAbsent(int chunkNumber, HistoryTimeSeries data);

    abstract CompletableFuture<VersionedMetadata<HistoryTimeSeries>> getHistoryTimeSeriesChunkData(int chunkNumber, boolean ignoreCached);

    abstract CompletableFuture<Version> updateHistoryTimeSeriesChunkData(int historyChunk, VersionedMetadata<HistoryTimeSeries> tData);

    abstract CompletableFuture<Void> createCurrentEpochRecordDataIfAbsent(EpochRecord data);

    abstract CompletableFuture<Version> updateCurrentEpochRecordData(VersionedMetadata<EpochRecord> data);

    abstract CompletableFuture<VersionedMetadata<EpochRecord>> getCurrentEpochRecordData(boolean ignoreCached);

    abstract CompletableFuture<Void> createEpochRecordDataIfAbsent(int epoch, EpochRecord data);

    abstract CompletableFuture<VersionedMetadata<EpochRecord>> getEpochRecordData(int epoch);

    // region scale
    abstract CompletableFuture<Void> createEpochTransitionIfAbsent(EpochTransitionRecord epochTransition);

    abstract CompletableFuture<Version> updateEpochTransitionNode(VersionedMetadata<EpochTransitionRecord> epochTransition);

    abstract CompletableFuture<VersionedMetadata<EpochTransitionRecord>> getEpochTransitionNode();
    // endregion

    // region processor
    abstract CompletableFuture<Void> createWaitingRequestNodeIfAbsent(String data);

    abstract CompletableFuture<String> getWaitingRequestNode();

    abstract CompletableFuture<Void> deleteWaitingRequestNode();
    // endregion
    */

}