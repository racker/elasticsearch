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

package org.elasticsearch.index.query;

import org.apache.lucene.search.Filter;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.unit.DistanceUnit;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.cache.filter.support.CacheKeyFilter;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.MapperService;
import org.elasticsearch.index.mapper.geo.GeoPointFieldDataType;
import org.elasticsearch.index.mapper.geo.GeoPointFieldMapper;
import org.elasticsearch.index.search.geo.GeoDistance;
import org.elasticsearch.index.search.geo.GeoDistanceFilter;
import org.elasticsearch.index.search.geo.GeoHashUtils;
import org.elasticsearch.index.search.geo.GeoUtils;

import java.io.IOException;

import static org.elasticsearch.index.query.support.QueryParsers.wrapSmartNameFilter;

/**
 * <pre>
 * {
 *     "name.lat" : 1.1,
 *     "name.lon" : 1.2,
 * }
 * </pre>
 */
public class GeoDistanceFilterParser implements FilterParser {

    public static final String NAME = "geo_distance";

    @Inject
    public GeoDistanceFilterParser() {
    }

    @Override
    public String[] names() {
        return new String[]{NAME, "geoDistance"};
    }

    @Override
    public Filter parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        XContentParser.Token token;

        boolean cache = false;
        CacheKeyFilter.Key cacheKey = null;
        String filterName = null;
        String currentFieldName = null;
        double lat = 0;
        double lon = 0;
        String fieldName = null;
        double distance = 0;
        Object vDistance = null;
        DistanceUnit unit = DistanceUnit.KILOMETERS; // default unit
        GeoDistance geoDistance = GeoDistance.ARC;
        String optimizeBbox = "memory";
        boolean normalizeLon = true;
        boolean normalizeLat = true;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token == XContentParser.Token.START_ARRAY) {
                token = parser.nextToken();
                lon = parser.doubleValue();
                token = parser.nextToken();
                lat = parser.doubleValue();
                while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {

                }
                fieldName = currentFieldName;
            } else if (token == XContentParser.Token.START_OBJECT) {
                // the json in the format of -> field : { lat : 30, lon : 12 }
                String currentName = parser.currentName();
                fieldName = currentFieldName;
                while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
                    if (token == XContentParser.Token.FIELD_NAME) {
                        currentName = parser.currentName();
                    } else if (token.isValue()) {
                        if (currentName.equals(GeoPointFieldMapper.Names.LAT)) {
                            lat = parser.doubleValue();
                        } else if (currentName.equals(GeoPointFieldMapper.Names.LON)) {
                            lon = parser.doubleValue();
                        } else if (currentName.equals(GeoPointFieldMapper.Names.GEOHASH)) {
                            double[] values = GeoHashUtils.decode(parser.text());
                            lat = values[0];
                            lon = values[1];
                        } else {
                            throw new QueryParsingException(parseContext.index(), "[geo_distance] filter does not support [" + currentFieldName + "]");
                        }
                    }
                }
            } else if (token.isValue()) {
                if (currentFieldName.equals("distance")) {
                    if (token == XContentParser.Token.VALUE_STRING) {
                        vDistance = parser.text(); // a String
                    } else {
                        vDistance = parser.numberValue(); // a Number
                    }
                } else if (currentFieldName.equals("unit")) {
                    unit = DistanceUnit.fromString(parser.text());
                } else if (currentFieldName.equals("distance_type") || currentFieldName.equals("distanceType")) {
                    geoDistance = GeoDistance.fromString(parser.text());
                } else if (currentFieldName.endsWith(GeoPointFieldMapper.Names.LAT_SUFFIX)) {
                    lat = parser.doubleValue();
                    fieldName = currentFieldName.substring(0, currentFieldName.length() - GeoPointFieldMapper.Names.LAT_SUFFIX.length());
                } else if (currentFieldName.endsWith(GeoPointFieldMapper.Names.LON_SUFFIX)) {
                    lon = parser.doubleValue();
                    fieldName = currentFieldName.substring(0, currentFieldName.length() - GeoPointFieldMapper.Names.LON_SUFFIX.length());
                } else if (currentFieldName.endsWith(GeoPointFieldMapper.Names.GEOHASH_SUFFIX)) {
                    double[] values = GeoHashUtils.decode(parser.text());
                    lat = values[0];
                    lon = values[1];
                    fieldName = currentFieldName.substring(0, currentFieldName.length() - GeoPointFieldMapper.Names.GEOHASH_SUFFIX.length());
                } else if ("_name".equals(currentFieldName)) {
                    filterName = parser.text();
                } else if ("_cache".equals(currentFieldName)) {
                    cache = parser.booleanValue();
                } else if ("_cache_key".equals(currentFieldName) || "_cacheKey".equals(currentFieldName)) {
                    cacheKey = new CacheKeyFilter.Key(parser.text());
                } else if ("optimize_bbox".equals(currentFieldName) || "optimizeBbox".equals(currentFieldName)) {
                    optimizeBbox = parser.textOrNull();
                } else if ("normalize".equals(currentFieldName)) {
                    normalizeLat = parser.booleanValue();
                    normalizeLon = parser.booleanValue();
                } else {
                    // assume the value is the actual value
                    String value = parser.text();
                    int comma = value.indexOf(',');
                    if (comma != -1) {
                        lat = Double.parseDouble(value.substring(0, comma).trim());
                        lon = Double.parseDouble(value.substring(comma + 1).trim());
                    } else {
                        double[] values = GeoHashUtils.decode(value);
                        lat = values[0];
                        lon = values[1];
                    }
                    fieldName = currentFieldName;
                }
            }
        }

        if (vDistance instanceof Number) {
            distance = unit.toMiles(((Number) vDistance).doubleValue());
        } else {
            distance = DistanceUnit.parse((String) vDistance, unit, DistanceUnit.MILES);
        }
        distance = geoDistance.normalize(distance, DistanceUnit.MILES);

        if (normalizeLat) {
            lat = GeoUtils.normalizeLat(lat);
        }
        if (normalizeLon) {
            lon = GeoUtils.normalizeLon(lon);
        }

        MapperService.SmartNameFieldMappers smartMappers = parseContext.smartFieldMappers(fieldName);
        if (smartMappers == null || !smartMappers.hasMapper()) {
            throw new QueryParsingException(parseContext.index(), "failed to find geo_point field [" + fieldName + "]");
        }
        FieldMapper mapper = smartMappers.mapper();
        if (mapper.fieldDataType() != GeoPointFieldDataType.TYPE) {
            throw new QueryParsingException(parseContext.index(), "field [" + fieldName + "] is not a geo_point field");
        }
        GeoPointFieldMapper geoMapper = ((GeoPointFieldMapper.GeoStringFieldMapper) mapper).geoMapper();
        fieldName = mapper.names().indexName();

        Filter filter = new GeoDistanceFilter(lat, lon, distance, geoDistance, fieldName, geoMapper, parseContext.indexCache().fieldData(), optimizeBbox);
        if (cache) {
            filter = parseContext.cacheFilter(filter, cacheKey);
        }
        filter = wrapSmartNameFilter(filter, smartMappers, parseContext);
        if (filterName != null) {
            parseContext.addNamedFilter(filterName, filter);
        }
        return filter;
    }
}
