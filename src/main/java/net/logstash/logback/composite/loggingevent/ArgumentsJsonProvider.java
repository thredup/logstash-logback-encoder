/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.logstash.logback.composite.loggingevent;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import net.logstash.logback.argument.StructuredArgument;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import net.logstash.logback.composite.FieldNamesAware;
import net.logstash.logback.composite.JsonFactoryAware;
import net.logstash.logback.fieldnames.LogstashFieldNames;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Include the logging event's {@link ILoggingEvent#getArgumentArray()} in the JSON output.
 * <p>
 *
 * Arguments that are an instance of {@link StructuredArgument} will be output
 * as specified by {@link StructuredArgument#writeTo(JsonGenerator)}.
 * <p>
 *
 * Non-{@link StructuredArgument}s will be omitted unless {@link #includeNonStructuredArguments} is true.
 * When true, they will be included in the JSON output as separate fields
 * whose names are {@link #nonStructuredArgumentsFieldPrefix} plus the argument index.
 * (For example, "arg0").
 * </p>
 *
 * If the fieldName is non-null, then the arguments will be written to that field as a subobject.
 * Otherwise, the arguments are written inline.
 */
public class ArgumentsJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> implements FieldNamesAware<LogstashFieldNames>, JsonFactoryAware {

    private boolean includeStructuredArguments = true;
    private boolean includeNonStructuredArguments;
    private String nonStructuredArgumentsFieldPrefix = "arg";
    private String nonStructuredArgumentsFieldsMapping;
    private JsonFactory jsonFactory;
    private Map<String, String> nonStructuredArgumentsFieldsMappingMap = Collections.emptyMap();

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {

        if (!includeStructuredArguments && !includeNonStructuredArguments) {
            // Short-circuit if nothing is included
            return;
        }

        Object[] args = event.getArgumentArray();

        if (args == null || args.length == 0) {
            return;
        }

        boolean hasWrittenFieldName = false;

        for (int argIndex = 0; argIndex < args.length; argIndex++) {

            Object arg = args[argIndex];

            if (arg instanceof StructuredArgument) {
                if (includeStructuredArguments) {
                    if (!hasWrittenFieldName && getFieldName() != null) {
                        generator.writeObjectFieldStart(getFieldName());
                        hasWrittenFieldName = true;
                    }
                    StructuredArgument structuredArgument = (StructuredArgument) arg;
                    structuredArgument.writeTo(generator);
                }
            } else if (includeNonStructuredArguments) {
                if (!hasWrittenFieldName && getFieldName() != null) {
                    generator.writeObjectFieldStart(getFieldName());
                    hasWrittenFieldName = true;
                }
                String fieldName = nonStructuredArgumentsFieldPrefix + argIndex;
                String mappedName = nonStructuredArgumentsFieldsMappingMap.get(fieldName);
                generator.writeObjectField(mappedName != null ? mappedName : fieldName, arg.toString());
            }
        }

        if (hasWrittenFieldName) {
            generator.writeEndObject();
        }
    }

    public boolean isIncludeStructuredArguments() {
        return includeStructuredArguments;
    }

    public void setIncludeStructuredArguments(boolean includeStructuredArguments) {
        this.includeStructuredArguments = includeStructuredArguments;
    }

    public boolean isIncludeNonStructuredArguments() {
        return includeNonStructuredArguments;
    }

    public void setIncludeNonStructuredArguments(boolean includeNonStructuredArguments) {
        this.includeNonStructuredArguments = includeNonStructuredArguments;
    }

    public String getNonStructuredArgumentsFieldPrefix() {
        return nonStructuredArgumentsFieldPrefix;
    }

    public void setNonStructuredArgumentsFieldPrefix(String nonStructuredArgumentsFieldPrefix) {
        this.nonStructuredArgumentsFieldPrefix = nonStructuredArgumentsFieldPrefix;
    }

    public void setNonStructuredArgumentsFieldsMapping(String nonStructuredArgumentsFieldsMap) {
        this.nonStructuredArgumentsFieldsMapping = nonStructuredArgumentsFieldsMap;
        parseMapping();
    }

    public String getNonStructuredArgumentsFieldsMapping() {
        return this.nonStructuredArgumentsFieldsMapping;
    }

    @Override
    public void setFieldNames(LogstashFieldNames fieldNames) {
        setFieldName(fieldNames.getArguments());
    }

    @Override
    public void setJsonFactory(JsonFactory jsonFactory) {
        this.jsonFactory = jsonFactory;
        parseMapping();
    }

    private void parseMapping() {
        if (nonStructuredArgumentsFieldsMapping != null && this.jsonFactory != null) {
            try {
                nonStructuredArgumentsFieldsMappingMap = this.jsonFactory.
                        createParser(this.nonStructuredArgumentsFieldsMapping).readValueAs(new TypeReference<HashMap<String, Object>>() {
                });
            } catch (IOException e) {
                addError("Failed to parse mapped fields [" + nonStructuredArgumentsFieldsMappingMap + "]", e);
            }
        }
    }
}
