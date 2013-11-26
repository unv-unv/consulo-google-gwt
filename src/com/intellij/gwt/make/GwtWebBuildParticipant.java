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

import com.intellij.compiler.impl.packagingCompiler.BuildInstructionBase;
import com.intellij.compiler.impl.packagingCompiler.FileCopyInstructionImpl;
import com.intellij.facet.FacetManager;
import com.intellij.gwt.facet.GwtFacet;
import com.intellij.gwt.facet.GwtFacetType;
import com.intellij.gwt.module.GwtModulesManager;
import com.intellij.gwt.module.model.GwtModule;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.javaee.web.make.CustomWebBuildParticipant;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.make.BuildInstruction;
import com.intellij.openapi.compiler.make.BuildRecipe;
import com.intellij.openapi.deployment.DeploymentUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;

import java.io.File;
import java.util.Collection;

/**
 * @author nik
 */
public class GwtWebBuildParticipant extends CustomWebBuildParticipant {
  private static final Key<Pair<GwtFacet, String>> GWT_MODULE_INFO_KEY = Key.create("GWT_MODULE_INFO_KEY");

  public void registerBuildInstructions(final WebFacet webFacet, BuildRecipe buildRecipe, CompileContext context) {
    Module module = webFacet.getModule();

    final Collection<GwtFacet> facets = FacetManager.getInstance(module).getFacetsByType(GwtFacetType.ID);
    for (GwtFacet facet : facets) {
      if (facet.getConfiguration().isRunGwtCompilerOnMake() && webFacet.equals(facet.getWebFacet())) {
        final GwtModule[] modules = GwtModulesManager.getInstance(module.getProject()).getGwtModules(module);
        for (GwtModule gwtModule : modules) {
          String qualifiedName = gwtModule.getQualifiedName();
          final File output = new File(GwtCompilerPaths.getOutputDirectory(facet), qualifiedName);
          String relativePath = facet.getConfiguration().getPackagingRelativePath(gwtModule);
          final FileCopyInstructionImpl instruction =
            new FileCopyInstructionImpl(output, true, module, DeploymentUtil.trimForwardSlashes(relativePath), null);
          buildRecipe.addInstruction(instruction);
          instruction.putUserData(GWT_MODULE_INFO_KEY, Pair.create(facet, qualifiedName));
        }
      }
    }
  }

  public static boolean isCopyGwtOutputInstruction(final BuildInstruction instruction) {
    return getGwtModuleInfo(instruction) != null;
  }

  public static Pair<GwtFacet, String> getGwtModuleInfo(final BuildInstruction instruction) {
    return ((BuildInstructionBase)instruction).getUserData(GWT_MODULE_INFO_KEY);
  }
}
