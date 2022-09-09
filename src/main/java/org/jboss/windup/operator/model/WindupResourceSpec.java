package org.jboss.windup.operator.model;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.quarkus.runtime.annotations.RegisterForReflection;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@JsonDeserialize
@RegisterForReflection
@Getter
@Setter
public class WindupResourceSpec implements KubernetesResource {
    private static final long serialVersionUID = 1L;

    private String version;
    private String application_name;

    private String volume_capacity;
    private Integer executor_desired_replicas;

    private String hostname;
    private String tls_secret;

    private Images images;
    private DB db;
    private SSO sso;

    private ResourceLimit web_resource_limits;
    private ResourceLimit executor_resource_limits;
    private ResourceLimit database_resource_limits;

    @Data
    public static class Images {
        private String web;
        private String executor;
        private String database;
    }


    @Data
    public static class SSO {
        private String server_url;
        private String realm;
        private String client_id;
        private String ssl_required;
    }

    @Data
    public static class DB {
        private String username;
        private String password;
        private String database;
    }

    @Data
    public static class ResourceLimit {
        private String cpu_request;
        private String cpu_limit;
        private String mem_request;
        private String mem_limit;
    }

}

