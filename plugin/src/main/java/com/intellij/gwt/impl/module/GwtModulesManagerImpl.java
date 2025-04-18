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

package com.intellij.gwt.impl.module;

import com.intellij.gwt.base.module.index.GwtHtmlFileIndex;
import com.intellij.gwt.module.GwtModulesManager;
import com.intellij.gwt.module.model.GwtEntryPoint;
import com.intellij.gwt.module.model.GwtModule;
import com.intellij.java.language.psi.JavaDirectoryService;
import com.intellij.java.language.psi.JavaPsiFacade;
import com.intellij.java.language.psi.PsiClass;
import com.intellij.java.language.psi.PsiJavaPackage;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.content.ContentIterator;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.xml.ide.highlighter.HtmlFileType;
import consulo.xml.ide.highlighter.XmlFileType;
import consulo.xml.lang.html.HTMLLanguage;
import consulo.xml.psi.XmlRecursiveElementVisitor;
import consulo.xml.psi.xml.XmlFile;
import consulo.xml.psi.xml.XmlTag;
import consulo.xml.util.xml.DomFileElement;
import consulo.xml.util.xml.DomManager;
import consulo.xml.util.xml.DomService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.*;

/**
 * @author nik
 */
@ServiceImpl
@Singleton
public class GwtModulesManagerImpl extends GwtModulesManager
{
	private static final Key<CachedValue<Set<GwtModule>>> CACHED_GWT_INHERITED_MODULES = Key.create("CACHED_GWT_INHERITED_MODULES");
	private Project myProject;
	private ProjectFileIndex myProjectFileIndex;

	@Inject
	public GwtModulesManagerImpl(final Project project, ProjectFileIndex projectFileIndex)
	{
		myProject = project;
		myProjectFileIndex = projectFileIndex;
	}

	@Override
	@Nonnull
	public GwtModule[] getAllGwtModules()
	{
		return getGwtModules(GlobalSearchScope.allScope(myProject));
	}

	private GwtModule[] getGwtModules(@Nonnull GlobalSearchScope scope)
	{
		if(!DumbService.isDumb(myProject))
		{
			return new GwtModule[0];
		}

		final GwtModulesFinder finder = new GwtModulesFinder(myProject);
		final Collection<VirtualFile> candidates = DomService.getInstance().getDomFileCandidates(GwtModule.class, myProject, scope);
		for(VirtualFile file : candidates)
		{
			if(myProjectFileIndex.isInSource(file) || myProjectFileIndex.isInResource(file) || myProjectFileIndex.isInLibraryClasses(file))
			{
				finder.processFile(file);
			}
		}

		final List<GwtModule> list = finder.getResults();
		return list.toArray(new GwtModule[list.size()]);
	}

	@Override
	@Nonnull
	public GwtModule[] getGwtModules(@Nonnull final Module module)
	{
		return getGwtModules(GlobalSearchScope.moduleScope(module));
	}


	@Override
	@Nullable
	public GwtModule findGwtModuleByClientSourceFile(@Nonnull VirtualFile file)
	{
		List<GwtModule> gwtModules = findGwtModulesByClientSourceFile(file);
		return !gwtModules.isEmpty() ? gwtModules.get(0) : null;
	}

	@Override
	@Nonnull
	public List<GwtModule> findGwtModulesByClientSourceFile(@Nonnull final VirtualFile file)
	{
		return findModulesByClientOrPublicFile(file, true, false);
	}

	@Override
	@Nullable
	public GwtModule findGwtModuleByClientOrPublicFile(@Nonnull VirtualFile file)
	{
		List<GwtModule> gwtModules = findModulesByClientOrPublicFile(file, true, true);
		return !gwtModules.isEmpty() ? gwtModules.get(0) : null;
	}

