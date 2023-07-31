package org.jboss.windup.operator.controllers;

import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.config.informer.InformerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.*;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Dependent;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import io.javaoperatorsdk.operator.processing.event.source.informer.InformerEventSource;
import org.jboss.logging.Logger;
import org.jboss.windup.operator.Constants;
import org.jboss.windup.operator.cdrs.v2alpha1.*;

import java.time.Duration;
import java.util.Map;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

@ControllerConfiguration(
        namespaces = WATCH_CURRENT_NAMESPACE,
        name = "windup",
        dependents = {
                @Dependent(name = "db-pvc", type = DBPersistentVolumeClaim.class),
                @Dependent(name = "db-secret", type = DBSecret.class),
                @Dependent(name = "db-deployment", type = DBDeployment.class, dependsOn = {"db-pvc", "db-secret"}, readyPostcondition = DBDeployment.class),
                @Dependent(name = "db-service", type = DBService.class, dependsOn = {"db-deployment"}),

                @Dependent(name = "web-pvc", type = WebConsolePersistentVolumeClaim.class),
                @Dependent(name = "web-deployment", type = WebDeployment.class, dependsOn = {"db-service"}, readyPostcondition = WebDeployment.class),
                @Dependent(name = "web-service", type = WebService.class, dependsOn = {"db-service"}),

                @Dependent(name = "executor-deployment", type = ExecutorDeployment.class, dependsOn = {"web-service"}),

                @Dependent(name = "ingress", type = WebIngress.class, dependsOn = {"db-service"}, readyPostcondition = WebIngress.class),
                @Dependent(name = "ingress-secure", type = WebIngressSecure.class, dependsOn = {"db-service"}, readyPostcondition = WebIngressSecure.class)
        }
)
public class WindupReconciler implements Reconciler<Windup>, ContextInitializer<Windup>,
        EventSourceInitializer<Windup> {

    private static final Logger logger = Logger.getLogger(WindupReconciler.class);

    public static final String PVC_EVENT_SOURCE = "PVCEventSource";
    public static final String DEPLOYMENT_EVENT_SOURCE = "DeploymentEventSource";
    public static final String SERVICE_EVENT_SOURCE = "ServiceEventSource";
    public static final String INGRESS_EVENT_SOURCE = "IngressEventSource";

    @Override
    public void initContext(Windup cr, Context<Windup> context) {
        final var labels = Map.of(
                "app.kubernetes.io/managed-by", "windup-operator",
                "app.kubernetes.io/name", cr.getMetadata().getName(),
                "app.kubernetes.io/part-of", cr.getMetadata().getName(),
                "windup-operator/cluster", Constants.WINDUP_NAME
        );
        context.managedDependentResourceContext().put(Constants.CONTEXT_LABELS_KEY, labels);
    }

    @Override
    public UpdateControl<Windup> reconcile(Windup cr, Context context) {
        return context.managedDependentResourceContext()
                .getWorkflowReconcileResult()
                .map(wrs -> {
                    if (wrs.allDependentResourcesReady()) {
                        return UpdateControl.<Windup>noUpdate();
                    } else {
                        final var duration = Duration.ofSeconds(5);
                        return UpdateControl.<Windup>noUpdate().rescheduleAfter(duration);
                    }
                })
                .orElseThrow();
    }

    @Override
    public Map<String, EventSource> prepareEventSources(EventSourceContext<Windup> context) {
        var pcvInformerConfiguration = InformerConfiguration.from(PersistentVolumeClaim.class, context).build();
        var deploymentInformerConfiguration = InformerConfiguration.from(Deployment.class, context).build();
        var serviceInformerConfiguration = InformerConfiguration.from(Service.class, context).build();
        var ingressInformerConfiguration = InformerConfiguration.from(Ingress.class, context).build();

        var pcvInformerEventSource = new InformerEventSource<>(pcvInformerConfiguration, context);
        var deploymentInformerEventSource = new InformerEventSource<>(deploymentInformerConfiguration, context);
        var serviceInformerEventSource = new InformerEventSource<>(serviceInformerConfiguration, context);
        var ingressInformerEventSource = new InformerEventSource<>(ingressInformerConfiguration, context);

        return Map.of(
                PVC_EVENT_SOURCE, pcvInformerEventSource,
                DEPLOYMENT_EVENT_SOURCE, deploymentInformerEventSource,
                SERVICE_EVENT_SOURCE, serviceInformerEventSource,
                INGRESS_EVENT_SOURCE, ingressInformerEventSource
        );
    }
}
