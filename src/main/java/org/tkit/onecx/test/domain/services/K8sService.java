package org.tkit.onecx.test.domain.services;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.fabric8.kubernetes.api.model.LabelSelector;
import io.fabric8.kubernetes.client.KubernetesClient;

@ApplicationScoped
public class K8sService {

    private static final Logger log = LoggerFactory.getLogger(K8sService.class);

    @Inject
    KubernetesClient client;

    public List<String> findPodsForService(String name) {
        var service = client.services().withName(name).get();
        var labels = new LabelSelector();
        labels.setMatchLabels(service.getSpec().getSelector());

        return client.pods().withLabelSelector(labels).list().getItems().stream()
                .map(pod -> pod.getMetadata().getName())
                .toList();
    }

}
