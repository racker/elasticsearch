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

package org.elasticsearch.search.highlight;

import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Streamable;

import java.io.IOException;
import java.util.Arrays;

/**
 * A field highlighted with its highlighted fragments.
 *
 *
 */
public class HighlightField implements Streamable {

    private String name;

    private String[] fragments;

    HighlightField() {
    }

    public HighlightField(String name, String[] fragments) {
        this.name = name;
        this.fragments = fragments;
    }

    /**
     * The name of the field highlighted.
     */
    public String name() {
        return name;
    }

    /**
     * The name of the field highlighted.
     */
    public String getName() {
        return name();
    }

    /**
     * The highlighted fragments. <tt>null</tt> if failed to highlight (for example, the field is not stored).
     */
    public String[] fragments() {
        return fragments;
    }

    /**
     * The highlighted fragments. <tt>null</tt> if failed to highlight (for example, the field is not stored).
     */
    public String[] getFragments() {
        return fragments();
    }

    @Override
    public String toString() {
        return "[" + name + "], fragments[" + Arrays.toString(fragments) + "]";
    }

    public static HighlightField readHighlightField(StreamInput in) throws IOException {
        HighlightField field = new HighlightField();
        field.readFrom(in);
        return field;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        name = in.readUTF();
        if (in.readBoolean()) {
            int size = in.readVInt();
            if (size == 0) {
                fragments = Strings.EMPTY_ARRAY;
            } else {
                fragments = new String[size];
                for (int i = 0; i < size; i++) {
                    fragments[i] = in.readUTF();
                }
            }
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeUTF(name);
        if (fragments == null) {
            out.writeBoolean(false);
        } else {
            out.writeBoolean(true);
            out.writeVInt(fragments.length);
            for (String fragment : fragments) {
                out.writeUTF(fragment);
            }
        }
    }
}
