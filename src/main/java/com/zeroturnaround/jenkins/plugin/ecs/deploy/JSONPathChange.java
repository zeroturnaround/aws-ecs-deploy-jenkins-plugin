package com.zeroturnaround.jenkins.plugin.ecs.deploy;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.kohsuke.stapler.DataBoundConstructor;

@RequiredArgsConstructor(onConstructor = @__({ @DataBoundConstructor}))
@Getter
public class JSONPathChange extends AbstractDescribableImpl<JSONPathChange> {

    private final String path;

    private final String value;

    @Extension
    public static class DescriptorImpl extends Descriptor<JSONPathChange> {

        @Override
        public String getDisplayName() {
            return "";
        }
    }
}
