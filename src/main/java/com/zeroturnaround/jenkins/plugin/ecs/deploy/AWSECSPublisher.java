package com.zeroturnaround.jenkins.plugin.ecs.deploy;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.AmazonECSClient;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionRequest;
import com.amazonaws.services.ecs.model.DescribeTaskDefinitionResult;
import com.amazonaws.util.json.Jackson;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.zeroturnaround.jenkins.plugin.ecs.deploy.steps.ECSStep;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Computer;
import hudson.model.ItemGroup;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Builder;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor(onConstructor = @__({@DataBoundConstructor}))
@Getter
public class AWSECSPublisher extends Builder {

    private final Regions region;

    private final String credentialsId;

    private final List<ECSStep> steps;

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.BUILD;
    }

    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        AmazonWebServicesCredentials credentials = CredentialsMatchers.firstOrNull(
                CredentialsProvider.lookupCredentials(AmazonWebServicesCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, (List<DomainRequirement>) null),
                CredentialsMatchers.withId(credentialsId)
        );

        if (credentials == null) {
            throw new AbortException("Unknown credentials with id: " + credentialsId);
        }

        AmazonECS ecs = new AmazonECSClient(credentials);

        ecs.setRegion(Region.getRegion(region));

        for (ECSStep step : steps) {
            step.perform(ecs, build, launcher, listener);
        }

        return true;
    }

    @Extension
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {

        public DescriptorImpl() {
            load();
        }

        @Override
        public String getDisplayName() {
            return "Deploy to AWS Elastic Container Service";
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath ItemGroup<?> context) {
            AccessControlled accessControlled = context instanceof AccessControlled ? (AccessControlled) context : Jenkins.getInstance();

            if (!accessControlled.hasPermission(Computer.CONFIGURE)) {
                return new ListBoxModel();
            }

            List<AmazonWebServicesCredentials> awsCredentials = CredentialsProvider.lookupCredentials(
                    AmazonWebServicesCredentials.class,
                    context,
                    ACL.SYSTEM,
                    (List<DomainRequirement>) null
            );

            return new StandardListBoxModel().withAll(awsCredentials);

        }
    }
}
