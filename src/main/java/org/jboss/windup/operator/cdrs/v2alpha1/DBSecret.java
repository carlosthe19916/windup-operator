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

import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.processing.dependent.Creator;
import io.javaoperatorsdk.operator.processing.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.CRUDKubernetesDependentResource;
import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.windup.operator.Constants;

import java.util.Map;

public class DBSecret extends CRUDKubernetesDependentResource<Secret, Windup> implements Creator<Secret, Windup> {

    public DBSecret() {
        super(Secret.class);
    }

    @Override
    protected Secret desired(Windup cr, Context<Windup> context) {
        return newSecret(cr, context);
    }

    @Override
    public Matcher.Result<Secret> match(Secret actual, Windup cr, Context<Windup> context) {
        final var desiredSecretName = getSecretName(cr);
        return Matcher.Result.nonComputed(actual.getMetadata().getName().equals(desiredSecretName));
    }

    @SuppressWarnings("unchecked")
    private Secret newSecret(Windup cr, Context<Windup> context) {
        final var labels = (Map<String, String>) context.managedDependentResourceContext()
                .getMandatory(Constants.CONTEXT_LABELS_KEY, Map.class);

        return new SecretBuilder()
                .withNewMetadata()
                .withName(getSecretName(cr))
                .withNamespace(cr.getMetadata().getNamespace())
                .withLabels(labels)
                .endMetadata()
                .addToStringData(Constants.DB_SECRET_USERNAME, RandomStringUtils.randomAlphanumeric(8))
                .addToStringData(Constants.DB_SECRET_PASSWORD, RandomStringUtils.randomAlphanumeric(8))
                .addToStringData(Constants.DB_SECRET_DATABASE_NAME, "windup")
                .build();
    }

    public static String getSecretName(Windup cr) {
        return cr.getMetadata().getName() + Constants.DB_SECRET_SUFFIX;
    }
}