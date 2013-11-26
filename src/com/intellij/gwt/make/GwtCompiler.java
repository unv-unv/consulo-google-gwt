/*
 * Copyright 2000-2006 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.gwt.make;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.impl.CompilerUtil;
import com.intellij.execution.CompileStepBeforeRun;
import com.intellij.execution.configurations.*;
import com.intellij.facet.FacetManager;
import com.intellij.gwt.GwtBundle;
import com.intellij.gwt.sdk.GwtVersion;
import com.intellij.gwt.facet.GwtFacet;
import com.intellij.gwt.facet.GwtFacetType;
import com.intellij.gwt.module.GwtModulesManager;
import com.intellij.gwt.module.model.GwtModule;
import com.intellij.javaee.deployment.DeploymentManager;
import com.intellij.javaee.deployment.DeploymentModel;
import com.intellij.javaee.run.configuration.CommonModel;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectClasspathTraversing;
import com.intellij.openapi.roots.ProjectRootsTraversing;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathsList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GwtCompiler implements ClassInstrumentingCompiler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.gwt.make.GwtCompiler");
  private Project myProject;
  private GwtModulesManager myGwtModulesManager;
  @NonNls public static final String LOG_LEVEL_ARGUMENT = "-logLevel";
  @NonNls public static final String GEN_AGRUMENT = "-gen";
  @NonNls public static final String STYLE_ARGUMENT = "-style";

  public GwtCompiler(Project project, GwtModulesManager modulesManager) {
    myProject = project;
    myGwtModulesManager = modulesManager;
  }

  @NotNull
  public String getDescription() {
    return GwtBundle.message("compiler.description.google.compiler");
  }

  public boolean validateConfiguration(CompileScope scope) {
    return true;
  }

  public ValidityState createValidityState(DataInput in) throws IOException {
    return new GwtItemValidityState(in);
  }

  @NotNull
  public ProcessingItem[] getProcessingItems(final CompileContext context) {
    final ArrayList<ProcessingItem> result = new ArrayList<ProcessingItem>();
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        CompilerConfiguration compilerConfiguration = CompilerConfiguration.getInstance(myProject);
        RunConfiguration runConfiguration = CompileStepBeforeRun.getRunConfiguration(context);
        final Module[] modules = context.getCompileScope().getAffectedModules();
        for (Module module : modules) {
          GwtFacet facet = FacetManager.getInstance(module).getFacetByType(GwtFacetType.ID);
          if (facet == null || !facet.getConfiguration().isRunGwtCompilerOnMake()) continue;

          if (runConfiguration != null) {
            WebFacet webFacet = facet.getWebFacet();
            if (!(runConfiguration instanceof CommonModel) || webFacet == null) continue;

            final CommonModel commonModel = (CommonModel)runConfiguration;
            DeploymentModel model = commonModel.getDeploymentModel(webFacet);
            if (model == null || !DeploymentManager.getInstance(myProject).isModuleDeployedOrIncludedInDeployed(model)) {
              continue;
            }
          }

          final GwtModule[] gwtModules = myGwtModulesManager.getGwtModules(module);
          for (GwtModule gwtModule : gwtModules) {
            if (myGwtModulesManager.isLibraryModule(gwtModule)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("GWT module " + gwtModule.getQualifiedName() + " has not entry points and html files so it won't be compiled.");
              }
              continue;
            }

            VirtualFile moduleFile = gwtModule.getModuleFile();
            if (compilerConfiguration.isExcludedFromCompilation(moduleFile)) {
              if (LOG.isDebugEnabled()) {
                LOG.debug("GWT module '" + gwtModule.getQualifiedName() + "' is excluded from compilation.");
              }
              continue;
            }

            addFilesRecursively(gwtModule, facet, moduleFile, result);

            for (VirtualFile file : gwtModule.getPublicRoots()) {
              addFilesRecursively(gwtModule, facet, file, result);
            }
            for (VirtualFile file : gwtModule.getSourceRoots()) {
              addFilesRecursively(gwtModule, facet, file, result);
            }
          }
        }
      }
    });
    return result.toArray(new ProcessingItem[result.size()]);
  }

  private static void addFilesRecursively(final GwtModule module, GwtFacet facet, final VirtualFile file, final List<ProcessingItem> result) {
    if (!file.isValid() || FileTypeManager.getInstance().isFileIgnored(file.getName())) {
      return;
    }

    if (file.isDirectory()) {
      final VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        addFilesRecursively(module, facet, child, result);
      }
    }
    else {
      result.add(new GwtModuleFileProcessingItem(facet, module, file));
    }
  }

  public ProcessingItem[] process(final CompileContext context, ProcessingItem[] items) {
    MultiValuesMap<Pair<GwtFacet, GwtModule>, GwtModuleFileProcessingItem> module2Items = new MultiValuesMap<Pair<GwtFacet, GwtModule>, GwtModuleFileProcessingItem>();
    for (ProcessingItem item : items) {
      final GwtModuleFileProcessingItem processingItem = (GwtModuleFileProcessingItem)item;
      module2Items.put(Pair.create(processingItem.getFacet(), processingItem.getModule()), processingItem);
    }

    final ArrayList<ProcessingItem> compiled = new ArrayList<ProcessingItem>();

    for (Pair<GwtFacet, GwtModule> pair : module2Items.keySet()) {
      if (compile(context, pair.getFirst(), pair.getSecond())) {
        compiled.addAll(module2Items.get(pair));
      }
    }

    return compiled.toArray(new ProcessingItem[compiled.size()]);
  }

  private static boolean compile(final CompileContext context, final GwtFacet facet, final GwtModule gwtModule) {
    final Ref<VirtualFile> gwtModuleFile = Ref.create(null);
    final Ref<File> outputDirRef = Ref.create(null);
    final Ref<String> gwtModuleName = Ref.create(null);
    final Module module = new ReadAction<Module>() {
      protected void run(final Result<Module> result) {
        gwtModuleName.set(gwtModule.getQualifiedName());
        gwtModuleFile.set(gwtModule.getModuleFile());
        outputDirRef.set(GwtCompilerPaths.getOutputDirectory(facet));
        result.setResult(gwtModule.getModule());
      }
    }.execute().getResultObject();

    final File generatedDir = GwtCompilerPaths.getDirectoryForGenerated(module);
    generatedDir.mkdirs();
    File outputDir = outputDirRef.get();
    outputDir.mkdirs();

    try {
      GeneralCommandLine commandLine = CommandLineBuilder.createFromJavaParameters(createCommand(facet, gwtModule, outputDir, generatedDir, gwtModuleName.get()));
      if (LOG.isDebugEnabled()) {
        LOG.debug("GWT Compiler command line: " + commandLine.getCommandLineString());
      }
      commandLine.setWorkingDirectory(outputDir);
      context.getProgressIndicator().setText2(GwtBundle.message("progress.text.compiling.gwt.module.0", gwtModuleName.get()));

      GwtCompilerProcessHandler handler = new GwtCompilerProcessHandler(commandLine.createProcess(), context, gwtModuleFile.get().getUrl(), facet.getModule());
      handler.startNotify();
      handler.waitFor();
    }
    catch (Exception e) {
      LOG.info(e);
      context.addMessage(CompilerMessageCategory.ERROR, e.getMessage(), null, -1, -1);
      return false;
    }

    CompilerUtil.refreshIODirectories(Collections.singletonList(outputDir));

    return context.getMessageCount(CompilerMessageCategory.ERROR) == 0;
  }

  private static JavaParameters createCommand(GwtFacet facet, final GwtModule module, final File outputDir, final File generatedDir,
                                              final String gwtModuleName) {
    final JavaParameters javaParameters = new JavaParameters();
    javaParameters.setJdk(ModuleRootManager.getInstance(module.getModule()).getSdk());
    ParametersList vmParameters = javaParameters.getVMParametersList();
    vmParameters.addParametersString(facet.getConfiguration().getAdditionalCompilerParameters());
    vmParameters.replaceOrAppend("-Xmx", "-Xmx" + facet.getConfiguration().getCompilerMaxHeapSize() + "m");

    createClasspath(facet, module.getModule(), javaParameters.getClassPath());
    final GwtVersion sdkVersion = facet.getSdkVersion();
    javaParameters.setMainClass(sdkVersion.getCompilerClassName());
    ParametersList parameters = javaParameters.getProgramParametersList();
    parameters.add(LOG_LEVEL_ARGUMENT);
    parameters.add("TRACE");
    parameters.add(sdkVersion.getCompilerOutputDirParameterName());
    parameters.add(outputDir.getAbsolutePath());
    parameters.add(GEN_AGRUMENT);
    parameters.add(generatedDir.getAbsolutePath());
    parameters.add(STYLE_ARGUMENT);
    parameters.add(facet.getConfiguration().getOutputStyle().getId());
    parameters.add(gwtModuleName);
    return javaParameters;
  }

  private static void createClasspath(final GwtFacet facet, Module module, final PathsList classPath) {
    ProjectRootsTraversing.collectRoots(module, new ProjectRootsTraversing.RootTraversePolicy(
      ProjectRootsTraversing.RootTraversePolicy.PRODUCTION_SOURCES, 
      ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES,
      ProjectRootsTraversing.RootTraversePolicy.ADD_CLASSES,
      ProjectRootsTraversing.RootTraversePolicy.RECURSIVE), classPath);

    ProjectRootsTraversing.collectRoots(module, new ProjectRootsTraversing.RootTraversePolicy(
      ProjectClasspathTraversing.GENERAL_OUTPUT, null, null,
      ProjectRootsTraversing.RootTraversePolicy.RECURSIVE), classPath);

    classPath.addFirst(facet.getConfiguration().getSdk().getDevJarPath());
  }
}
