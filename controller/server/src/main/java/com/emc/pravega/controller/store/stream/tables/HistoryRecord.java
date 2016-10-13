/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.emc.pravega.controller.store.stream.tables;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.ArrayUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Data
@RequiredArgsConstructor
/**
 * Class corresponding to one row in the HistoryTable
 * HistoryRecords are of variable length, so we will maintain markers for
 * start of row and end of row. We need it in both directions because we need to traverse
 * in both directions on the history table
 * Row : [eventTime, List-of-active-segment-numbers]
 */
public class HistoryRecord {
    private final int endOfRowPointer;
    private final long eventTime;
    private final List<Integer> segments;
    private final int startOfRowPointer;

    public HistoryRecord(long eventTime, List<Integer> segments, int offset) {
        this.eventTime = eventTime;
        this.segments = segments;
        this.startOfRowPointer = offset;
        endOfRowPointer = offset + (Integer.SIZE + Long.SIZE + segments.size() * Integer.SIZE + Integer.SIZE) / 8 - 1;
    }

    public static Optional<HistoryRecord> readRecord(byte[] historyTable, int offset) {
        if (offset >= historyTable.length)
            return Optional.empty();

        int rowEndOffset = Utilities.toInt(ArrayUtils.subarray(historyTable,
                offset,
                offset + (Integer.SIZE / 8)));

        return Optional.of(parse(ArrayUtils.subarray(historyTable,
                offset,
                rowEndOffset + 1)));
    }

    public static Optional<HistoryRecord> readLatestRecord(byte[] historyTable) {
        if (historyTable.length == 0)
            return Optional.empty();

        int lastRowStartOffset = Utilities.toInt(ArrayUtils.subarray(historyTable,
                historyTable.length - (Integer.SIZE / 8),
                historyTable.length));

        return readRecord(historyTable, lastRowStartOffset);
    }

    public static Optional<HistoryRecord> fetchNext(HistoryRecord record, byte[] historyTable) {
        assert historyTable.length >= record.getEndOfRowPointer();

        if (historyTable.length == record.getEndOfRowPointer())
            return Optional.empty();
        else {
            return HistoryRecord.readRecord(historyTable, record.getEndOfRowPointer() + 1);
        }
    }

    public static Optional<HistoryRecord> fetchPrevious(HistoryRecord record,
                                                        byte[] historyTable) {
        if (record.getStartOfRowPointer() == 0)
            return Optional.empty();
        else {
            int rowStartOffset = Utilities.toInt(
                    org.apache.commons.lang3.ArrayUtils.subarray(historyTable,
                            record.getStartOfRowPointer() - (Integer.SIZE / 8),
                            record.getStartOfRowPointer()));

            return HistoryRecord.readRecord(historyTable, rowStartOffset);
        }
    }

    private static HistoryRecord parse(byte[] b) {
        int endOfRowPtr = Utilities.toInt(ArrayUtils.subarray(b, 0, Integer.SIZE / 8));
        long eventTime = Utilities.toLong(ArrayUtils.subarray(b, Integer.SIZE / 8, (Integer.SIZE + Long.SIZE) / 8));

        List<Integer> segments = extractSegments(ArrayUtils.subarray(b,
                (Integer.SIZE + Long.SIZE) / 8,
                b.length - (Integer.SIZE / 8)));

        int startOfRowPtr = Utilities.toInt(ArrayUtils.subarray(b, b.length - (Integer.SIZE / 8), b.length));

        return new HistoryRecord(endOfRowPtr, eventTime, segments, startOfRowPtr);
    }

    private static List<Integer> extractSegments(byte[] b) {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < b.length; i = i + (Integer.SIZE / 8)) {
            result.add(Utilities.toInt(ArrayUtils.subarray(b, i, i + (Integer.SIZE / 8))));
        }
        return result;
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        try {
            outputStream.write(Utilities.toByteArray(endOfRowPointer));
            outputStream.write(Utilities.toByteArray(eventTime));
            for (Integer segment : segments) {
                outputStream.write(Utilities.toByteArray(segment));
            }

            outputStream.write(Utilities.toByteArray(startOfRowPointer));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return outputStream.toByteArray();
    }
}
