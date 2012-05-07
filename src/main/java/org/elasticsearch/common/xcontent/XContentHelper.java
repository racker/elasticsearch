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

package org.elasticsearch.common.xcontent;

import com.google.common.base.Charsets;
import org.elasticsearch.ElasticSearchParseException;
import org.elasticsearch.common.collect.Tuple;
import org.elasticsearch.common.compress.lzf.LZF;
import org.elasticsearch.common.io.stream.BytesStreamInput;
import org.elasticsearch.common.io.stream.CachedStreamInput;
import org.elasticsearch.common.io.stream.LZFStreamInput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class XContentHelper {

    public static XContentParser createParser(byte[] data, int offset, int length) throws IOException {
        if (LZF.isCompressed(data, offset, length)) {
            BytesStreamInput siBytes = new BytesStreamInput(data, offset, length, false);
            LZFStreamInput siLzf = CachedStreamInput.cachedLzf(siBytes);
            XContentType contentType = XContentFactory.xContentType(siLzf);
            siLzf.resetToBufferStart();
            return XContentFactory.xContent(contentType).createParser(siLzf);
        } else {
            return XContentFactory.xContent(data, offset, length).createParser(data, offset, length);
        }
    }

    public static Tuple<XContentType, Map<String, Object>> convertToMap(byte[] data, int offset, int length, boolean ordered) throws ElasticSearchParseException {
        try {
            XContentParser parser;
            XContentType contentType;
            if (LZF.isCompressed(data, offset, length)) {
                BytesStreamInput siBytes = new BytesStreamInput(data, offset, length, false);
                LZFStreamInput siLzf = CachedStreamInput.cachedLzf(siBytes);
                contentType = XContentFactory.xContentType(siLzf);
                siLzf.resetToBufferStart();
                parser = XContentFactory.xContent(contentType).createParser(siLzf);
            } else {
                contentType = XContentFactory.xContentType(data, offset, length);
                parser = XContentFactory.xContent(contentType).createParser(data, offset, length);
            }
            if (ordered) {
                return Tuple.create(contentType, parser.mapOrderedAndClose());
            } else {
                return Tuple.create(contentType, parser.mapAndClose());
            }
        } catch (IOException e) {
            throw new ElasticSearchParseException("Failed to parse content to map", e);
        }
    }

    public static String convertToJson(byte[] data, int offset, int length, boolean reformatJson) throws IOException {
        XContentType xContentType = XContentFactory.xContentType(data, offset, length);
        if (xContentType == XContentType.JSON && reformatJson) {
            return new String(data, offset, length, Charsets.UTF_8);
        }
        XContentParser parser = null;
        try {
            parser = XContentFactory.xContent(xContentType).createParser(data, offset, length);
            parser.nextToken();
            XContentBuilder builder = XContentFactory.jsonBuilder();
            builder.copyCurrentStructure(parser);
            return builder.string();
        } finally {
            if (parser != null) {
                parser.close();
            }
        }
    }

    /**
     * Merges the defaults provided as the second parameter into the content of the first. Only does recursive merge
     * for inner maps.
     */
    @SuppressWarnings({"unchecked"})
    public static void mergeDefaults(Map<String, Object> content, Map<String, Object> defaults) {
        for (Map.Entry<String, Object> defaultEntry : defaults.entrySet()) {
            if (!content.containsKey(defaultEntry.getKey())) {
                // copy it over, it does not exists in the content
                content.put(defaultEntry.getKey(), defaultEntry.getValue());
            } else {
                // in the content and in the default, only merge compound ones (maps)
                if (content.get(defaultEntry.getKey()) instanceof Map && defaultEntry.getValue() instanceof Map) {
                    mergeDefaults((Map<String, Object>) content.get(defaultEntry.getKey()), (Map<String, Object>) defaultEntry.getValue());
                } else if (content.get(defaultEntry.getKey()) instanceof List && defaultEntry.getValue() instanceof List) {
                    // if both are lists, simply combine them, first the defaults, then the content
                    List mergedList = new ArrayList();
                    mergedList.addAll((Collection) defaultEntry.getValue());
                    mergedList.addAll((Collection) content.get(defaultEntry.getKey()));
                    content.put(defaultEntry.getKey(), mergedList);
                }
            }
        }
    }

    public static void copyCurrentStructure(XContentGenerator generator, XContentParser parser) throws IOException {
        XContentParser.Token t = parser.currentToken();

        // Let's handle field-name separately first
        if (t == XContentParser.Token.FIELD_NAME) {
            generator.writeFieldName(parser.currentName());
            t = parser.nextToken();
            // fall-through to copy the associated value
        }

        switch (t) {
            case START_ARRAY:
                generator.writeStartArray();
                while (parser.nextToken() != XContentParser.Token.END_ARRAY) {
                    copyCurrentStructure(generator, parser);
                }
                generator.writeEndArray();
                break;
            case START_OBJECT:
                generator.writeStartObject();
                while (parser.nextToken() != XContentParser.Token.END_OBJECT) {
                    copyCurrentStructure(generator, parser);
                }
                generator.writeEndObject();
                break;
            default: // others are simple:
                copyCurrentEvent(generator, parser);
        }
    }

    public static void copyCurrentEvent(XContentGenerator generator, XContentParser parser) throws IOException {
        switch (parser.currentToken()) {
            case START_OBJECT:
                generator.writeStartObject();
                break;
            case END_OBJECT:
                generator.writeEndObject();
                break;
            case START_ARRAY:
                generator.writeStartArray();
                break;
            case END_ARRAY:
                generator.writeEndArray();
                break;
            case FIELD_NAME:
                generator.writeFieldName(parser.currentName());
                break;
            case VALUE_STRING:
                if (parser.hasTextCharacters()) {
                    generator.writeString(parser.textCharacters(), parser.textOffset(), parser.textLength());
                } else {
                    generator.writeString(parser.text());
                }
                break;
            case VALUE_NUMBER:
                switch (parser.numberType()) {
                    case INT:
                        generator.writeNumber(parser.intValue());
                        break;
                    case LONG:
                        generator.writeNumber(parser.longValue());
                        break;
                    case FLOAT:
                        generator.writeNumber(parser.floatValue());
                        break;
                    case DOUBLE:
                        generator.writeNumber(parser.doubleValue());
                        break;
                }
                break;
            case VALUE_BOOLEAN:
                generator.writeBoolean(parser.booleanValue());
                break;
            case VALUE_NULL:
                generator.writeNull();
                break;
            case VALUE_EMBEDDED_OBJECT:
                generator.writeBinary(parser.binaryValue());
        }
    }

}
