/*
 * Licensed to ElasticSearch and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.transport.support;

import org.elasticsearch.common.io.stream.CachedStreamOutput;
import org.elasticsearch.common.io.stream.HandlesStreamOutput;
import org.elasticsearch.common.io.stream.Streamable;
import org.elasticsearch.transport.TransportRequestOptions;
import org.elasticsearch.transport.TransportResponseOptions;

import java.io.IOException;

/**
 *
 */
public class TransportStreams {

    public static final int HEADER_SIZE = 4 + 8 + 1;
    public static final byte[] HEADER_PLACEHOLDER = new byte[HEADER_SIZE];

    public static void writeHeader(byte[] data, int dataLength, long requestId, byte status) {
        writeInt(data, 0, dataLength - 4);  // no need for the header, already there
        writeLong(data, 4, requestId);
        data[12] = status;
    }

    // same as writeLong in StreamOutput

    private static void writeLong(byte[] buffer, int offset, long value) {
        buffer[offset++] = ((byte) (value >> 56));
        buffer[offset++] = ((byte) (value >> 48));
        buffer[offset++] = ((byte) (value >> 40));
        buffer[offset++] = ((byte) (value >> 32));
        buffer[offset++] = ((byte) (value >> 24));
        buffer[offset++] = ((byte) (value >> 16));
        buffer[offset++] = ((byte) (value >> 8));
        buffer[offset] = ((byte) (value));
    }

    // same as writeInt in StreamOutput

    private static void writeInt(byte[] buffer, int offset, int value) {
        buffer[offset++] = ((byte) (value >> 24));
        buffer[offset++] = ((byte) (value >> 16));
        buffer[offset++] = ((byte) (value >> 8));
        buffer[offset] = ((byte) (value));
    }

    private static final byte STATUS_REQRES = 1 << 0;
    private static final byte STATUS_ERROR = 1 << 1;
    private static final byte STATUS_COMPRESS = 1 << 2;

    public static boolean statusIsRequest(byte value) {
        return (value & STATUS_REQRES) == 0;
    }

    public static byte statusSetRequest(byte value) {
        value &= ~STATUS_REQRES;
        return value;
    }

    public static byte statusSetResponse(byte value) {
        value |= STATUS_REQRES;
        return value;
    }

    public static boolean statusIsError(byte value) {
        return (value & STATUS_ERROR) != 0;
    }

    public static byte statusSetError(byte value) {
        value |= STATUS_ERROR;
        return value;
    }

    public static boolean statusIsCompress(byte value) {
        return (value & STATUS_COMPRESS) != 0;
    }

    public static byte statusSetCompress(byte value) {
        value |= STATUS_COMPRESS;
        return value;
    }

    public static void buildRequest(CachedStreamOutput.Entry cachedEntry, final long requestId, final String action, final Streamable message, TransportRequestOptions options) throws IOException {
        byte status = 0;
        status = TransportStreams.statusSetRequest(status);

        if (options.compress()) {
            status = TransportStreams.statusSetCompress(status);
            HandlesStreamOutput stream = cachedEntry.cachedHandlesLzfBytes();
            cachedEntry.bytes().write(HEADER_PLACEHOLDER);
            stream.writeUTF(action);
            message.writeTo(stream);
            stream.flush();
        } else {
            HandlesStreamOutput stream = cachedEntry.cachedHandlesBytes();
            cachedEntry.bytes().write(HEADER_PLACEHOLDER);
            stream.writeUTF(action);
            message.writeTo(stream);
            stream.flush();
        }
        TransportStreams.writeHeader(cachedEntry.bytes().underlyingBytes(), cachedEntry.bytes().size(), requestId, status);
    }

    public static void buildResponse(CachedStreamOutput.Entry cachedEntry, final long requestId, Streamable message, TransportResponseOptions options) throws IOException {
        byte status = 0;
        status = TransportStreams.statusSetResponse(status);

        if (options.compress()) {
            status = TransportStreams.statusSetCompress(status);
            HandlesStreamOutput stream = cachedEntry.cachedHandlesLzfBytes();
            cachedEntry.bytes().write(HEADER_PLACEHOLDER);
            message.writeTo(stream);
            stream.flush();
        } else {
            HandlesStreamOutput stream = cachedEntry.cachedHandlesBytes();
            cachedEntry.bytes().write(HEADER_PLACEHOLDER);
            message.writeTo(stream);
            stream.flush();
        }
        TransportStreams.writeHeader(cachedEntry.bytes().underlyingBytes(), cachedEntry.bytes().size(), requestId, status);
    }
}