	@Nonnull
	private List<GwtModule> findModulesByClientOrPublicFile(final VirtualFile file, final boolean clientFileAllowed, final boolean publicFileAllowed)
	{
		final GwtModulesFinder finder = new GwtModulesFinder(myProject);
		VirtualFile parent = file.getParent();
		while(parent != null && (myProjectFileIndex.isInSource(parent) || myProjectFileIndex.isInLibraryClasses(parent)))
		{
			finder.processChildren(parent);
			parent = parent.getParent();
		}

		ArrayList<GwtModule> gwtModules = new ArrayList<GwtModule>();
		for(GwtModule module : finder.getResults())
		{
			if(clientFileAllowed)
			{
				final List<VirtualFile> sourceRoots = module.getSourceRoots();
				for(VirtualFile sourceRoot : sourceRoots)
				{
					if(VirtualFileUtil.isAncestor(sourceRoot, file, false))
					{
						gwtModules.add(module);
					}
				}
			}
			if(publicFileAllowed)
			{
				final List<VirtualFile> publicRoots = module.getPublicRoots();
				for(VirtualFile publicRoot : publicRoots)
				{
					if(VirtualFileUtil.isAncestor(publicRoot, file, false))
					{
						gwtModules.add(module);
					}
				}
			}
		}
		return gwtModules;
	}

	//todo[nik] return all files
	@Override
	@Nullable
	public XmlFile findHtmlFileByModule(@Nonnull GwtModule module)
	{
		final Collection<VirtualFile> htmlFiles = GwtHtmlFileIndex.getHtmlFilesByModule(myProject, module.getQualifiedName());
		if(htmlFiles.isEmpty())
		{
			return null;
		}

		final VirtualFile parent = module.getModuleDirectory();
		final VirtualFile defaultFile = parent.findFileByRelativePath(DEFAULT_PUBLIC_PATH + "/" + module.getShortName() + "." + HtmlFileType.INSTANCE
				.getDefaultExtension());
		final VirtualFile htmlFile;
		if(defaultFile != null && htmlFiles.contains(defaultFile))
		{
			htmlFile = defaultFile;
		}
		else
		{
			htmlFile = htmlFiles.iterator().next();
		}

		final FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(htmlFile);
		if(viewProvider == null)
		{
			return null;
		}

		return (XmlFile) viewProvider.getPsi(HTMLLanguage.INSTANCE);
	}

	@Override
	@Nullable
	public PsiElement findTagById(@Nonnull XmlFile htmlFile, final String id)
	{
		final Map<String, XmlTag> id2Tag = getHtmlId2TagMap(htmlFile);
		return id2Tag.get(id);
	}

	private static Map<String, XmlTag> getHtmlId2TagMap(final XmlFile htmlFile)
	{
		final Map<String, XmlTag> id2Tag = new HashMap<String, XmlTag>();
		htmlFile.accept(new XmlRecursiveElementVisitor()
		{
			@Override
			public void visitXmlTag(XmlTag tag)
			{
				final String elementId = tag.getAttributeValue("id");
				if(elementId != null)
				{
					id2Tag.put(elementId, tag);
				}
				super.visitXmlTag(tag);
			}
		});
		return id2Tag;
	}

	@Override
	public boolean isGwtModuleFile(final VirtualFile file)
	{
		return file.getName().endsWith(GWT_XML_SUFFIX) && myProjectFileIndex.isInSourceContent(file);
	}

	@Override
	public boolean isInheritedOrSelf(GwtModule gwtModule, GwtModule inheritedModule)
	{
		final Set<GwtModule> set = getInheritedModules(gwtModule);
		return set.contains(inheritedModule);
	}

