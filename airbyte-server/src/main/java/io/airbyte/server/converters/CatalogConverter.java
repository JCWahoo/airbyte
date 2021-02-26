/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.server.converters;

import io.airbyte.commons.enums.Enums;
import io.airbyte.commons.text.Names;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Convert classes between io.airbyte.protocol.models and io.airbyte.api.model
 */
public class CatalogConverter {

  private static io.airbyte.api.model.AirbyteStreamName toApi(final String name, final io.airbyte.protocol.models.AirbyteStreamName streamName) {
    final io.airbyte.api.model.AirbyteStreamName result = new io.airbyte.api.model.AirbyteStreamName();
    // Try to use streamName object or default to old name field
    if (streamName != null) {
      return result
          .namespace(streamName.getNamespace())
          .name(streamName.getName());
    } else {
      return result.name(name);
    }
  }

  private static io.airbyte.protocol.models.AirbyteStreamName toProtocol(final String name, final io.airbyte.api.model.AirbyteStreamName streamName) {
    final io.airbyte.protocol.models.AirbyteStreamName result = new io.airbyte.protocol.models.AirbyteStreamName();
    // Try to use streamName object or default to old name field
    if (streamName != null) {
      return result
          .withNamespace(streamName.getNamespace())
          .withName(streamName.getName());
    } else {
      return result.withName(name);
    }
  }

  private static io.airbyte.api.model.AirbyteStream toApi(final io.airbyte.protocol.models.AirbyteStream stream) {
    return new io.airbyte.api.model.AirbyteStream()
        .name(stream.getName())
        .streamName(toApi(stream.getName(), stream.getStreamName()))
        .jsonSchema(stream.getJsonSchema())
        .supportedSyncModes(Enums.convertListTo(stream.getSupportedSyncModes(), io.airbyte.api.model.SyncMode.class))
        .sourceDefinedCursor(stream.getSourceDefinedCursor())
        .defaultCursorField(stream.getDefaultCursorField());
  }

  private static io.airbyte.protocol.models.AirbyteStream toProtocol(final io.airbyte.api.model.AirbyteStream stream) {
    return new io.airbyte.protocol.models.AirbyteStream()
        .withName(stream.getName())
        .withStreamName(toProtocol(stream.getName(), stream.getStreamName()))
        .withJsonSchema(stream.getJsonSchema())
        .withSupportedSyncModes(Enums.convertListTo(stream.getSupportedSyncModes(), io.airbyte.protocol.models.SyncMode.class))
        .withSourceDefinedCursor(stream.getSourceDefinedCursor())
        .withDefaultCursorField(stream.getDefaultCursorField());
  }

  public static io.airbyte.api.model.AirbyteCatalog toApi(final io.airbyte.protocol.models.AirbyteCatalog catalog) {
    return new io.airbyte.api.model.AirbyteCatalog()
        .streams(catalog.getStreams()
            .stream()
            .map(CatalogConverter::toApi)
            .map(s -> new io.airbyte.api.model.AirbyteStreamAndConfiguration()
                .stream(s)
                .config(generateDefaultConfiguration(s)))
            .collect(Collectors.toList()));
  }

  private static io.airbyte.api.model.AirbyteStreamConfiguration generateDefaultConfiguration(final io.airbyte.api.model.AirbyteStream s) {
    io.airbyte.api.model.AirbyteStreamConfiguration result = new io.airbyte.api.model.AirbyteStreamConfiguration()
        .aliasName(Names.toAlphanumericAndUnderscore(s.getName()))
        .cursorField(s.getDefaultCursorField())
        .selected(true);
    if (s.getSupportedSyncModes().size() > 0)
      result.setSyncMode(s.getSupportedSyncModes().get(0));
    else
      result.setSyncMode(io.airbyte.api.model.SyncMode.INCREMENTAL);
    return result;
  }

  public static io.airbyte.api.model.AirbyteCatalog toApi(final io.airbyte.protocol.models.ConfiguredAirbyteCatalog catalog) {
    final List<io.airbyte.api.model.AirbyteStreamAndConfiguration> streams = catalog.getStreams()
        .stream()
        .map(configuredStream -> {
          final io.airbyte.api.model.AirbyteStreamConfiguration configuration = new io.airbyte.api.model.AirbyteStreamConfiguration()
              .syncMode(Enums.convertTo(configuredStream.getSyncMode(), io.airbyte.api.model.SyncMode.class))
              .cursorField(configuredStream.getCursorField())
              .aliasName(Names.toAlphanumericAndUnderscore(configuredStream.getStream().getName()))
              .selected(true);
          return new io.airbyte.api.model.AirbyteStreamAndConfiguration()
              .stream(toApi(configuredStream.getStream()))
              .config(configuration);
        })
        .collect(Collectors.toList());
    return new io.airbyte.api.model.AirbyteCatalog().streams(streams);
  }

  public static io.airbyte.protocol.models.ConfiguredAirbyteCatalog toProtocol(final io.airbyte.api.model.AirbyteCatalog catalog) {
    final List<io.airbyte.protocol.models.ConfiguredAirbyteStream> streams = catalog.getStreams()
        .stream()
        .filter(s -> s.getConfig().getSelected())
        .map(s -> new io.airbyte.protocol.models.ConfiguredAirbyteStream()
            .withStream(toProtocol(s.getStream()))
            .withSyncMode(Enums.convertTo(s.getConfig().getSyncMode(), io.airbyte.protocol.models.SyncMode.class))
            .withCursorField(s.getConfig().getCursorField()))
        .collect(Collectors.toList());
    return new io.airbyte.protocol.models.ConfiguredAirbyteCatalog()
        .withStreams(streams);
  }

}
