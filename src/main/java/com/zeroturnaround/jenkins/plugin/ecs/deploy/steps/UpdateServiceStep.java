package com.zeroturnaround.jenkins.plugin.ecs.deploy.steps;

import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Deployment;
import com.amazonaws.services.ecs.model.DescribeServicesRequest;
import com.amazonaws.services.ecs.model.DescribeServicesResult;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import hudson.AbortException;
import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.tokenmacro.MacroEvaluationException;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor(onConstructor = @__({@DataBoundConstructor}))
@Getter
public class UpdateServiceStep extends ECSStep {

    private static final int TASK_DEFINITION_ID_LIMIT = 45;

    private static final String TABLE_FORMAT = "%" + TASK_DEFINITION_ID_LIMIT + "s | %8s | %7s | %7s | %7s | %30s\n";

    private static final String TABLE_LINE = StringUtils.repeat("-", 120);

    private static final int RETRY_ATTEMPTS = 60;

    private final String cluster;

    private final String service;

    private final String taskDefinition;

    @Override
    public void perform(AmazonECS ecs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
        DescribeServicesRequest describeServicesRequest = new DescribeServicesRequest();

        try {
            describeServicesRequest
                    .withCluster(TokenMacro.expandAll(build, listener, cluster))
                    .withServices(TokenMacro.expandAll(build, listener, service));
        } catch (MacroEvaluationException e) {
            throw new AbortException(e.getMessage());
        }

        DescribeServicesResult describeServicesResult = ecs.describeServices(describeServicesRequest);

        UpdateServiceRequest request = new UpdateServiceRequest();

        try {
            request
                    .withCluster(describeServicesRequest.getCluster())
                    .withService(describeServicesRequest.getServices().get(0))
                    .withDesiredCount(describeServicesResult.getServices().get(0).getDesiredCount())
                    .withTaskDefinition(TokenMacro.expandAll(build, listener, taskDefinition));
        } catch (MacroEvaluationException e) {
            throw new AbortException(e.getMessage());
        }

        ecs.updateService(request);

        PrintStream logger = listener.getLogger();
        logger.println("Current deployments:");

        int attemptsLeft = RETRY_ATTEMPTS;
        while(attemptsLeft-- > 0) {
            TimeUnit.SECONDS.sleep(5);

            DescribeServicesResult servicesResult = ecs.describeServices(
                    new DescribeServicesRequest()
                            .withCluster(request.getCluster())
                            .withServices(request.getService())
            );

            logger.printf(TABLE_FORMAT, "Task definition ID", "Status", "Desired", "Pending", "Running", "Created at");
            logger.println(TABLE_LINE);

            boolean shouldRetry = false;
            for (Deployment deployment : servicesResult.getServices().get(0).getDeployments()) {
                logger.printf(
                        TABLE_FORMAT,
                        StringUtils.right(deployment.getTaskDefinition(), TASK_DEFINITION_ID_LIMIT),
                        deployment.getStatus(),
                        deployment.getDesiredCount() + "",
                        deployment.getPendingCount() + "",
                        deployment.getRunningCount() + "",
                        deployment.getCreatedAt()
                );

                if ("PRIMARY".equalsIgnoreCase(deployment.getStatus())) {
                    // Retry if it's PRIMARY and desired !== running
                    shouldRetry = shouldRetry || !deployment.getDesiredCount().equals(deployment.getRunningCount());
                } else {
                    // Retry if we found a non-primary task
                    shouldRetry = true;
                }
            }
            logger.println(TABLE_LINE);
            logger.println();

            if (!shouldRetry) {
                return;
            }
        }

        throw new AbortException("Update didn't succeed after " + RETRY_ATTEMPTS + " attempts");
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<ECSStep> {

        @Override
        public String getDisplayName() {
            return "Update service";
        }
    }
}
