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

package org.elasticsearch.rest.action.admin.indices.stats;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.indices.stats.IndicesStats;
import org.elasticsearch.action.admin.indices.stats.IndicesStatsRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.*;
import org.elasticsearch.rest.action.support.RestXContentBuilder;

import java.io.IOException;

import static org.elasticsearch.rest.RestRequest.Method.GET;
import static org.elasticsearch.rest.RestStatus.OK;
import static org.elasticsearch.rest.action.support.RestActions.*;

/**
 */
public class RestIndicesStatsAction extends BaseRestHandler {

    @Inject
    public RestIndicesStatsAction(Settings settings, Client client, RestController controller) {
        super(settings, client);
        controller.registerHandler(GET, "/_stats", this);
        controller.registerHandler(GET, "/{index}/_stats", this);

        controller.registerHandler(GET, "_stats/docs", new RestDocsStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/docs", new RestDocsStatsHandler());

        controller.registerHandler(GET, "/_stats/store", new RestStoreStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/store", new RestStoreStatsHandler());

        controller.registerHandler(GET, "/_stats/indexing", new RestIndexingStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/indexing", new RestIndexingStatsHandler());
        controller.registerHandler(GET, "/_stats/indexing/{indexingTypes1}", new RestIndexingStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/indexing/{indexingTypes2}", new RestIndexingStatsHandler());

        controller.registerHandler(GET, "/_stats/search", new RestSearchStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/search", new RestSearchStatsHandler());
        controller.registerHandler(GET, "/_stats/search/{searchGroupsStats1}", new RestSearchStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/search/{searchGroupsStats2}", new RestSearchStatsHandler());

        controller.registerHandler(GET, "/_stats/get", new RestGetStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/get", new RestGetStatsHandler());

        controller.registerHandler(GET, "/_stats/refresh", new RestRefreshStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/refresh", new RestRefreshStatsHandler());

        controller.registerHandler(GET, "/_stats/merge", new RestMergeStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/merge", new RestMergeStatsHandler());

        controller.registerHandler(GET, "/_stats/flush", new RestFlushStatsHandler());
        controller.registerHandler(GET, "/{index}/_stats/flush", new RestFlushStatsHandler());
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel) {
        IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
        boolean clear = request.paramAsBoolean("clear", false);
        if (clear) {
            indicesStatsRequest.clear();
        }
        boolean all = request.paramAsBoolean("all", false);
        if (all) {
            indicesStatsRequest.all();
        }
        indicesStatsRequest.indices(splitIndices(request.param("index")));
        indicesStatsRequest.types(splitTypes(request.param("types")));
        if (request.hasParam("groups")) {
            indicesStatsRequest.groups(Strings.splitStringByCommaToArray(request.param("groups")));
        }
        indicesStatsRequest.docs(request.paramAsBoolean("docs", indicesStatsRequest.docs()));
        indicesStatsRequest.store(request.paramAsBoolean("store", indicesStatsRequest.store()));
        indicesStatsRequest.indexing(request.paramAsBoolean("indexing", indicesStatsRequest.indexing()));
        indicesStatsRequest.search(request.paramAsBoolean("search", indicesStatsRequest.search()));
        indicesStatsRequest.get(request.paramAsBoolean("get", indicesStatsRequest.get()));
        indicesStatsRequest.merge(request.paramAsBoolean("merge", indicesStatsRequest.merge()));
        indicesStatsRequest.refresh(request.paramAsBoolean("refresh", indicesStatsRequest.refresh()));
        indicesStatsRequest.flush(request.paramAsBoolean("flush", indicesStatsRequest.flush()));

        client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
            @Override
            public void onResponse(IndicesStats response) {
                try {
                    XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                    builder.startObject();
                    builder.field("ok", true);
                    buildBroadcastShardsHeader(builder, response);
                    response.toXContent(builder, request);
                    builder.endObject();
                    channel.sendResponse(new XContentRestResponse(request, OK, builder));
                } catch (Exception e) {
                    onFailure(e);
                }
            }

            @Override
            public void onFailure(Throwable e) {
                try {
                    channel.sendResponse(new XContentThrowableRestResponse(request, e));
                } catch (IOException e1) {
                    logger.error("Failed to send failure response", e1);
                }
            }
        });
    }

    class RestDocsStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().docs(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            indicesStatsRequest.types(splitTypes(request.param("types")));

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestStoreStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().store(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            indicesStatsRequest.types(splitTypes(request.param("types")));

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestIndexingStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().indexing(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            if (request.hasParam("types")) {
                indicesStatsRequest.types(splitTypes(request.param("types")));
            } else if (request.hasParam("indexingTypes1")) {
                indicesStatsRequest.types(splitTypes(request.param("indexingTypes1")));
            } else if (request.hasParam("indexingTypes2")) {
                indicesStatsRequest.types(splitTypes(request.param("indexingTypes2")));
            }

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestSearchStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().search(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            if (request.hasParam("groups")) {
                indicesStatsRequest.groups(Strings.splitStringByCommaToArray(request.param("groups")));
            } else if (request.hasParam("searchGroupsStats1")) {
                indicesStatsRequest.groups(Strings.splitStringByCommaToArray(request.param("searchGroupsStats1")));
            } else if (request.hasParam("searchGroupsStats2")) {
                indicesStatsRequest.groups(Strings.splitStringByCommaToArray(request.param("searchGroupsStats2")));
            }

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestGetStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().get(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestMergeStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().merge(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            indicesStatsRequest.types(splitTypes(request.param("types")));

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestFlushStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().flush(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            indicesStatsRequest.types(splitTypes(request.param("types")));

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }

    class RestRefreshStatsHandler implements RestHandler {

        @Override
        public void handleRequest(final RestRequest request, final RestChannel channel) {
            IndicesStatsRequest indicesStatsRequest = new IndicesStatsRequest();
            indicesStatsRequest.clear().refresh(true);
            indicesStatsRequest.indices(splitIndices(request.param("index")));
            indicesStatsRequest.types(splitTypes(request.param("types")));

            client.admin().indices().stats(indicesStatsRequest, new ActionListener<IndicesStats>() {
                @Override
                public void onResponse(IndicesStats response) {
                    try {
                        XContentBuilder builder = RestXContentBuilder.restContentBuilder(request);
                        builder.startObject();
                        builder.field("ok", true);
                        buildBroadcastShardsHeader(builder, response);
                        response.toXContent(builder, request);
                        builder.endObject();
                        channel.sendResponse(new XContentRestResponse(request, OK, builder));
                    } catch (Exception e) {
                        onFailure(e);
                    }
                }

                @Override
                public void onFailure(Throwable e) {
                    try {
                        channel.sendResponse(new XContentThrowableRestResponse(request, e));
                    } catch (IOException e1) {
                        logger.error("Failed to send failure response", e1);
                    }
                }
            });
        }
    }
}
