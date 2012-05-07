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

package org.elasticsearch.test.integration.gateway.local;

import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthStatus;
import org.elasticsearch.action.admin.indices.status.IndexShardStatus;
import org.elasticsearch.action.admin.indices.status.IndicesStatusResponse;
import org.elasticsearch.action.admin.indices.status.ShardStatus;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.gateway.Gateway;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.internal.InternalNode;
import org.elasticsearch.test.integration.AbstractNodesTests;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static org.elasticsearch.client.Requests.clusterHealthRequest;
import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;
import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 *
 */
public class SimpleRecoveryLocalGatewayTests extends AbstractNodesTests {

    @AfterMethod
    public void cleanAndCloseNodes() throws Exception {
        for (int i = 0; i < 10; i++) {
            if (node("node" + i) != null) {
                node("node" + i).stop();
                // since we store (by default) the index snapshot under the gateway, resetting it will reset the index data as well
                ((InternalNode) node("node" + i)).injector().getInstance(Gateway.class).reset();
            }
        }
        closeAllNodes();
    }

    @Test
    public void testX() throws Exception {
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();

        Node node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).build());
        node1.client().prepareIndex("test", "type1", "10990239").setSource(jsonBuilder().startObject()
                .field("_id", "10990239")
                .startArray("appAccountIds").value(14).value(179).endArray().endObject()).execute().actionGet();
        node1.client().prepareIndex("test", "type1", "10990473").setSource(jsonBuilder().startObject()
                .field("_id", "10990473")
                .startArray("appAccountIds").value(14).endArray().endObject()).execute().actionGet();
        node1.client().prepareIndex("test", "type1", "10990513").setSource(jsonBuilder().startObject()
                .field("_id", "10990513")
                .startArray("appAccountIds").value(14).value(179).endArray().endObject()).execute().actionGet();
        node1.client().prepareIndex("test", "type1", "10990695").setSource(jsonBuilder().startObject()
                .field("_id", "10990695")
                .startArray("appAccountIds").value(14).endArray().endObject()).execute().actionGet();
        node1.client().prepareIndex("test", "type1", "11026351").setSource(jsonBuilder().startObject()
                .field("_id", "11026351")
                .startArray("appAccountIds").value(14).endArray().endObject()).execute().actionGet();

        node1.client().admin().indices().prepareRefresh().execute().actionGet();
        assertThat(node1.client().prepareCount().setQuery(termQuery("appAccountIds", 179)).execute().actionGet().count(), equalTo(2l));

        closeNode("node1");
        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        ClusterHealthResponse clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForYellowStatus().waitForActiveShards(1)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.YELLOW));

        assertThat(node1.client().prepareCount().setQuery(termQuery("appAccountIds", 179)).execute().actionGet().count(), equalTo(2l));

        closeNode("node1");
        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForYellowStatus().waitForActiveShards(1)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.YELLOW));

        assertThat(node1.client().prepareCount().setQuery(termQuery("appAccountIds", 179)).execute().actionGet().count(), equalTo(2l));
    }

    @Test
    public void testSingleNodeNoFlush() throws Exception {
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();

        Node node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).build());
        for (int i = 0; i < 100; i++) {
            node1.client().prepareIndex("test", "type1", "1").setSource(jsonBuilder().startObject().field("_id", "1").field("field", "value1").startArray("num").value(14).value(179).endArray().endObject()).execute().actionGet();
            node1.client().prepareIndex("test", "type1", "2").setSource(jsonBuilder().startObject().field("_id", "2").field("field", "value2").startArray("num").value(14).endArray().endObject()).execute().actionGet();
        }

        node1.client().admin().indices().prepareRefresh().execute().actionGet();
        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("field", "value1")).execute().actionGet().count(), equalTo(1l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("field", "value2")).execute().actionGet().count(), equalTo(1l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("num", 179)).execute().actionGet().count(), equalTo(1l));
        }

        closeNode("node1");
        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        ClusterHealthResponse clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForYellowStatus().waitForActiveShards(1)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.YELLOW));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("field", "value1")).execute().actionGet().count(), equalTo(1l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("field", "value2")).execute().actionGet().count(), equalTo(1l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("num", 179)).execute().actionGet().count(), equalTo(1l));
        }

        closeNode("node1");
        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForYellowStatus().waitForActiveShards(1)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.YELLOW));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("field", "value1")).execute().actionGet().count(), equalTo(1l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("field", "value2")).execute().actionGet().count(), equalTo(1l));
            assertThat(node1.client().prepareCount().setQuery(termQuery("num", 179)).execute().actionGet().count(), equalTo(1l));
        }
    }


    @Test
    public void testSingleNodeWithFlush() throws Exception {
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();

        Node node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).build());
        node1.client().prepareIndex("test", "type1", "1").setSource(jsonBuilder().startObject().field("field", "value1").endObject()).execute().actionGet();
        node1.client().admin().indices().prepareFlush().execute().actionGet();
        node1.client().prepareIndex("test", "type1", "2").setSource(jsonBuilder().startObject().field("field", "value2").endObject()).execute().actionGet();
        node1.client().admin().indices().prepareRefresh().execute().actionGet();

        assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));

        closeNode("node1");
        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        ClusterHealthResponse clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForYellowStatus().waitForActiveShards(1)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.YELLOW));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
        }

        closeNode("node1");
        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForYellowStatus().waitForActiveShards(1)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.YELLOW));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
        }
    }

    @Test
    public void testTwoNodeFirstNodeCleared() throws Exception {
        // clean two nodes
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        buildNode("node2", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();

        Node node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).build());
        Node node2 = startNode("node2", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).build());

        node1.client().prepareIndex("test", "type1", "1").setSource(jsonBuilder().startObject().field("field", "value1").endObject()).execute().actionGet();
        node1.client().admin().indices().prepareFlush().execute().actionGet();
        node1.client().prepareIndex("test", "type1", "2").setSource(jsonBuilder().startObject().field("field", "value2").endObject()).execute().actionGet();
        node1.client().admin().indices().prepareRefresh().execute().actionGet();

        logger.info("Running Cluster Health (wait for the shards to startup)");
        ClusterHealthResponse clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForActiveShards(2)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
        }

        logger.info("--> closing nodes");
        closeNode("node1");
        closeNode("node2");

        logger.info("--> cleaning node1 gateway");
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();

        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("gateway.recover_after_nodes", 2).build());
        node2 = startNode("node2", settingsBuilder().put("gateway.type", "local").put("gateway.recover_after_nodes", 2).build());

        logger.info("Running Cluster Health (wait for the shards to startup)");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForActiveShards(2)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
        }
    }

    @Test
    public void testLatestVersionLoaded() throws Exception {
        // clean two nodes
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        buildNode("node2", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();

        Node node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).put("gateway.recover_after_nodes", 2).build());
        Node node2 = startNode("node2", settingsBuilder().put("gateway.type", "local").put("index.number_of_shards", 1).put("gateway.recover_after_nodes", 2).build());

        node1.client().prepareIndex("test", "type1", "1").setSource(jsonBuilder().startObject().field("field", "value1").endObject()).execute().actionGet();
        node1.client().admin().indices().prepareFlush().execute().actionGet();
        node1.client().prepareIndex("test", "type1", "2").setSource(jsonBuilder().startObject().field("field", "value2").endObject()).execute().actionGet();
        node1.client().admin().indices().prepareRefresh().execute().actionGet();

        logger.info("--> running cluster_health (wait for the shards to startup)");
        ClusterHealthResponse clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForActiveShards(2)).actionGet();
        logger.info("--> done cluster_health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(2l));
        }

        logger.info("--> closing first node, and indexing more data to the second node");
        closeNode("node1");

        node2.client().prepareIndex("test", "type1", "3").setSource(jsonBuilder().startObject().field("field", "value3").endObject()).execute().actionGet();
        node2.client().admin().indices().prepareRefresh().execute().actionGet();

        for (int i = 0; i < 10; i++) {
            assertThat(node2.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(3l));
        }

        logger.info("--> add some metadata, additional type and template");
        node2.client().admin().indices().preparePutMapping("test").setType("type2")
                .setSource(jsonBuilder().startObject().startObject("type1").startObject("_source").field("enabled", false).endObject().endObject().endObject())
                .execute().actionGet();
        node2.client().admin().indices().preparePutTemplate("template_1")
                .setTemplate("te*")
                .setOrder(0)
                .addMapping("type1", XContentFactory.jsonBuilder().startObject().startObject("type1").startObject("properties")
                        .startObject("field1").field("type", "string").field("store", "yes").endObject()
                        .startObject("field2").field("type", "string").field("store", "yes").field("index", "not_analyzed").endObject()
                        .endObject().endObject().endObject())
                .execute().actionGet();
        node2.client().admin().indices().prepareAliases().addAlias("test", "test_alias", FilterBuilders.termFilter("field", "value")).execute().actionGet();


        logger.info("--> closing the second node");
        closeNode("node2");

        logger.info("--> starting two nodes back, verifying we got the latest version");

        node1 = startNode("node1", settingsBuilder().put("gateway.type", "local").put("gateway.recover_after_nodes", 2).build());
        node2 = startNode("node2", settingsBuilder().put("gateway.type", "local").put("gateway.recover_after_nodes", 2).build());

        logger.info("--> running cluster_health (wait for the shards to startup)");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForActiveShards(2)).actionGet();
        logger.info("--> done cluster_health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        for (int i = 0; i < 10; i++) {
            assertThat(node1.client().prepareCount().setQuery(matchAllQuery()).execute().actionGet().count(), equalTo(3l));
        }

        ClusterState state = node1.client().admin().cluster().prepareState().execute().actionGet().state();
        assertThat(state.metaData().index("test").mapping("type2"), notNullValue());
        assertThat(state.metaData().templates().get("template_1").template(), equalTo("te*"));
        assertThat(state.metaData().index("test").aliases().get("test_alias"), notNullValue());
        assertThat(state.metaData().index("test").aliases().get("test_alias").filter(), notNullValue());
    }

    @Test
    public void testReusePeerRecovery() throws Exception {
        buildNode("node1", settingsBuilder().put("gateway.type", "local").build());
        buildNode("node2", settingsBuilder().put("gateway.type", "local").build());
        buildNode("node3", settingsBuilder().put("gateway.type", "local").build());
        buildNode("node4", settingsBuilder().put("gateway.type", "local").build());
        cleanAndCloseNodes();


        ImmutableSettings.Builder settings = ImmutableSettings.settingsBuilder()
                .put("action.admin.cluster.node.shutdown.delay", "10ms")
                .put("gateway.recover_after_nodes", 4)
                .put("gateway.type", "local");
        startNode("node1", settings);
        startNode("node2", settings);
        startNode("node3", settings);
        startNode("node4", settings);

        logger.info("--> indexing docs");
        for (int i = 0; i < 1000; i++) {
            client("node1").prepareIndex("test", "type").setSource("field", "value").execute().actionGet();
            if ((i % 200) == 0) {
                client("node1").admin().indices().prepareFlush().execute().actionGet();
            }
        }
        logger.info("Running Cluster Health");
        ClusterHealthResponse clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForRelocatingShards(0)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        logger.info("--> shutting down the nodes");
        client("node1").admin().cluster().prepareNodesShutdown().setDelay("10ms").setExit(false).execute().actionGet();
        Thread.sleep(2000);
        logger.info("--> start the nodes back up");
        startNode("node1", settings);
        startNode("node2", settings);
        startNode("node3", settings);
        startNode("node4", settings);

        logger.info("Running Cluster Health");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForActiveShards(10)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        logger.info("--> shutting down the nodes");
        client("node1").admin().cluster().prepareNodesShutdown().setDelay("10ms").setExit(false).execute().actionGet();
        Thread.sleep(2000);

        logger.info("--> start the nodes back up");
        startNode("node1", settings);
        startNode("node2", settings);
        startNode("node3", settings);
        startNode("node4", settings);

        logger.info("Running Cluster Health");
        clusterHealth = client("node1").admin().cluster().health(clusterHealthRequest().waitForGreenStatus().waitForActiveShards(10)).actionGet();
        logger.info("Done Cluster Health, status " + clusterHealth.status());
        assertThat(clusterHealth.timedOut(), equalTo(false));
        assertThat(clusterHealth.status(), equalTo(ClusterHealthStatus.GREEN));

        IndicesStatusResponse statusResponse = client("node1").admin().indices().prepareStatus("test").setRecovery(true).execute().actionGet();
        for (IndexShardStatus indexShardStatus : statusResponse.index("test")) {
            for (ShardStatus shardStatus : indexShardStatus) {
                if (!shardStatus.shardRouting().primary()) {
                    logger.info("--> shard {}, recovered {}, reuse {}", shardStatus.shardId(), shardStatus.peerRecoveryStatus().recoveredIndexSize(), shardStatus.peerRecoveryStatus().reusedIndexSize());
                    assertThat(shardStatus.peerRecoveryStatus().recoveredIndexSize().bytes(), greaterThan(0l));
                    assertThat(shardStatus.peerRecoveryStatus().reusedIndexSize().bytes(), greaterThan(0l));
                    assertThat(shardStatus.peerRecoveryStatus().reusedIndexSize().bytes(), greaterThan(shardStatus.peerRecoveryStatus().recoveredIndexSize().bytes()));
                }
            }
        }
    }

    @Test
    public void testRecoveryDifferentNodeOrderStartup() throws Exception {
        // we need different data paths so we make sure we start the second node fresh
        buildNode("node1", settingsBuilder().put("gateway.type", "local").put("path.data", "data/data1").build());
        buildNode("node2", settingsBuilder().put("gateway.type", "local").put("path.data", "data/data2").build());
        cleanAndCloseNodes();

        startNode("node1", settingsBuilder().put("gateway.type", "local").put("path.data", "data/data1").build());

        client("node1").prepareIndex("test", "type1", "1").setSource("field", "value").execute().actionGet();

        startNode("node2", settingsBuilder().put("gateway.type", "local").put("path.data", "data/data2").build());

        ClusterHealthResponse health = client("node2").admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        assertThat(health.timedOut(), equalTo(false));

        closeNode("node1");
        closeNode("node2");

        startNode("node2", settingsBuilder().put("gateway.type", "local").put("path.data", "data/data2").build());

        health = client("node2").admin().cluster().prepareHealth().setWaitForYellowStatus().execute().actionGet();
        assertThat(health.timedOut(), equalTo(false));

        assertThat(client("node2").admin().indices().prepareExists("test").execute().actionGet().exists(), equalTo(true));
        assertThat(client("node2").prepareCount("test").setQuery(QueryBuilders.matchAllQuery()).execute().actionGet().count(), equalTo(1l));
    }
}
