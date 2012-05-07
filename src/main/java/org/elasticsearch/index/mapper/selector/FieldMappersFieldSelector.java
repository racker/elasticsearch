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

package org.elasticsearch.index.mapper.selector;

import org.apache.lucene.document.FieldSelectorResult;
import org.elasticsearch.common.lucene.document.ResetFieldSelector;
import org.elasticsearch.index.mapper.FieldMapper;
import org.elasticsearch.index.mapper.FieldMappers;

import java.util.HashSet;

/**
 *
 */
public class FieldMappersFieldSelector implements ResetFieldSelector {

    private final HashSet<String> names = new HashSet<String>();

    public void add(String fieldName) {
        names.add(fieldName);
    }

    public void add(FieldMappers fieldMappers) {
        for (FieldMapper fieldMapper : fieldMappers) {
            names.add(fieldMapper.names().indexName());
        }
    }

    @Override
    public FieldSelectorResult accept(String fieldName) {
        if (names.contains(fieldName)) {
            return FieldSelectorResult.LOAD;
        }
        return FieldSelectorResult.NO_LOAD;
    }

    @Override
    public void reset() {
    }
}
