package org.basinmc.maven.plugins.bsdiff;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

import io.sigpipe.jbsdiff.InvalidHeaderException;
import io.sigpipe.jbsdiff.ui.FileUI;

/**
 * Generates a binary diff between any file and an artifact within the local maven repository.
 *
 * @author <a href="mailto:johannesd@torchmind.com">Johannes Donath</a>
 */
@Mojo(
        name = "generate-diff",
        threadSafe = true,
        defaultPhase = LifecyclePhase.VERIFY
)
public class DiffMojo extends AbstractMojo {

    /**
     * Specifies the artifact to compare against.
     */
    @Parameter(required = true)
    private String artifact;

    /**
     * Specifies the input file.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.jar")
    private File inputFile;

    /**
     * Specifies the file to write the diff to.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.diff")
    private File diffFile;

    /**
     * Specifies the diff compression algorithm.
     */
    @Parameter(defaultValue = "bzip2")
    private String diffCompression;

    /**
     * Specifies whether the resulting diff shall be attached.
     */
    @Parameter(defaultValue = "true")
    private boolean attach;

    @Parameter(readonly = true, defaultValue = "${project}")
    private MavenProject project;

    @Component
    private MavenSession session;

    @Component
    private MavenProjectHelper projectHelper;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver artifactResolver;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            String[] elements = this.artifact.split(":");

            if (elements.length != 3) {
                // TODO: Better support for more complex coordinates
                throw new MojoFailureException("Could not extract artifact coordinate from configuration");
            }

            Artifact artifact = this.artifactFactory.createBuildArtifact(elements[0], elements[1], elements[2], "jar");
            this.artifactResolver.resolve(artifact, Collections.emptyList(), this.session.getLocalRepository());

            this.getLog().info("Generating diff ...");
            FileUI.diff(artifact.getFile(), this.inputFile, this.diffFile, this.diffCompression);
            this.getLog().info("Diff was written to " + this.diffFile.toString());

            if (this.attach) {
                this.getLog().info("Attaching diff to project artifact");
                this.projectHelper.attachArtifact(this.project, this.diffFile, "diff");
            }
        } catch (CompressorException e) {
            throw new MojoFailureException("Could not compress resulting diff: " + e.getMessage(), e);
        } catch (InvalidHeaderException | IOException e) {
            throw new MojoFailureException("Could not generate diff: " + e.getMessage(), e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoFailureException("Could not find artifact: " + e.getMessage(), e);
        } catch (ArtifactResolutionException e) {
            throw new MojoFailureException("Could not resolve artifact: " + e.getMessage(), e);
        }
    }
}
