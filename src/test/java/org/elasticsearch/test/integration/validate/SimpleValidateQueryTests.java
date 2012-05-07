/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.test.integration.validate;

import org.elasticsearch.action.admin.indices.validate.query.ValidateQueryResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.search.geo.GeoDistance;
import org.elasticsearch.test.integration.AbstractNodesTests;
import org.hamcrest.Matcher;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 *
 */
public class SimpleValidateQueryTests extends AbstractNodesTests {

        private Client client;

        @BeforeClass
        public void createNodes() throws Exception {
            startNode("node1");
            startNode("node2");
            client = getClient();
        }

        @AfterClass
        public void closeNodes() {
            client.close();
            closeAllNodes();
        }

        protected Client getClient() {
            return client("node1");
        }

        @Test
        public void simpleValidateQuery() throws Exception {
            client.admin().indices().prepareDelete().execute().actionGet();

            client.admin().indices().prepareCreate("test").setSettings(ImmutableSettings.settingsBuilder().put("index.number_of_shards", 1)).execute().actionGet();
            client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
            client.admin().indices().preparePutMapping("test").setType("type1")
                    .setSource(XContentFactory.jsonBuilder().startObject().startObject("type1").startObject("properties")
                            .startObject("foo").field("type", "string").endObject()
                            .startObject("bar").field("type", "integer").endObject()
                            .endObject().endObject().endObject())
                    .execute().actionGet();

            client.admin().indices().prepareRefresh().execute().actionGet();

            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery("foo".getBytes()).execute().actionGet().valid(), equalTo(false));
            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery(QueryBuilders.queryString("_id:1")).execute().actionGet().valid(), equalTo(true));
            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery(QueryBuilders.queryString("_i:d:1")).execute().actionGet().valid(), equalTo(false));

            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery(QueryBuilders.queryString("foo:1")).execute().actionGet().valid(), equalTo(true));
            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery(QueryBuilders.queryString("bar:hey")).execute().actionGet().valid(), equalTo(false));

            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery(QueryBuilders.queryString("nonexistent:hello")).execute().actionGet().valid(), equalTo(true));

            assertThat(client.admin().indices().prepareValidateQuery("test").setQuery(QueryBuilders.queryString("foo:1 AND")).execute().actionGet().valid(), equalTo(false));
        }

    @Test
    public void explainValidateQuery() throws Exception {
        client.admin().indices().prepareDelete().execute().actionGet();

        client.admin().indices().prepareCreate("test").setSettings(ImmutableSettings.settingsBuilder().put("index.number_of_shards", 1)).execute().actionGet();
        client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();
        client.admin().indices().preparePutMapping("test").setType("type1")
                .setSource(XContentFactory.jsonBuilder().startObject().startObject("type1").startObject("properties")
                        .startObject("foo").field("type", "string").endObject()
                        .startObject("bar").field("type", "integer").endObject()
                        .startObject("baz").field("type", "string").field("analyzer", "snowball").endObject()
                        .startObject("pin").startObject("properties").startObject("location").field("type", "geo_point").endObject().endObject().endObject()
                        .endObject().endObject().endObject())
                .execute().actionGet();

        client.admin().indices().prepareRefresh().execute().actionGet();


        ValidateQueryResponse response;
        response = client.admin().indices().prepareValidateQuery("test")
                .setQuery("foo".getBytes())
                .setExplain(true)
                .execute().actionGet();
        assertThat(response.valid(), equalTo(false));
        assertThat(response.queryExplanations().size(), equalTo(1));
        assertThat(response.queryExplanations().get(0).error(), containsString("Failed to parse"));
        assertThat(response.queryExplanations().get(0).explanation(), nullValue());

        assertExplanation(QueryBuilders.queryString("_id:1"), equalTo("ConstantScore(UidFilter([_uid:type1#1]))"));

        assertExplanation(QueryBuilders.idsQuery("type1").addIds("1").addIds("2"),
                equalTo("ConstantScore(UidFilter([_uid:type1#1, _uid:type1#2]))"));

        assertExplanation(QueryBuilders.queryString("foo"), equalTo("_all:foo"));

        assertExplanation(QueryBuilders.filteredQuery(
                QueryBuilders.termQuery("foo", "1"),
                FilterBuilders.orFilter(
                        FilterBuilders.termFilter("bar", "2"),
                        FilterBuilders.termFilter("baz", "3")
                )
        ), equalTo("filtered(foo:1)->cache(bar:[2 TO 2]) cache(baz:3)"));

        assertExplanation(QueryBuilders.filteredQuery(
                QueryBuilders.termQuery("foo", "1"),
                FilterBuilders.orFilter(
                        FilterBuilders.termFilter("bar", "2")
                )
        ), equalTo("filtered(foo:1)->cache(bar:[2 TO 2])"));

        assertExplanation(QueryBuilders.filteredQuery(
                QueryBuilders.matchAllQuery(),
                FilterBuilders.geoPolygonFilter("pin.location")
                        .addPoint(40, -70)
                        .addPoint(30, -80)
                        .addPoint(20, -90)
        ), equalTo("ConstantScore(NotDeleted(GeoPolygonFilter(pin.location, [[40.0, -70.0], [30.0, -80.0], [20.0, -90.0]])))"));

        assertExplanation(QueryBuilders.constantScoreQuery(FilterBuilders.geoBoundingBoxFilter("pin.location")
                .topLeft(40, -80)
                .bottomRight(20, -70)
        ), equalTo("ConstantScore(NotDeleted(GeoBoundingBoxFilter(pin.location, [40.0, -80.0], [20.0, -70.0])))"));

        assertExplanation(QueryBuilders.constantScoreQuery(FilterBuilders.geoDistanceFilter("pin.location")
                .lat(10).lon(20).distance(15, DistanceUnit.MILES).geoDistance(GeoDistance.PLANE)
        ), equalTo("ConstantScore(NotDeleted(GeoDistanceFilter(pin.location, PLANE, 15.0, 10.0, 20.0)))"));

        assertExplanation(QueryBuilders.constantScoreQuery(FilterBuilders.geoDistanceFilter("pin.location")
                .lat(10).lon(20).distance(15, DistanceUnit.MILES).geoDistance(GeoDistance.PLANE)
        ), equalTo("ConstantScore(NotDeleted(GeoDistanceFilter(pin.location, PLANE, 15.0, 10.0, 20.0)))"));

        assertExplanation(QueryBuilders.constantScoreQuery(FilterBuilders.geoDistanceRangeFilter("pin.location")
                .lat(10).lon(20).from("15miles").to("25miles").geoDistance(GeoDistance.PLANE)
        ), equalTo("ConstantScore(NotDeleted(GeoDistanceRangeFilter(pin.location, PLANE, [15.0 - 25.0], 10.0, 20.0)))"));

        assertExplanation(QueryBuilders.filteredQuery(
                QueryBuilders.termQuery("foo", "1"),
                FilterBuilders.andFilter(
                        FilterBuilders.termFilter("bar", "2"),
                        FilterBuilders.termFilter("baz", "3")
                )
        ), equalTo("filtered(foo:1)->+cache(bar:[2 TO 2]) +cache(baz:3)"));

        assertExplanation(QueryBuilders.constantScoreQuery(FilterBuilders.termsFilter("foo", "1", "2", "3")),
                equalTo("ConstantScore(NotDeleted(cache(foo:1 foo:2 foo:3)))"));

        assertExplanation(QueryBuilders.constantScoreQuery(FilterBuilders.notFilter(FilterBuilders.termFilter("foo", "bar"))),
                equalTo("ConstantScore(NotDeleted(NotFilter(cache(foo:bar))))"));

    }

    private void assertExplanation(QueryBuilder queryBuilder, Matcher<String> matcher) {
        ValidateQueryResponse response = client.admin().indices().prepareValidateQuery("test")
                .setQuery(queryBuilder)
                .setExplain(true)
                .execute().actionGet();
        assertThat(response.queryExplanations().size(), equalTo(1));
        assertThat(response.queryExplanations().get(0).error(), nullValue());
        assertThat(response.queryExplanations().get(0).explanation(), matcher);
        assertThat(response.valid(), equalTo(true));
    }

}
