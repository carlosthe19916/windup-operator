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

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.jboss.windup.operator.Config;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.cdrs.v2alpha1.Windup;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupWebDeployment;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupWebIngress;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupSecretBasicAuth;
import org.jboss.windup.operator.cdrs.v2alpha1.WindupWebService;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE, name = "windup", dependents = {
        @Dependent(name = "secret", type = WindupSecretBasicAuth.class),
        @Dependent(name = "deployment", type = WindupWebDeployment.class),
        @Dependent(name = "service", type = WindupWebService.class),
        @Dependent(name = "ingress", type = WindupWebIngress.class, readyPostcondition = WindupWebIngress.class, dependsOn = "service")
})
public class WindupReconciler implements Reconciler<Windup>, ContextInitializer<Windup> {

    private static final Logger logger = Logger.getLogger(WindupReconciler.class);

    @Inject
    Config config;

    @Inject
    KubernetesClient k8sClient;

    @Override
    public void initContext(Windup cr, Context<Windup> context) {
        final var labels = Map.of(
                "app.kubernetes.io/name", cr.getMetadata().getName(),
                "openubl-operator/cluster", Constants.WINDUP_WEB_NAME
        );
        context.managedDependentResourceContext().put(Constants.CONTEXT_LABELS_KEY, labels);
        context.managedDependentResourceContext().put(Constants.CONTEXT_CONFIG_KEY, config);
        context.managedDependentResourceContext().put(Constants.CONTEXT_K8S_CLIENT_KEY, k8sClient);
    }

    @SuppressWarnings("unchecked")
    @Override
    public UpdateControl<Windup> reconcile(Windup cr, Context context) {
        final var name = cr.getMetadata().getName();

        // retrieve the workflow reconciliation result and re-schedule if we have dependents that are not yet ready
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        Ingress ingress = (Ingress) context.getSecondaryResource(Ingress.class).orElseThrow();
                        final var url = WindupWebIngress.getExposedURL(cr, ingress);
                        url.ifPresent(serverUrl -> {
                            logger.infof("App %s is exposed and ready to be used at %s", name, serverUrl);
                        });
                        return UpdateControl.<Windup>noUpdate();
                    } else {
                        final var duration = Duration.ofSeconds(5);
                        logger.infof("App %s is not ready yet, rescheduling reconciliation after %ss", name, duration.toSeconds());
                        return UpdateControl.<Windup>noUpdate().rescheduleAfter(duration);
                    }
                })
                .orElseThrow();
    }

}
