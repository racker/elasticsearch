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

import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;

import java.io.IOException;

/**
 * Delete by query response executed on a specific index.
 *
 *
 */
public class IndexDeleteByQueryResponse implements ActionResponse, Streamable {

    private String index;

    private int successfulShards;

    private int failedShards;

    IndexDeleteByQueryResponse(String index, int successfulShards, int failedShards) {
        this.index = index;
        this.successfulShards = successfulShards;
        this.failedShards = failedShards;
    }

    IndexDeleteByQueryResponse() {

    }

    /**
     * The index the delete by query operation was executed against.
     */
    public String index() {
        return this.index;
    }

    /**
     * The index the delete by query operation was executed against.
     */
    public String getIndex() {
        return index;
    }

    /**
     * The total number of shards the delete by query was executed on.
     */
    public int totalShards() {
        return failedShards + successfulShards;
    }

    /**
     * The total number of shards the delete by query was executed on.
     */
    public int getTotalShards() {
        return totalShards();
    }

    /**
     * The successful number of shards the delete by query was executed on.
     */
    public int successfulShards() {
        return successfulShards;
    }

    /**
     * The successful number of shards the delete by query was executed on.
     */
    public int getSuccessfulShards() {
        return successfulShards;
    }

    /**
     * The failed number of shards the delete by query was executed on.
     */
    public int failedShards() {
        return failedShards;
    }

    /**
     * The failed number of shards the delete by query was executed on.
     */
    public int getFailedShards() {
        return failedShards;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        index = in.readUTF();
        successfulShards = in.readVInt();
        failedShards = in.readVInt();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(index);
        out.writeVInt(successfulShards);
        out.writeVInt(failedShards);
    }
}