package org.tkit.onecx.test.domain.services;

import java.util.List;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.client.KubernetesClient;

@ApplicationScoped
public class K8sService {

    @Inject
    KubernetesClient client;

    @ConfigProperty(name = "quarkus.kubernetes-client.namespace")
    String namespace;

    public Map<String, String> findServiceSelector(String name) {
        var service = client.services().inNamespace(namespace).withName(name).get();
        if (service == null) {
            return Map.of();
        }
        return service.getSpec().getSelector();
    }

    public List<String> findPodsBySelector(Map<String, String> selector) {
        var labels = new LabelSelector();
        labels.setMatchLabels(selector);

        return client.pods().withLabelSelector(labels).list().getItems().stream()
                .map(pod -> pod.getMetadata().getName())
                .toList();
    }

}
