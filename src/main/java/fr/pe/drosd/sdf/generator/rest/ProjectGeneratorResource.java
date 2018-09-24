package fr.pe.drosd.sdf.generator.rest;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.enterprise.context.ApplicationScoped;
import javax.validation.constraints.NotNull;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

@ApplicationScoped
@Path("/generator")
public class ProjectGeneratorResource {
    private static final String THORNTAIL_VERSION = "2.2.0.Final";

    TemplateEngine engine;

    public ProjectGeneratorResource() {
        engine = new TemplateEngine();

        ClassLoaderTemplateResolver textTemplateResolver = new ClassLoaderTemplateResolver(getClass().getClassLoader());
        textTemplateResolver.setTemplateMode(TemplateMode.TEXT);
        Set<String> javaResolvablePattern = new HashSet<>(Arrays.asList("*.java"));
        textTemplateResolver.setResolvablePatterns(javaResolvablePattern);

        ClassLoaderTemplateResolver xmlTemplateResolver = new ClassLoaderTemplateResolver(getClass().getClassLoader());
        Set<String> xmlResolvablePattern = new HashSet<>(Arrays.asList("*.xml"));
        xmlTemplateResolver.setResolvablePatterns(xmlResolvablePattern);

        engine.addTemplateResolver(xmlTemplateResolver);
        engine.addTemplateResolver(textTemplateResolver);
    }

    @GET
    @Produces("application/zip")
    public Response generate(
            @QueryParam("sv") @DefaultValue(THORNTAIL_VERSION) String thorntailVersion,
            @QueryParam("g") @DefaultValue("com.example") @NotNull(message = "Parameter 'g' (Group Id) must not be null") String groupId,
            @QueryParam("a") @DefaultValue("demo") @NotNull(message = "Parameter 'a' (Artifact Id) must not be null") String artifactId,
            @QueryParam("d") List<String> dependencies)
            throws Exception {
        // Remove empty values
        dependencies.remove("");
        Context context = new Context();
        context.setVariable("groupId", groupId);
        context.setVariable("artifactId", artifactId);
        context.setVariable("dependencies", dependencies);
        context.setVariable("thorntailVersion", thorntailVersion);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry(artifactId + "/src/main/java/"));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry(artifactId + "/pom.xml"));
            zos.write(engine.process("templates/pom.tl.xml", context).getBytes());
            zos.closeEntry();

            if (enableJAXRS(dependencies)) {
                EndpointFilePathGenerator fpg = new EndpointFilePathGenerator(groupId, artifactId);
                context.setVariable("endpointPackage", fpg.getEndpointPackage());
                zos.putNextEntry(new ZipEntry(artifactId + fpg.getEndpointFilePath()));
                zos.write(engine.process("templates/HelloWorldEndpoint.tl.java", context).getBytes());
                zos.putNextEntry(new ZipEntry(artifactId + fpg.getApplicationPath()));
                zos.write(engine.process("templates/RestApplication.tl.java", context).getBytes());
                zos.closeEntry();
            }
        }

        return Response
                .ok(baos.toByteArray())
                .type("application/zip")
                .header("Content-Disposition", "attachment; filename=\"" + artifactId + ".zip\"")
                .build();
    }

    private boolean enableJAXRS(List<String> dependencies) {
        if (dependencies == null || dependencies.size() == 0) {
            return true;
        }
        return dependencies.stream().anyMatch(d -> d.contains("jaxrs") || d.contains("microprofile"));
    }

}
