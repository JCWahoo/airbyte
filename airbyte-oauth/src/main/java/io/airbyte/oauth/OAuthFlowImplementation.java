/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.config.persistence.ConfigNotFoundException;
import io.airbyte.protocol.models.OAuthConfigSpecification;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

public interface OAuthFlowImplementation {

  String getSourceConsentUrl(UUID workspaceId, UUID sourceDefinitionId, String redirectUrl) throws IOException, ConfigNotFoundException;

  String getDestinationConsentUrl(UUID workspaceId, UUID destinationDefinitionId, String redirectUrl) throws IOException, ConfigNotFoundException;

  @Deprecated
  Map<String, Object> completeSourceOAuth(UUID workspaceId, UUID sourceDefinitionId, Map<String, Object> queryParams, String redirectUrl)
      throws IOException, ConfigNotFoundException;

  Map<String, Object> completeSourceOAuth(UUID workspaceId,
                                          UUID sourceDefinitionId,
                                          Map<String, Object> queryParams,
                                          String redirectUrl,
                                          JsonNode inputOAuthConfiguration,
                                          OAuthConfigSpecification oauthConfigSpecification)
      throws IOException, ConfigNotFoundException;

  @Deprecated
  Map<String, Object> completeDestinationOAuth(UUID workspaceId, UUID destinationDefinitionId, Map<String, Object> queryParams, String redirectUrl)
      throws IOException, ConfigNotFoundException;

  Map<String, Object> completeDestinationOAuth(UUID workspaceId,
                                               UUID destinationDefinitionId,
                                               Map<String, Object> queryParams,
                                               String redirectUrl,
                                               JsonNode inputOAuthConfiguration,
                                               OAuthConfigSpecification oAuthConfigSpecification)
      throws IOException, ConfigNotFoundException;

}
