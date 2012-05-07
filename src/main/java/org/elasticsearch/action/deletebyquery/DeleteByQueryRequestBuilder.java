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

package org.elasticsearch.action.deletebyquery;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.WriteConsistencyLevel;
import org.elasticsearch.action.support.BaseRequestBuilder;
import org.elasticsearch.action.support.replication.ReplicationType;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;

import java.util.Map;

/**
 *
 */
public class DeleteByQueryRequestBuilder extends BaseRequestBuilder<DeleteByQueryRequest, DeleteByQueryResponse> {

    public DeleteByQueryRequestBuilder(Client client) {
        super(client, new DeleteByQueryRequest());
    }

    /**
     * The indices the delete by query will run against.
     */
    public DeleteByQueryRequestBuilder setIndices(String... indices) {
        request.indices(indices);
        return this;
    }

    /**
     * The types of documents the query will run against. Defaults to all types.
     */
    public DeleteByQueryRequestBuilder setTypes(String... types) {
        request.types(types);
        return this;
    }

    /**
     * A comma separated list of routing values to control the shards the action will be executed on.
     */
    public DeleteByQueryRequestBuilder setRouting(String routing) {
        request.routing(routing);
        return this;
    }

    /**
     * The routing values to control the shards that the action will be executed on.
     */
    public DeleteByQueryRequestBuilder setRouting(String... routing) {
        request.routing(routing);
        return this;
    }


    /**
     * The query source to execute.
     *
     * @see org.elasticsearch.index.query.QueryBuilders
     */
    public DeleteByQueryRequestBuilder setQuery(QueryBuilder queryBuilder) {
        request.query(queryBuilder);
        return this;
    }

    /**
     * The query source to execute. It is preferable to use either {@link #setQuery(byte[])}
     * or {@link #setQuery(org.elasticsearch.index.query.QueryBuilder)}.
     */
    public DeleteByQueryRequestBuilder setQuery(String querySource) {
        request.query(querySource);
        return this;
    }

    /**
     * The query source to execute in the form of a map.
     */
    public DeleteByQueryRequestBuilder setQuery(Map<String, Object> querySource) {
        request.query(querySource);
        return this;
    }

    /**
     * The query source to execute in the form of a builder.
     */
    public DeleteByQueryRequestBuilder setQuery(XContentBuilder builder) {
        request.query(builder);
        return this;
    }

    /**
     * The query source to execute.
     */
    public DeleteByQueryRequestBuilder setQuery(byte[] querySource) {
        request.query(querySource);
        return this;
    }

    /**
     * The query source to execute.
     */
    public DeleteByQueryRequestBuilder setQuery(byte[] querySource, int offset, int length, boolean unsafe) {
        request.query(querySource, offset, length, unsafe);
        return this;
    }

    /**
     * A timeout to wait if the delete by query operation can't be performed immediately. Defaults to <tt>1m</tt>.
     */
    public DeleteByQueryRequestBuilder setTimeout(TimeValue timeout) {
        request.timeout(timeout);
        return this;
    }

    /**
     * A timeout to wait if the delete by query operation can't be performed immediately. Defaults to <tt>1m</tt>.
     */
    public DeleteByQueryRequestBuilder setTimeout(String timeout) {
        request.timeout(timeout);
        return this;
    }

    /**
     * The replication type to use with this operation.
     */
    public DeleteByQueryRequestBuilder setReplicationType(ReplicationType replicationType) {
        request.replicationType(replicationType);
        return this;
    }

    /**
     * The replication type to use with this operation.
     */
    public DeleteByQueryRequestBuilder setReplicationType(String replicationType) {
        request.replicationType(replicationType);
        return this;
    }

    public DeleteByQueryRequestBuilder setConsistencyLevel(WriteConsistencyLevel consistencyLevel) {
        request.consistencyLevel(consistencyLevel);
        return this;
    }

    /**
     * Should the listener be called on a separate thread if needed.
     */
    public DeleteByQueryRequestBuilder setListenerThreaded(boolean threadedListener) {
        request.listenerThreaded(threadedListener);
        return this;
    }

    @Override
    protected void doExecute(ActionListener<DeleteByQueryResponse> listener) {
        client.deleteByQuery(request, listener);
    }
}
