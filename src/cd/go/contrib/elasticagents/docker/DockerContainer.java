/*
 * Copyright 2016 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cd.go.contrib.elasticagents.docker;

import com.spotify.docker.client.*;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.ExecState;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;

import static cd.go.contrib.elasticagents.docker.Constants.CREATED_BY_LABEL_KEY;
import static cd.go.contrib.elasticagents.docker.DockerPlugin.LOG;

public class DockerContainer {
    private String id;
    private DateTime createdAt;
    private DockerClient docker;

    public DockerContainer(DockerClient docker) {
        this.docker = docker;
    }

    public DockerContainer(DockerClient docker, String id) {
        this.docker = docker;
        this.id = id;
    }

    public DockerContainer(DefaultDockerClient docker, String id, DateTime createdAt) {
        this.docker = docker;
        this.id = id;
        this.createdAt = createdAt;
    }

    public DateTime createdAt() throws DockerException, InterruptedException {
        if (this.createdAt == null) {
            this.createdAt = new DateTime(docker.inspectContainer(id).created());
        }

        return this.createdAt;
    }

    public String id() {
        return id;
    }

    public static DockerContainer find(DefaultDockerClient docker, String containerId) throws DockerException, InterruptedException {
        ContainerInfo containerInfo = docker.inspectContainer(containerId);
        return new DockerContainer(docker, containerId, new DateTime(containerInfo.created()));
    }

    public void terminate() throws DockerException, InterruptedException {
        try {
            LOG.debug("Terminating instance " + this.id() + ".");
            docker.stopContainer(id, 2);
            docker.removeContainer(id);
        } catch (ContainerNotFoundException ignore) {
            LOG.warn("Cannot terminate a container that does not exist " + id);
        }
    }

    public DockerContainer initialize(String autoRegisterKey, Collection<String> resources, String environment) throws DockerException, InterruptedException, IOException {
        HashMap<String, String> labels = new HashMap<>();
        labels.put(CREATED_BY_LABEL_KEY, Constants.PLUGIN_ID);
        ContainerCreation container = docker.createContainer(ContainerConfig.builder().
                image("gocd/ubuntu-docker-elastic-agent").
                openStdin(true).
                cmd("bash").
                labels(labels).
                build());
        id = container.id();

        LOG.debug("Created container " + id);

        docker.startContainer(id);

        execCommand(id, false, "mkdir", "-p", "/go-agent/config");

        File tempDirectory = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
        File configDir = new File(tempDirectory, "config");

        tempDirectory.mkdirs();
        configDir.mkdirs();
        File autoregisterPropertiesFile = new File(configDir, "autoregister.properties");

        try {
            saveAutoRegisterProperties(id, autoregisterPropertiesFile, autoRegisterKey, resources, environment);
            FileUtils.copyFile(new File("/Users/ketanpadegaonkar/projects/gocd/gocd/agent/target/agent.jar"), new File(tempDirectory, "agent.jar"));
            LOG.debug("Copying files to container " + id);
            docker.copyToContainer(tempDirectory.toPath(), id, "/go-agent");
            LOG.debug("Done copying files to container " + id);
        } finally {
            FileUtils.deleteDirectory(tempDirectory);
        }

        LOG.debug("Starting agent process on container " + id);
        execCommand(id, false, "bash", "-c", "cd /go-agent && ((java -jar agent.jar https://localhost.ketanpkr.in:8154/go > agent.stdout.log 2>&1 & disown)& disown)");
        LOG.debug("Agent should now be ready in a moment...");
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DockerContainer that = (DockerContainer) o;

        return id != null ? id.equals(that.id) : that.id == null;
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

    private void saveAutoRegisterProperties(String containerId, File autoregisterPropertiesFile, String autoRegisterKey, Collection<String> resources, String environment) throws IOException {
        FileWriter fileWriter = new FileWriter(autoregisterPropertiesFile);

        Properties properties = new Properties();
        if (StringUtils.isNotBlank(autoRegisterKey)) {
            properties.put("agent.auto.register.key", autoRegisterKey);
        }

        properties.put("agent.auto.register.resources", StringUtils.join(resources, ", "));

        if (StringUtils.isNotBlank(environment)) {
            properties.put("agent.auto.register.environments", environment);
        }

        properties.put("agent.auto.register.elasticAgent.agentId", containerId);
        properties.put("agent.auto.register.elasticAgent.pluginId", Constants.PLUGIN_ID);
        properties.store(fileWriter, "");
        fileWriter.close();
    }

    private void execCommand(String containerId, boolean detach, String... cmd) throws DockerException, InterruptedException {
        String execId = docker.execCreate(containerId, cmd);
        if (detach) {
            docker.execStart(execId, DockerClient.ExecStartParameter.DETACH);
            return;
        }

        LogStream logStream = docker.execStart(execId);

        ExecState execState;
        while (true) {
            execState = docker.execInspect(execId);
            if (execState.running()) {
                Thread.sleep(100);
            } else {
                break;
            }
        }

        if (execState.exitCode() != 0) {
            throw new RuntimeException("Could not execute command. The status code was " + execState.exitCode() + ". The output was: " + logStream.readFully());
        }
    }


}
