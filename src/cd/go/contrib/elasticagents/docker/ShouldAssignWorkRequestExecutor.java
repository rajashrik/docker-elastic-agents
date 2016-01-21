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

import com.thoughtworks.go.plugin.api.response.DefaultGoPluginApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;

public class ShouldAssignWorkRequestExecutor implements RequestExecutor {
    private final DockerContainers containers;
    private final ShouldAssignWorkRequest request;

    public ShouldAssignWorkRequestExecutor(DockerContainers containers, ShouldAssignWorkRequest request) {
        this.containers = containers;
        this.request = request;
    }

    @Override
    public GoPluginApiResponse execute() {
        return DefaultGoPluginApiResponse.success("true");
    }
}
