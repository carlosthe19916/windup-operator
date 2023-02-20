/*
 * Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.windup.operator.cdrs.v2alpha1;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import org.jboss.windup.operator.ValueOrSecret;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WindupSpec {

    @JsonPropertyDescription("Number of instances. Default is 1.")
    private int instances = 1;

    @JsonPropertyDescription("Custom image to be used.")
    private String image;

    @JsonPropertyDescription("Secret(s) that might be used when pulling an image from a private container image registry or repository.")
    private List<LocalObjectReference> imagePullSecrets;

    @JsonPropertyDescription("Configuration of the server.\n" +
            "expressed as a keys and values that can be either direct values or references to secrets.")
    private List<ValueOrSecret> additionalOptions;

    @JsonProperty("http")
    @JsonPropertyDescription("In this section you can configure features related to HTTP and HTTPS")
    private HttpSpec httpSpec;

    @JsonProperty("ingress")
    @JsonPropertyDescription("The deployment is, by default, exposed through a basic ingress.\n" +
            "You can change this behaviour by setting the enabled property to false.")
    private IngressSpec ingressSpec;

    @JsonProperty("db")
    @JsonPropertyDescription("In this section you can find all properties related to connect to a database.")
    private DatabaseSpec databaseSpec;

    @JsonProperty("hostname")
    @JsonPropertyDescription("In this section you can configure hostname and related properties.")
    private HostnameSpec hostnameSpec;

    @JsonProperty("oidc")
    @JsonPropertyDescription("In this section you can configure Oidc settings.")
    private OidcSpec oidcSpec;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HttpSpec {
        @JsonPropertyDescription("A secret containing the TLS configuration for HTTPS. Reference: https://kubernetes.io/docs/concepts/configuration/secret/#tls-secrets.")
        private String tlsSecret;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class IngressSpec {
        @JsonProperty("enabled")
        private boolean enabled = true;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DatabaseSpec {
        @JsonPropertyDescription("Size of the PVC to create.")
        private String size;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HostnameSpec {
        @JsonPropertyDescription("Hostname for the server.")
        private String hostname;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OidcSpec {
        @JsonPropertyDescription("Enable Oidc Auth.")
        private boolean enabled;

        @JsonPropertyDescription("Oidc server url.")
        private String serverUrl;

        @JsonPropertyDescription("Oidc client id.")
        private String clientId;

        @JsonPropertyDescription("Oidc client id.")
        private SecretKeySelector credentialsSecret;
    }

}
