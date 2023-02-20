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
package org.jboss.windup.operator.controllers;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelector;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.cdrs.v2alpha1.Windup;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupSecretBasicAuth;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupWebService;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupSpec;
import org.jboss.windup.operator.utils.CRDUtils;
import io.quarkus.logging.Log;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class WindupDistConfigurator {

    private final Windup cr;

    private final List<EnvVar> allEnvVars;
    private final List<Volume> allVolumes;
    private final List<VolumeMount> allVolumeMounts;

    public WindupDistConfigurator(Windup cr) {
        this.cr = cr;
        this.allEnvVars = new ArrayList<>();
        this.allVolumes = new ArrayList<>();
        this.allVolumeMounts = new ArrayList<>();

        configureHttp();
        configureDatabase();
        configureBasicAuth();
        configureOidc();
        configureSunat();
        configureWorkspace();
    }

    public List<EnvVar> getAllEnvVars() {
        return allEnvVars;
    }

    public List<Volume> getAllVolumes() {
        return allVolumes;
    }

    public List<VolumeMount> getAllVolumeMounts() {
        return allVolumeMounts;
    }

    private void configureHttp() {
        var optionMapper = optionMapper(cr.getSpec().getHttpSpec());

        configureTLS(optionMapper);

        List<EnvVar> envVars = optionMapper.getEnvVars();
        allEnvVars.addAll(envVars);
    }

    private void configureTLS(OptionMapper<WindupSpec.HttpSpec> optionMapper) {
        final String certFileOptionName = "QUARKUS_HTTP_SSL_CERTIFICATE_FILE";
        final String keyFileOptionName = "QUARKUS_HTTP_SSL_CERTIFICATE_KEY_FILE";

        if (!WindupWebService.isTlsConfigured(cr)) {
            // for mapping and triggering warning in status if someone uses the fields directly
            optionMapper.mapOption(certFileOptionName);
            optionMapper.mapOption(keyFileOptionName);
            return;
        }

        optionMapper.mapOption(certFileOptionName, Constants.CERTIFICATES_FOLDER + "/tls.crt");
        optionMapper.mapOption(keyFileOptionName, Constants.CERTIFICATES_FOLDER + "/tls.key");

        optionMapper.mapOption("QUARKUS_HTTP_INSECURE_REQUESTS", "redirect");

        var volume = new VolumeBuilder()
                .withName("searchpe-tls-certificates")
                .withNewSecret()
                .withSecretName(cr.getSpec().getHttpSpec().getTlsSecret())
                .withOptional(false)
                .endSecret()
                .build();

        var volumeMount = new VolumeMountBuilder()
                .withName(volume.getName())
                .withMountPath(Constants.CERTIFICATES_FOLDER)
                .build();

        allVolumes.add(volume);
        allVolumeMounts.add(volumeMount);
    }

    private void configureDatabase() {
        List<EnvVar> envVars = optionMapper(cr.getSpec().getDatabaseSpec())
                .mapOption("QUARKUS_DATASOURCE_USERNAME", WindupSpec.DatabaseSpec::getUsernameSecret)
                .mapOption("QUARKUS_DATASOURCE_PASSWORD", WindupSpec.DatabaseSpec::getPasswordSecret)
                .mapOption("QUARKUS_DATASOURCE_JDBC_URL", WindupSpec.DatabaseSpec::getUrl)
                .getEnvVars();
        allEnvVars.addAll(envVars);
    }

    private void configureBasicAuth() {
        boolean isBasicAuthEnabled = CRDUtils
                .getValueFromSubSpec(cr.getSpec().getBasicAuthSpec(), WindupSpec.BasicAuthSpec::isEnabled)
                .orElse(false);
        if (!isBasicAuthEnabled) {
            return;
        }

        SecretKeySelector sessionEncryptionKeySecret = CRDUtils
                .getValueFromSubSpec(cr.getSpec().getBasicAuthSpec(), WindupSpec.BasicAuthSpec::getSessionEncryptionKeySecret)
                .orElseGet(() -> new SecretKeySelector(Constants.BASIC_AUTH_SECRET_ENCRYPTIONKEY, WindupSecretBasicAuth.getSecretName(cr), false));

        List<EnvVar> envVars = optionMapper(cr.getSpec().getBasicAuthSpec())
                .mapOption("QUARKUS_HTTP_AUTH_SESSION_ENCRYPTION_KEY", basicAuthSpec -> sessionEncryptionKeySecret)
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private void configureOidc() {
        boolean isOidcAuthEnabled = CRDUtils
                .getValueFromSubSpec(cr.getSpec().getOidcSpec(), WindupSpec.OidcSpec::isEnabled)
                .orElse(false);
        if (!isOidcAuthEnabled) {
            return;
        }

        List<EnvVar> envVars = optionMapper(cr.getSpec().getOidcSpec())
                .mapOption("QUARKUS_OIDC_AUTH_SERVER_URL", WindupSpec.OidcSpec::getServerUrl)
                .mapOption("QUARKUS_OIDC_CLIENT_ID", WindupSpec.OidcSpec::getClientId)
                .mapOption("QUARKUS_OIDC_CREDENTIALS_SECRET", WindupSpec.OidcSpec::getCredentialsSecret)
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private void configureSunat() {
        List<EnvVar> envVars = optionMapper(cr.getSpec().getSunatSpec())
                .mapOption("SEARCHPE_SUNAT_PADRONREDUCIDOURL", WindupSpec.SunatSpec::getPadronReducidoUrl)
                .mapOption("SEARCHPE_SCHEDULED_CRON", WindupSpec.SunatSpec::getPadronReducidoCron)
                .getEnvVars();

        allEnvVars.addAll(envVars);
    }

    private void configureWorkspace() {
        var volume = new VolumeBuilder()
                .withName("searchpe-workspace")
                .withNewEmptyDir()
                .endEmptyDir()
                .build();

        var volumeMount = new VolumeMountBuilder()
                .withName(volume.getName())
                .withMountPath(Constants.WORKSPACES_FOLDER)
                .build();

        allEnvVars.add(new EnvVarBuilder()
                .withName("SEARCHPE_WORKSPACE_DIRECTORY")
                .withValue(Constants.WORKSPACES_FOLDER)
                .build()
        );
        allVolumes.add(volume);
        allVolumeMounts.add(volumeMount);
    }

    private <T> OptionMapper<T> optionMapper(T optionSpec) {
        return new OptionMapper<>(optionSpec);
    }

    private class OptionMapper<T> {
        private final T categorySpec;
        private final List<EnvVar> envVars;

        public OptionMapper(T optionSpec) {
            this.categorySpec = optionSpec;
            this.envVars = new ArrayList<>();
        }

        public List<EnvVar> getEnvVars() {
            return envVars;
        }

        public <R> OptionMapper<T> mapOption(String optionName, Function<T, R> optionValueSupplier) {
            if (categorySpec == null) {
                Log.debugf("No category spec provided for %s", optionName);
                return this;
            }

            R value = optionValueSupplier.apply(categorySpec);

            if (value == null || value.toString().trim().isEmpty()) {
                Log.debugf("No value provided for %s", optionName);
                return this;
            }

            EnvVarBuilder envVarBuilder = new EnvVarBuilder()
                    .withName(optionName);

            if (value instanceof SecretKeySelector) {
                envVarBuilder.withValueFrom(new EnvVarSourceBuilder().withSecretKeyRef((SecretKeySelector) value).build());
            } else {
                envVarBuilder.withValue(String.valueOf(value));
            }

            envVars.add(envVarBuilder.build());

            return this;
        }

        public <R> OptionMapper<T> mapOption(String optionName) {
            return mapOption(optionName, s -> null);
        }

        public <R> OptionMapper<T> mapOption(String optionName, R optionValue) {
            return mapOption(optionName, s -> optionValue);
        }

        protected <R extends Collection<?>> OptionMapper<T> mapOptionFromCollection(String optionName, Function<T, R> optionValueSupplier) {
            return mapOption(optionName, s -> {
                var value = optionValueSupplier.apply(s);
                if (value == null) return null;
                return value.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
            });
        }
    }

}
