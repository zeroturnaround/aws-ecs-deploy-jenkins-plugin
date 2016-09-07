package com.zeroturnaround.jenkins.plugin.ecs.deploy.steps;

import com.amazonaws.services.ecs.AmazonECS;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractDescribableImpl;
import hudson.model.BuildListener;

import java.io.IOException;

public abstract class ECSStep extends AbstractDescribableImpl<ECSStep> {

    abstract public void perform(AmazonECS ecs, AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException;

}
