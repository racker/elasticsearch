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

package org.elasticsearch.action.admin.indices.validate.query;

import org.apache.lucene.util.UnicodeUtil;
import org.elasticsearch.ElasticSearchGenerationException;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.broadcast.BroadcastOperationRequest;
import org.elasticsearch.action.support.broadcast.BroadcastOperationThreading;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.BytesHolder;
import org.elasticsearch.common.Required;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.Unicode;
import org.elasticsearch.common.io.BytesStream;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.QueryBuilder;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 * A request to validate a specific query.
 * <p/>
 * <p>The request requires the query source to be set either using {@link #query(org.elasticsearch.index.query.QueryBuilder)},
 * or {@link #query(byte[])}.
 */
public class ValidateQueryRequest extends BroadcastOperationRequest {

    private static final XContentType contentType = Requests.CONTENT_TYPE;

    private byte[] querySource;
    private int querySourceOffset;
    private int querySourceLength;
    private boolean querySourceUnsafe;
    
    private boolean explain;

    private String[] types = Strings.EMPTY_ARRAY;

    ValidateQueryRequest() {
    }

    /**
     * Constructs a new validate request against the provided indices. No indices provided means it will
     * run against all indices.
     */
    public ValidateQueryRequest(String... indices) {
        super(indices);
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = super.validate();
        return validationException;
    }

    /**
     * Controls the operation threading model.
     */
    @Override
    public ValidateQueryRequest operationThreading(BroadcastOperationThreading operationThreading) {
        super.operationThreading(operationThreading);
        return this;
    }

    @Override
    protected void beforeStart() {
        if (querySourceUnsafe) {
            querySource = Arrays.copyOfRange(querySource, querySourceOffset, querySourceOffset + querySourceLength);
            querySourceOffset = 0;
            querySourceUnsafe = false;
        }
    }

    /**
     * Should the listener be called on a separate thread if needed.
     */
    @Override
    public ValidateQueryRequest listenerThreaded(boolean threadedListener) {
        super.listenerThreaded(threadedListener);
        return this;
    }

    public ValidateQueryRequest indices(String... indices) {
        this.indices = indices;
        return this;
    }

    /**
     * The query source to execute.
     */
    BytesHolder querySource() {
        return new BytesHolder(querySource, querySourceOffset, querySourceLength);
    }

    /**
     * The query source to execute.
     *
     * @see org.elasticsearch.index.query.QueryBuilders
     */
    @Required
    public ValidateQueryRequest query(QueryBuilder queryBuilder) {
        BytesStream bos = queryBuilder.buildAsBytes();
        this.querySource = bos.underlyingBytes();
        this.querySourceOffset = 0;
        this.querySourceLength = bos.size();
        this.querySourceUnsafe = false;
        return this;
    }

    /**
     * The query source to execute in the form of a map.
     */
    @Required
    public ValidateQueryRequest query(Map querySource) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(contentType);
            builder.map(querySource);
            return query(builder);
        } catch (IOException e) {
            throw new ElasticSearchGenerationException("Failed to generate [" + querySource + "]", e);
        }
    }

    @Required
    public ValidateQueryRequest query(XContentBuilder builder) {
        try {
            this.querySource = builder.underlyingBytes();
            this.querySourceOffset = 0;
            this.querySourceLength = builder.underlyingBytesLength();
            this.querySourceUnsafe = false;
            return this;
        } catch (IOException e) {
            throw new ElasticSearchGenerationException("Failed to generate [" + builder + "]", e);
        }
    }

    /**
     * The query source to validate. It is preferable to use either {@link #query(byte[])}
     * or {@link #query(org.elasticsearch.index.query.QueryBuilder)}.
     */
    @Required
    public ValidateQueryRequest query(String querySource) {
        UnicodeUtil.UTF8Result result = Unicode.fromStringAsUtf8(querySource);
        this.querySource = result.result;
        this.querySourceOffset = 0;
        this.querySourceLength = result.length;
        this.querySourceUnsafe = true;
        return this;
    }

    /**
     * The query source to validate.
     */
    @Required
    public ValidateQueryRequest query(byte[] querySource) {
        return query(querySource, 0, querySource.length, false);
    }

    /**
     * The query source to validate.
     */
    @Required
    public ValidateQueryRequest query(byte[] querySource, int offset, int length, boolean unsafe) {
        this.querySource = querySource;
        this.querySourceOffset = offset;
        this.querySourceLength = length;
        this.querySourceUnsafe = unsafe;
        return this;
    }

    /**
     * The types of documents the query will run against. Defaults to all types.
     */
    String[] types() {
        return this.types;
    }

    /**
     * The types of documents the query will run against. Defaults to all types.
     */
    public ValidateQueryRequest types(String... types) {
        this.types = types;
        return this;
    }

    /**
     * Indicate if detailed information about query is requested
     */
    public void explain(boolean explain) {
        this.explain = explain;
    }

    /**
     * Indicates if detailed information about query is requested
     */
    public boolean explain() {
        return explain;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);

        BytesHolder bytes = in.readBytesReference();
        querySourceUnsafe = false;
        querySource = bytes.bytes();
        querySourceOffset = bytes.offset();
        querySourceLength = bytes.length();

        int typesSize = in.readVInt();
        if (typesSize > 0) {
            types = new String[typesSize];
            for (int i = 0; i < typesSize; i++) {
                types[i] = in.readUTF();
            }
        }

        explain = in.readBoolean();
        
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        out.writeBytesHolder(querySource, querySourceOffset, querySourceLength);

        out.writeVInt(types.length);
        for (String type : types) {
            out.writeUTF(type);
        }
        
        out.writeBoolean(explain);
    }

    @Override
    public String toString() {
        return "[" + Arrays.toString(indices) + "]" + Arrays.toString(types) + ", querySource[" + Unicode.fromBytes(querySource, querySourceOffset, querySourceLength) + "], explain:" + explain;
    }
}
