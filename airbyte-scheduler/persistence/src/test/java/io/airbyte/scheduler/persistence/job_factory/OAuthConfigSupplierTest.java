/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.scheduler.persistence.job_factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import io.airbyte.analytics.TrackingClient;
import io.airbyte.commons.json.Jsons;
import io.airbyte.config.SourceOAuthParameter;
import io.airbyte.config.StandardSourceDefinition;
import io.airbyte.config.persistence.ConfigNotFoundException;
import io.airbyte.config.persistence.ConfigRepository;
import io.airbyte.oauth.MoreOAuthParameters;
import io.airbyte.validation.json.JsonValidationException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class OAuthConfigSupplierTest {

  private ConfigRepository configRepository;
  private TrackingClient trackingClient;
  private OAuthConfigSupplier oAuthConfigSupplier;
  private UUID sourceDefinitionId;

  @BeforeEach
  public void setup() throws JsonValidationException, ConfigNotFoundException, IOException {
    configRepository = mock(ConfigRepository.class);
    trackingClient = mock(TrackingClient.class);
    oAuthConfigSupplier = new OAuthConfigSupplier(configRepository, false, trackingClient);
    sourceDefinitionId = UUID.randomUUID();
    when(configRepository.getStandardSourceDefinition(any())).thenReturn(new StandardSourceDefinition()
        .withSourceDefinitionId(sourceDefinitionId)
        .withName("test")
        .withDockerImageTag("dev"));
  }

  @Test
  public void testInjectEmptyOAuthParameters() throws IOException {
    final JsonNode config = generateJsonConfig();
    final UUID workspaceId = UUID.randomUUID();
    final JsonNode actualConfig = oAuthConfigSupplier.injectSourceOAuthParameters(sourceDefinitionId, workspaceId, Jsons.clone(config));
    assertEquals(config, actualConfig);
    assertNoTracking();
  }

  @Test
  public void testInjectGlobalOAuthParameters() throws JsonValidationException, IOException {
    final JsonNode config = generateJsonConfig();
    final UUID workspaceId = UUID.randomUUID();
    final Map<String, String> oauthParameters = generateOAuthParameters();
    when(configRepository.listSourceOAuthParam()).thenReturn(List.of(
        new SourceOAuthParameter()
            .withOauthParameterId(UUID.randomUUID())
            .withSourceDefinitionId(sourceDefinitionId)
            .withWorkspaceId(null)
            .withConfiguration(Jsons.jsonNode(oauthParameters)),
        new SourceOAuthParameter()
            .withOauthParameterId(UUID.randomUUID())
            .withSourceDefinitionId(UUID.randomUUID())
            .withWorkspaceId(null)
            .withConfiguration(Jsons.jsonNode(generateOAuthParameters()))));
    final JsonNode actualConfig = oAuthConfigSupplier.injectSourceOAuthParameters(sourceDefinitionId, workspaceId, Jsons.clone(config));
    final ObjectNode expectedConfig = ((ObjectNode) Jsons.clone(config));
    for (final String key : oauthParameters.keySet()) {
      expectedConfig.set(key, Jsons.jsonNode(oauthParameters.get(key)));
    }
    assertEquals(expectedConfig, actualConfig);
    verify(trackingClient, times(1)).track(workspaceId, "OAuth Injection - Backend", Map.of(
        "connector_source", "test",
        "connector_source_definition_id", sourceDefinitionId,
        "connector_source_version", "dev"));
  }

  @Test
  public void testInjectWorkspaceOAuthParameters() throws JsonValidationException, IOException {
    final JsonNode config = generateJsonConfig();
    final UUID workspaceId = UUID.randomUUID();
    when(configRepository.listSourceOAuthParam()).thenReturn(List.of(
        new SourceOAuthParameter()
            .withOauthParameterId(UUID.randomUUID())
            .withSourceDefinitionId(sourceDefinitionId)
            .withWorkspaceId(null)
            .withConfiguration(Jsons.jsonNode(generateOAuthParameters())),
        new SourceOAuthParameter()
            .withOauthParameterId(UUID.randomUUID())
            .withSourceDefinitionId(sourceDefinitionId)
            .withWorkspaceId(workspaceId)
            .withConfiguration(Jsons.jsonNode(ImmutableMap.<String, Object>builder()
                .put("api_secret", "my secret workspace")
                .put("api_client", Map.of("anyOf", List.of(Map.of("id", "id"), Map.of("service", "account"))))
                .build()))));
    final JsonNode actualConfig = oAuthConfigSupplier.injectSourceOAuthParameters(sourceDefinitionId, workspaceId, Jsons.clone(config));
    final ObjectNode expectedConfig = (ObjectNode) Jsons.clone(config);
    expectedConfig.set("api_secret", Jsons.jsonNode("my secret workspace"));
    expectedConfig.set("api_client", Jsons.jsonNode(Map.of("anyOf", List.of(
        Map.of("id", "id"),
        Map.of("service", "account")))));
    assertEquals(expectedConfig, actualConfig);
    verify(trackingClient, times(1)).track(workspaceId, "OAuth Injection - Backend", Map.of(
        "connector_source", "test",
        "connector_source_definition_id", sourceDefinitionId,
        "connector_source_version", "dev"));
  }

  @Test
  void testInjectMaskedOAuthParameters() throws JsonValidationException, IOException {
    final OAuthConfigSupplier maskingSupplier = new OAuthConfigSupplier(configRepository, true, trackingClient);

    final JsonNode config = generateJsonConfig();
    final UUID workspaceId = UUID.randomUUID();
    final Map<String, String> oauthParameters = generateOAuthParameters();
    when(configRepository.listSourceOAuthParam()).thenReturn(List.of(
        new SourceOAuthParameter()
            .withOauthParameterId(UUID.randomUUID())
            .withSourceDefinitionId(sourceDefinitionId)
            .withWorkspaceId(null)
            .withConfiguration(Jsons.jsonNode(oauthParameters)),
        new SourceOAuthParameter()
            .withOauthParameterId(UUID.randomUUID())
            .withSourceDefinitionId(UUID.randomUUID())
            .withWorkspaceId(null)
            .withConfiguration(Jsons.jsonNode(generateOAuthParameters()))));
    final JsonNode actualConfig = maskingSupplier.injectSourceOAuthParameters(sourceDefinitionId, workspaceId, Jsons.clone(config));
    final ObjectNode expectedConfig = ((ObjectNode) Jsons.clone(config));
    for (final String key : oauthParameters.keySet()) {
      expectedConfig.set(key, MoreOAuthParameters.getSecretMask());
    }
    assertEquals(expectedConfig, actualConfig);
    assertNoTracking();
  }

  private ObjectNode generateJsonConfig() {
    return (ObjectNode) Jsons.jsonNode(ImmutableMap.builder()
        .put("apiSecret", "123")
        .put("client", "testing")
        .build());
  }

  private Map<String, String> generateOAuthParameters() {
    return ImmutableMap.<String, String>builder()
        .put("api_secret", "mysecret")
        .put("api_client", UUID.randomUUID().toString())
        .build();
  }

  private void assertNoTracking() {
    verify(trackingClient, times(0)).track(any(), anyString(), anyMap());
  }

}