	private Set<GwtModule> getInheritedModules(final GwtModule gwtModule)
	{
		CachedValue<Set<GwtModule>> cachedValue = gwtModule.getModuleXmlFile().getUserData(CACHED_GWT_INHERITED_MODULES);
		if(cachedValue == null)
		{
			cachedValue = CachedValuesManager.getManager(myProject).createCachedValue(new CachedValueProvider<Set<GwtModule>>()
			{
				@Override
				public Result<Set<GwtModule>> compute()
				{
					final Set<GwtModule> set = new HashSet<GwtModule>();
					Module module = gwtModule.getModule();
					List<Object> dependencies = new ArrayList<Object>();
					GlobalSearchScope scope = module != null ? GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module) : GlobalSearchScope
							.allScope(myProject);
					collectAllInherited(gwtModule, set, scope, dependencies);
					dependencies.add(ProjectRootManager.getInstance(myProject));
					return Result.create(set, dependencies.toArray(new Object[dependencies.size()]));
				}
			}, false);
			gwtModule.getModuleXmlFile().putUserData(CACHED_GWT_INHERITED_MODULES, cachedValue);
		}
		return cachedValue.getValue();
	}

	@Override
	public boolean isLibraryModule(GwtModule module)
	{
		return module.getEntryPoints().isEmpty() && findHtmlFileByModule(module) == null;
	}

	@Override
	public boolean isUnderGwtModule(final VirtualFile file)
	{
		final GwtModulesFinder finder = new GwtModulesFinder(myProject);
		VirtualFile parent = file.getParent();
		while(parent != null && myProjectFileIndex.isInSource(parent))
		{
			finder.processChildren(parent);
			parent = parent.getParent();
		}
		return !finder.getResults().isEmpty();
	}

	private static void collectAllInherited(final GwtModule gwtModule, final Set<GwtModule> set, final GlobalSearchScope scope,
			final List<Object> dependencies)
	{
		if(!set.add(gwtModule))
		{
			return;
		}

		dependencies.add(gwtModule.getModuleXmlFile());
		for(GwtModule module : gwtModule.getInherited(scope))
		{
			collectAllInherited(module, set, scope, dependencies);
		}
	}

	@Override
	@Nullable
	public GwtModule findGwtModuleByName(final @Nonnull String qualifiedName, final GlobalSearchScope scope)
	{
		final GwtModule[] gwtModules = findGwtModulesByName(qualifiedName, scope);
		return gwtModules.length > 0 ? gwtModules[0] : null;
	}

	@Override
	@Nullable
	public String getPathFromPublicRoot(@Nonnull final GwtModule gwtModule, @Nonnull VirtualFile file)
	{
		for(VirtualFile root : gwtModule.getPublicRoots())
		{
			if(VirtualFileUtil.isAncestor(root, file, false))
			{
				return VirtualFileUtil.getRelativePath(file, root, '/');
			}
		}
		return null;
	}

	private GwtModule[] findGwtModulesByName(final String qualifiedName, final GlobalSearchScope scope)
	{
		List<GwtModule> modules = new ArrayList<GwtModule>();
		String name = qualifiedName;
		String packageName = "";
		do
		{
			final PsiJavaPackage psiPackage = JavaPsiFacade.getInstance(myProject).findPackage(packageName);
			if(psiPackage != null)
			{
				final PsiDirectory[] directories = psiPackage.getDirectories(scope);
				for(PsiDirectory directory : directories)
				{
					final PsiFile psiFile = directory.findFile(name + GWT_XML_SUFFIX);
					if(psiFile instanceof XmlFile)
					{
						final DomFileElement<GwtModule> fileElement = DomManager.getDomManager(myProject).getFileElement((XmlFile) psiFile, GwtModule.class);
						if(fileElement != null)
						{
							modules.add(fileElement.getRootElement());
						}
					}
				}
			}

			int dot = name.indexOf('.');
			if(dot == -1)
			{
				break;
			}

			final String shortName = name.substring(0, dot);
			packageName = packageName.length() > 0 ? packageName + "." + shortName : shortName;
			name = name.substring(dot + 1);
		}
		while(true);

		return modules.toArray(new GwtModule[modules.size()]);
	}

	@Override
	public String[] getAllIds(@Nonnull XmlFile htmlFile)
	{
		final Set<String> idSet = getHtmlId2TagMap(htmlFile).keySet();
		return ArrayUtil.toStringArray(idSet);
	}

	@Override
	@Nonnull
	public List<GwtModule> findModulesByClass(@Nonnull final PsiElement context, final @Nullable String className)
	{
		if(className == null)
		{
			return Collections.emptyList();
		}

		PsiClass[] psiClasses = JavaPsiFacade.getInstance(context.getProject()).findClasses(className, context.getResolveScope());
		for(PsiClass psiClass : psiClasses)
		{
			PsiFile psiFile = psiClass.getContainingFile();
			if(psiFile != null)
			{
				VirtualFile file = psiFile.getVirtualFile();
				if(file != null)
				{
					List<GwtModule> modules = findGwtModulesByClientSourceFile(file);
					if(!modules.isEmpty())
					{
						return modules;
					}
				}
			}
		}
		return Collections.emptyList();
	}

	@Override
	public GwtModule findGwtModuleByEntryPoint(@Nonnull final PsiClass psiClass)
	{
		PsiFile psiFile = psiClass.getContainingFile();
		if(psiFile == null)
		{
			return null;
		}

		VirtualFile file = psiFile.getVirtualFile();
		if(file == null)
		{
			return null;
		}

		List<GwtModule> gwtModules = findGwtModulesByClientSourceFile(file);
		for(GwtModule gwtModule : gwtModules)
		{
			List<GwtEntryPoint> entryPoints = gwtModule.getEntryPoints();
			for(GwtEntryPoint entryPoint : entryPoints)
			{
				String className = entryPoint.getEntryClass().getValue();
				if(className != null && className.equals(psiClass.getQualifiedName()))
				{
					return gwtModule;
				}
			}
		}
		return null;
	}

	@Override
	@Nonnull
	public List<Pair<GwtModule, String>> findGwtModulesByPublicFile(@Nonnull final VirtualFile file)
	{
		List<GwtModule> gwtModules = findModulesByClientOrPublicFile(file, false, true);
		List<Pair<GwtModule, String>> pairs = new ArrayList<Pair<GwtModule, String>>();
		for(GwtModule gwtModule : gwtModules)
		{
			String path = getPathFromPublicRoot(gwtModule, file);
			if(path != null)
			{
				pairs.add(Pair.create(gwtModule, path));
			}
		}
		return pairs;
	}

	@Override
	@Nullable
	public GwtModule getGwtModuleByXmlFile(@Nonnull PsiFile file)
	{
		if(file instanceof XmlFile)
		{
			DomFileElement<GwtModule> fileElement = DomManager.getDomManager(myProject).getFileElement((XmlFile) file, GwtModule.class);
			if(fileElement != null)
			{
				return fileElement.getRootElement();
			}
		}
		return null;
	}

	@Override
	public boolean isInheritedOrSelf(final GwtModule gwtModule, final List<GwtModule> referencedModules)
	{
		for(GwtModule referencedModule : referencedModules)
		{
			if(isInheritedOrSelf(gwtModule, referencedModule))
			{
				return true;
			}
		}
		return false;
	}

	private static class GwtModulesFinder implements ContentIterator
	{
		private final List<GwtModule> myResults;
		private final PsiManager myPsiManager;
		private final DomManager myDomManager;

		public GwtModulesFinder(final Project project)
		{
			myResults = new ArrayList<GwtModule>();
			myPsiManager = PsiManager.getInstance(project);
			myDomManager = DomManager.getDomManager(project);
		}

		@Override
		public boolean processFile(VirtualFile fileOrDir)
		{
			if(!fileOrDir.isDirectory() && fileOrDir.getFileType() == XmlFileType.INSTANCE &&
					fileOrDir.getNameWithoutExtension().endsWith(GWT_SUFFIX))
			{
				final PsiFile psiFile = myPsiManager.findFile(fileOrDir);
				if(psiFile instanceof XmlFile)
				{
					final DomFileElement<GwtModule> fileElement = myDomManager.getFileElement((XmlFile) psiFile, GwtModule.class);
					if(fileElement != null)
					{
						myResults.add(fileElement.getRootElement());
					}
				}
			}
			return true;
		}

		public List<GwtModule> getResults()
		{
			return myResults;
		}

		public void processChildren(final VirtualFile parent)
		{
			List<VirtualFile> directories = getDirectories(parent);

			for(VirtualFile directory : directories)
			{
				final VirtualFile[] files = directory.getChildren();
				if(files != null)
				{
					for(VirtualFile virtualFile : files)
					{
						processFile(virtualFile);
					}
				}
			}
		}

		private List<VirtualFile> getDirectories(final VirtualFile directory)
		{
			Module module = ModuleUtilCore.findModuleForFile(directory, myPsiManager.getProject());

			if(module != null)
			{
				PsiDirectory psiDirectory = myPsiManager.findDirectory(directory);
				if(psiDirectory != null)
				{
					PsiPackage psiPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
					if(psiPackage != null)
					{
						List<VirtualFile> directories = new ArrayList<VirtualFile>();
						PsiDirectory[] psiDirectories = psiPackage.getDirectories(GlobalSearchScope.moduleWithDependentsScope(module));
						for(PsiDirectory dir : psiDirectories)
						{
							directories.add(dir.getVirtualFile());
						}
						return directories;
					}
				}
			}

			return Collections.singletonList(directory);
		}
	}

}
