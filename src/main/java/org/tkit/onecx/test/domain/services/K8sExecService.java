package org.tkit.onecx.test.domain.services;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tkit.onecx.test.domain.models.ServiceException;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ExecListener;
import io.fabric8.kubernetes.client.dsl.ExecWatch;

@ApplicationScoped
public class K8sExecService {

    private static final Logger log = LoggerFactory.getLogger(K8sExecService.class);

    @Inject
    KubernetesClient client;

    public K8sExecService(KubernetesClient client) {
        this.client = client;
    }

    public String execCommandOnPod(String podName, String... cmd) {
        try {
            Pod pod = client.pods().withName(podName).get();
            if (pod == null) {
                log.info("Pod with name '{}' not found not found.", podName);
                return null;
            }

            log.info("Running command: [{}] on pod [{}] in namespace [{}]",
                    Arrays.toString(cmd), pod.getMetadata().getName(), pod.getMetadata().getNamespace());

            CompletableFuture<String> data = new CompletableFuture<>();
            try (ExecWatch _e = execCmd(pod, data, cmd)) {
                return data.get(10, TimeUnit.SECONDS);
            }
        } catch (Exception ex) {
            throw new ServiceException(ex);
        }
    }

    private ExecWatch execCmd(Pod pod, CompletableFuture<String> data, String[] command) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        return client.pods()
                .inNamespace(pod.getMetadata().getNamespace())
                .withName(pod.getMetadata().getName())
                .writingOutput(out)
                .writingError(out)
                .usingListener(new SimpleListener(data, out))
                .exec(command);
    }

    public static class SimpleListener implements ExecListener {

        private final CompletableFuture<String> data;
        private final ByteArrayOutputStream output;

        public SimpleListener(CompletableFuture<String> data, ByteArrayOutputStream output) {
            this.data = data;
            this.output = output;
        }

        @Override
        public void onOpen() {
            log.info("Reading data... ");
        }

        @Override
        public void onFailure(Throwable t, Response failureResponse) {
            log.error("Reading data failed: {}", t.getMessage());
            data.completeExceptionally(t);
        }

        @Override
        public void onClose(int code, String reason) {
            log.info("Reading data exit with: {} and with reason: {}", code, reason);
            data.complete(output.toString());
        }
    }

}
