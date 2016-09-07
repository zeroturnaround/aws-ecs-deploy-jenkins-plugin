package com.zeroturnaround.jenkins.plugin.ecs.deploy.steps;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.ContainerDefinition;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.RegisterTaskDefinitionResult;
import com.amazonaws.services.ecs.model.TaskDefinition;
import com.amazonaws.services.ecs.model.Volume;
import com.amazonaws.services.ecs.model.transform.TaskDefinitionJsonUnmarshaller;
import com.amazonaws.transform.JsonUnmarshallerContext;
import com.amazonaws.transform.JsonUnmarshallerContextImpl;
import com.amazonaws.util.json.Jackson;
import com.fasterxml.jackson.core.JsonParser;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.zeroturnaround.jenkins.plugin.ecs.deploy.JSONPathChange;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor(onConstructor = @__({@DataBoundConstructor}))
@Getter
public class RegisterTaskDefinitionStep extends ECSStep {

    public static final TypeRef<List<ContainerDefinition>> CONTAINER_DEF_TYPEREF = new TypeRef<List<ContainerDefinition>>() {};

    public static final TypeRef<List<Volume>> VOLUME_TYPEREF = new TypeRef<List<Volume>>() {};

    private final String family;

    private final String taskDefinitionFile;

    private final String taskDefinitionId;

    private final String taskDefinitionSource;

    private final List<JSONPathChange> changes;

    public String isTaskDefinitionSource(String value) {
        return (taskDefinitionSource != null ? taskDefinitionSource : "FILE").equalsIgnoreCase(value) ? "true" : "";
    }

    @Override
    public void perform(AmazonECS ecs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        String taskDefinitionSource = getTaskDefinition(build, ecs);

        DocumentContext context = JsonPath.parse(taskDefinitionSource);

        if (changes != null) {
            for (JSONPathChange change : changes) {
                final String expandedValue;
                try {
                    expandedValue = TokenMacro.expandAll(build, listener, change.getValue());
                } catch (MacroEvaluationException e) {
                    throw new AbortException(e.getMessage());
                }

                context = context.set(change.getPath(), expandedValue);
            }
        }

        String json = context.jsonString();

        final TaskDefinition taskDefinition;
        try {
            JsonParser jsonParser = Jackson.getObjectMapper().getFactory().createParser(json);

            JsonUnmarshallerContext unmarshallerContext = new JsonUnmarshallerContextImpl(jsonParser);
            taskDefinition = TaskDefinitionJsonUnmarshaller.getInstance().unmarshall(unmarshallerContext);
        } catch (Exception e) {
            throw new AbortException("Can't unmarshall task definition");
        }

        RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
                .withContainerDefinitions(taskDefinition.getContainerDefinitions())
                .withVolumes(taskDefinition.getVolumes());

        try {
            request.withFamily(TokenMacro.expandAll(build, listener, family));
        } catch (MacroEvaluationException e) {
            throw new AbortException(e.getMessage());
        }

        ecs.registerTaskDefinition(request);
    }

    protected String getTaskDefinition(AbstractBuild<?, ?> build, AmazonECS ecs) throws IOException, InterruptedException {
        if (taskDefinitionSource.equals("FILE")) {
            FilePath workspace = build.getWorkspace();
            if (workspace == null) {
                throw new AbortException("no workspace for " + build);
            }

            FilePath filePath = new FilePath(build.getWorkspace(), taskDefinitionFile);

            return filePath.readToString();
        } else if (taskDefinitionSource.equals("EXISTING_TASK_DEFINITION")) {
            DescribeTaskDefinitionResult taskDefinitionResult = ecs.describeTaskDefinition(
                    new DescribeTaskDefinitionRequest()
                            .withTaskDefinition(taskDefinitionId)
            );

            return Jackson.toJsonString(taskDefinitionResult.getTaskDefinition());
        } else {
            throw new AbortException("Unknown taskDefinition source: " + taskDefinitionSource);
        }
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSStep> {

        @Override
        public String getDisplayName() {
            return "Register Task Definition";
        }
    }
}
