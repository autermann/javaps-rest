/*
 * Copyright (C) 2016 by 52 North Initiative for Geospatial Open Source Software GmbH
 *
 * Contact: Andreas Wytzisk
 * 52 North Initiative for Geospatial Open Source Software GmbH
 * Martin-Luther-King-Weg 24
 * 48155 Muenster, Germany
 * info@52north.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.n52.wps.javaps.rest.deserializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.model.Input;
import io.swagger.model.Output;
import io.swagger.model.TransmissionMode;
import org.n52.shetland.ogc.ows.OwsCode;
import org.n52.shetland.ogc.wps.DataTransmissionMode;
import org.n52.shetland.ogc.wps.Format;
import org.n52.shetland.ogc.wps.OutputDefinition;
import org.n52.shetland.ogc.wps.data.ProcessData;
import org.n52.shetland.ogc.wps.data.ReferenceProcessData;
import org.n52.shetland.ogc.wps.data.impl.StringValueProcessData;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import static java.util.stream.Collectors.toList;

public class ExecuteDeserializer {
    private static final String VALUE_KEY = "value";
    private static final String INLINE_VALUE_KEY = "inlineValue";
    private static final String HREF_KEY = "href";
    private static final String FORMAT_KEY = "format";
    private static final String MIME_TYPE_KEY = "mimeType";
    private static final String ENCODING_KEY = "encoding";
    private static final String SCHEMA_KEY = "schema";
    private static final Format FORMAT_TEXT_PLAIN = new Format("text/plain");
    private static final String BBOX_KEY = "bbox";

    @Autowired
    private ObjectMapper objectMapper;

    public List<OutputDefinition> readOutputs(List<Output> outputs) {
        return outputs.stream().map(output -> {
            OutputDefinition definition = new OutputDefinition();
            definition.setId(createId(output.getId()));
            io.swagger.model.Format format = output.getFormat();
            definition.setFormat(new Format(format.getMimeType(), format.getEncoding(), format.getSchema()));
            definition.setDataTransmissionMode(getTransmisionMode(output.getTransmissionMode()));
            return definition;
        }).collect(toList());
    }

    private DataTransmissionMode getTransmisionMode(TransmissionMode transmissionMode) {
        switch (transmissionMode) {
            case VALUE:
                return DataTransmissionMode.VALUE;
            case REFERENCE:
            default:
                return DataTransmissionMode.REFERENCE;
        }
    }

    @SuppressWarnings("rawtypes")
    private Format getFormat(JsonNode object) {
        return new Format(object.path(MIME_TYPE_KEY).asText(),
                          object.path(ENCODING_KEY).asText(),
                          object.path(SCHEMA_KEY).asText());
    }

    private OwsCode createId(String id) {
        return new OwsCode(id);
    }

    public List<ProcessData> readInputs(List<Input> inputs) throws JsonProcessingException, URISyntaxException {
        List<ProcessData> list = new ArrayList<>();
        for (Input input : inputs) {
            list.add(readInput(createId(input.getId()), input.getInput()));
        }
        return list;
    }

    private ProcessData readInput(OwsCode id, JsonNode map) throws JsonProcessingException, URISyntaxException {
        JsonNode valueNode = map.path(VALUE_KEY);
        if (valueNode.isObject()) {
            ObjectNode value = (ObjectNode) valueNode;
            // complex data
            if (value.has(INLINE_VALUE_KEY)) {
                Format format = FORMAT_TEXT_PLAIN;
                if (map.has(FORMAT_KEY)) {
                    format = getFormat(map.get(FORMAT_KEY));
                }
                String stringValue = objectMapper.writeValueAsString(value.get(INLINE_VALUE_KEY));
                return new StringValueProcessData(id, format, stringValue);
            } else if (value.has(HREF_KEY)) {
                URI uri = new URI(value.get(HREF_KEY).toString());
                Format format = getFormat(map.get(FORMAT_KEY));
                return new ReferenceProcessData(id, format, uri);
            }
        } else if (valueNode.isValueNode()) {
            return new StringValueProcessData(id, FORMAT_TEXT_PLAIN, valueNode.asText());

        } else if (map.path(BBOX_KEY).isObject()) {
            return new StringValueProcessData(id, new Format("application/json"),
                                              new ObjectMapper().writeValueAsString(map));
        }

        return null;
    }

}
