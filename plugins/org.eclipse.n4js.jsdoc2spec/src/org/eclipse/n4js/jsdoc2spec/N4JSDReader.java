/**
 * Copyright (c) 2016 NumberFour AG.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   NumberFour AG - Initial API and implementation
 */
package org.eclipse.n4js.jsdoc2spec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.n4js.AnnotationDefinition;
import org.eclipse.n4js.jsdoc.N4JSDocHelper;
import org.eclipse.n4js.jsdoc.N4JSDocletParser;
import org.eclipse.n4js.jsdoc.dom.ContentNode;
import org.eclipse.n4js.jsdoc.dom.Doclet;
import org.eclipse.n4js.jsdoc.dom.FullMemberReference;
import org.eclipse.n4js.jsdoc.dom.LineTag;
import org.eclipse.n4js.jsdoc.tags.LineTagWithFullElementReference;
import org.eclipse.n4js.jsdoc2spec.adoc.RepoRelativePathHolder;
import org.eclipse.n4js.n4JS.FunctionOrFieldAccessor;
import org.eclipse.n4js.n4JS.Script;
import org.eclipse.n4js.projectModel.IN4JSCore;
import org.eclipse.n4js.projectModel.IN4JSProject;
import org.eclipse.n4js.projectModel.IN4JSSourceContainer;
import org.eclipse.n4js.resource.N4JSResource;
import org.eclipse.n4js.scoping.N4JSGlobalScopeProvider;
import org.eclipse.n4js.ts.types.ContainerType;
import org.eclipse.n4js.ts.types.SyntaxRelatedTElement;
import org.eclipse.n4js.ts.types.TClassifier;
import org.eclipse.n4js.ts.types.TMember;
import org.eclipse.n4js.ts.types.TMethod;
import org.eclipse.n4js.ts.types.TModule;
import org.eclipse.n4js.ts.types.TVariable;
import org.eclipse.n4js.ts.types.Type;
import org.eclipse.n4js.ts.types.util.MemberList;
import org.eclipse.n4js.utils.ContainerTypesHelper;
import org.eclipse.n4js.utils.ContainerTypesHelper.MemberCollector;
import org.eclipse.xtext.EcoreUtil2;
import org.eclipse.xtext.naming.QualifiedName;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;

/**
 */
public class N4JSDReader {

	@Inject
	ContainerTypesHelper containerTypesHelper;

	@Inject
	N4JSDocHelper n4jsDocHelper;

	@Inject
	IN4JSCore n4jsCore;

	@Inject
	N4JSGlobalScopeProvider globalScopeProvider;

	@Inject
	RepoRelativePathHolder rrph;

	IJSDoc2SpecIssueAcceptor issueAcceptor = IJSDoc2SpecIssueAcceptor.NULL_ACCEPTOR;

	/**
	 * Reads all N4JSD files in project, scans for types and links the tests.
	 *
	 * @return all types in a mapped with fully qualified type name (inclusive module spec) as key, the type info only
	 *         contains the types, no other information yet.
	 * @throws InterruptedException
	 *             thrown when user cancels the operation
	 */
	public Collection<SpecInfo> readN4JSDs(Collection<IN4JSProject> projects,
			Function<IN4JSProject, ResourceSet> resSetProvider, SubMonitorMsg monitor) throws InterruptedException {

		Multimap<String, SpecInfo> specInfoByName = HashMultimap.create();
		ResourceSet resSet = null;
		SubMonitorMsg sub = monitor.convert(2 * 100 * projects.size());
		for (IN4JSProject project : projects) {
			if (resSet == null) {
				resSet = resSetProvider.apply(project);
			}
			readScripts(specInfoByName, project, resSet, sub.newChild(100));
		}
		for (IN4JSProject project : projects) {
			if (resSet == null) {
				resSet = resSetProvider.apply(project);
			}
			linkTests(specInfoByName, project, resSet, sub.newChild(100));
		}

		return specInfoByName.values();
	}

	/**
	 * Reads all N4JSD files in project and scans for types. No further information is added yet. Reads all types into a
	 * map with fully qualified type name (inclusive module spec) as key, the type info only contains the types, no
	 * other information yet.
	 *
	 * @param specInfoByName
	 *            map of fqn of types or reqid keys to their corresponding spec info.
	 * @throws InterruptedException
	 *             thrown when user cancels the operation
	 */
	private void readScripts(Multimap<String, SpecInfo> specInfoByName, IN4JSProject project, ResourceSet resSet,
			SubMonitorMsg monitor) throws InterruptedException {

		ImmutableList<? extends IN4JSSourceContainer> srcCont = project.getSourceContainers();
		List<IN4JSSourceContainer> srcContFilter = new LinkedList<>();

		int count = 0;
		for (IN4JSSourceContainer container : srcCont) {
			if (container.isSource() || container.isTest()) {
				count += Iterables.size(container);
				srcContFilter.add(container);
			}
		}
		SubMonitorMsg sub = monitor.convert(count);

		for (IN4JSSourceContainer container : srcContFilter) {
			for (URI uri : container) {
				String ext = uri.fileExtension();
				if ("n4js".equals(ext) || "n4jsd".equals(ext)) {
					try {
						Resource resource = resSet.getResource(uri, true);
						if (resource != null) {
							Script script = (Script) (resource.getContents().isEmpty() ? null
									: resource.getContents().get(0));
							if (script == null) {
								// throw new IllegalStateException("Error parsing " + uri);
								continue;
							}
							N4JSResource.postProcess(resource);
							for (Type type : getRealTopLevelTypes(script)) {
								createTypeSpecInfo(type, specInfoByName);
							}
							for (TVariable tvar : script.getModule().getVariables()) {
								createTVarSpecInfo(tvar, specInfoByName);
							}
						}
					} catch (Exception ex) {
						ex.printStackTrace();
						String msg = "Error processing " + uri + ": " + ex.getMessage();
						throw new IllegalArgumentException(msg, ex);
					}
				}
				sub.worked(1);
				sub.checkCanceled();
			}
		}
	}

	/**
	 * The method {@link TModule#getTopLevelTypes()} returns also functions that are nested in functions. These are
	 * filtered out in this method.
	 *
	 * @return real top level types
	 */
	private Collection<Type> getRealTopLevelTypes(Script script) {
		Collection<Type> realTLT = new LinkedList<>();
		for (Type tlt : script.getModule().getTopLevelTypes()) {
			if (tlt instanceof SyntaxRelatedTElement) {
				SyntaxRelatedTElement srte = (SyntaxRelatedTElement) tlt;
				EObject astElem = srte.getAstElement();
				astElem = astElem != null ? astElem.eContainer() : null;
				FunctionOrFieldAccessor fofa = EcoreUtil2.getContainerOfType(astElem, FunctionOrFieldAccessor.class);
				if (fofa != null) {
					continue;
				}
			}

			realTLT.add(tlt);
		}
		return realTLT;
	}

	private void createTVarSpecInfo(TVariable tvar, Multimap<String, SpecInfo> specInfoByName) {
		String name = KeyUtils.getSpecKeyWithoutProjectFolder(rrph, tvar);
		specInfoByName.put(name, new SpecInfo(tvar));
	}

	private void createTypeSpecInfo(Type type, Multimap<String, SpecInfo> specInfoByName) {
		SpecInfo typeInfo = new SpecInfo(type);
		String regionName = KeyUtils.getSpecKeyWithoutProjectFolder(rrph, type);
		specInfoByName.put(regionName, typeInfo);

		Collection<SpecInfo> identicalSpecInfo = specInfoByName.get(regionName);
		if (identicalSpecInfo.size() > 1) {
			SpecInfo polyfillAware = null;
			List<SpecInfo> polyfilling = new LinkedList<>();
			for (SpecInfo si : identicalSpecInfo) {
				Type moduleType = si.specElementRef.getElementAsType();

				if (moduleType != null) {
					TModule typeModule = moduleType.getContainingModule();
					if (typeModule.isStaticPolyfillModule()) {
						polyfilling.add(si);
					} else if (typeModule.isStaticPolyfillAware()) {
						polyfillAware = si;
					}
				}
			}

			if (polyfillAware != null) {
				Type polyfillAwareType = polyfillAware.specElementRef.getElementAsType();
				for (SpecInfo si : polyfilling) {
					si.specElementRef.polyfillAware = polyfillAwareType;
				}
			}
		}
	}

	/**
	 * Links the tests to the testees and may create new specInfos for requirement ID related tests.
	 *
	 * @throws InterruptedException
	 *             thrown when user cancels the operation
	 */
	private void linkTests(Multimap<String, SpecInfo> specInfoByName, IN4JSProject project, ResourceSet resSet,
			SubMonitorMsg monitor) throws InterruptedException {

		List<Type> testTypes = getTestTypes(project, resSet, monitor);

		for (Type testType : testTypes) {
			try {
				if (testType instanceof TClassifier) {
					TClassifier ctype = (TClassifier) testType;
					processClassifier(specInfoByName, testType, ctype);
				}
			} catch (Exception ex) {
				ex.printStackTrace();
				String msg = "Error processing " + testType.eResource().getURI().toString() + ": " + ex.getMessage();
				throw new IllegalArgumentException(msg);
			}
		}
	}

	private List<Type> getTestTypes(IN4JSProject project, ResourceSet resSet, SubMonitorMsg monitor)
			throws InterruptedException {

		List<Type> testTypes = new ArrayList<>();
		ImmutableList<? extends IN4JSSourceContainer> srcCont = project.getSourceContainers();

		// count container
		int count = 0;
		for (IN4JSSourceContainer container : srcCont) {
			if (container.isTest()) {
				count += Iterables.size(container);
			}
		}
		SubMonitorMsg sub = monitor.convert(count);

		// scan for types
		for (IN4JSSourceContainer container : srcCont) {
			if (container.isTest()) {
				for (URI uri : container) {
					String ext = uri.fileExtension();
					if ("n4js".equals(ext)) {
						Resource resource = resSet.getResource(uri, true);
						if (resource != null) {
							Script script = (Script) (resource.getContents().isEmpty() ? null
									: resource.getContents().get(0));

							if (script == null) {
								throw new IllegalStateException("Error parsing " + uri);
							}
							N4JSResource.postProcess(resource);
							for (Type type : getRealTopLevelTypes(script)) {
								testTypes.add(type);
							}
						}
					}
					sub.worked(1);
					sub.checkCanceled();
				}
			}
		}
		return testTypes;
	}

	private void processClassifier(Multimap<String, SpecInfo> specInfoByName, Type testType, TClassifier ctype) {
		RepoRelativePath rrp = RepoRelativePath.compute(testType.eResource(), n4jsCore);
		Doclet testTypeDoclet = n4jsDocHelper.getDoclet(ctype.getAstElement());
		Collection<FullMemberReference> testeeRefsFromType = getFullMemberRefsFromType(testTypeDoclet);
		Collection<FullMemberReference> testeeTypeRefsFromType = getFullTypeRefsFromType(testTypeDoclet);

		MemberList<TMember> allMembers = containerTypesHelper.fromContext(testType).allMembers(ctype, false, false);
		for (TMember testMember : allMembers) {
			boolean isOwnedMember = testMember.getContainingType() == ctype;
			if (testMember instanceof TMethod && AnnotationDefinition.TEST_METHOD.hasAnnotation(testMember)) {
				EObject astElement = testMember.getAstElement();
				if (!astElement.eIsProxy()) {
					Doclet testMethodDoclet = n4jsDocHelper.getDoclet(astElement);
					LineTag tag = findLinkToElementTag(testMethodDoclet, isOwnedMember);

					if (tag != null) {
						processTag(specInfoByName, rrp, testeeRefsFromType, testeeTypeRefsFromType, testMember,
								isOwnedMember, astElement, testMethodDoclet, tag);
					}
				} else {
					System.err.println("cannot result AST when scanning for doclets: " + astElement);
				}
			}
		}
	}

	private void processTag(Multimap<String, SpecInfo> specInfoByName, RepoRelativePath rrp,
			Collection<FullMemberReference> testeeRefsFromType, Collection<FullMemberReference> testeeTypeRefsFromType,
			TMember testMember, boolean isOwnedMember, EObject astElement, Doclet testMethodDoclet, LineTag tag) {

		String title = tag.getTitle().getTitle();
		if ("testee".equals(title)) {
			FullMemberReference ref = getFullMemberRef(tag);
			if (ref != null) {
				addTestInfoForCodeElement(rrp, testMethodDoclet, ref, testMember, specInfoByName);
			}

		} else if ("testeeFromType".equals(title)) {
			RepoRelativePath rrpTestMethod = isOwnedMember ? rrp
					: RepoRelativePath.compute(astElement.eResource(), n4jsCore);

			for (FullMemberReference ref : testeeRefsFromType) {
				addTestInfoForCodeElement(rrpTestMethod, testMethodDoclet, ref, testMember, specInfoByName);
			}

		} else if ("testeeMember".equals(title)) {
			String testeeMember = N4JSDocletParser.TAG_TESTEEMEMBER.getValue(tag, "");
			RepoRelativePath rrpTestMethod = isOwnedMember ? rrp
					: RepoRelativePath.compute(astElement.eResource(), n4jsCore);

			for (FullMemberReference testeeTypeRef : testeeTypeRefsFromType) {
				FullMemberReference combinedTesteeRef = EcoreUtil.copy(testeeTypeRef);
				combinedTesteeRef.setMemberName(testeeMember);
				combinedTesteeRef.setRange(tag.getBegin(), tag.getEnd());
				addTestInfoForCodeElement(
						rrpTestMethod,
						testMethodDoclet,
						combinedTesteeRef,
						testMember,
						specInfoByName);
			}

		} else if ("reqid".equals(title)) {
			String reqid = N4JSDocletParser.TAG_REQID.getValue(tag, "");
			if (Strings.isNullOrEmpty(reqid)) {
				throw new IllegalStateException("Found reqid tag without requirement ID.");
			}
			RepoRelativePath rrpTestMethod = isOwnedMember ? rrp
					: RepoRelativePath.compute(astElement.eResource(), n4jsCore);

			addTestInfoForRequirement(rrpTestMethod, testMethodDoclet, reqid, testMember, specInfoByName);

		} else {
			// should not happen
			System.err.println("unhandled referencing tag: " + title);
		}
	}

	/**
	 * Returns the best matching tag which links the test to either a type, member, or reqid.
	 */
	private LineTag findLinkToElementTag(Doclet testMethodDoclet, boolean isOwnedMember) {
		LineTag bestMatch = null;
		for (LineTag tag : testMethodDoclet.getLineTags()) {
			if (isOwnedMember && "testee".equals(tag.getTitle().getTitle())) {
				bestMatch = tag; // testee always is the best match
				break;
			} else if ("testeeFromType".equals(tag.getTitle().getTitle())) {
				bestMatch = tag; // testeeFromType overrules testeeMember and reqid
			} else if ("testeeMember".equals(tag.getTitle().getTitle())) {
				if (bestMatch == null || "reqid".equals(bestMatch.getTitle().getTitle())) {
					bestMatch = tag; // testeeMember overrules reqid
				}
			} else if (isOwnedMember && "reqid".equals(tag.getTitle().getTitle())) {
				if (bestMatch == null) {
					bestMatch = tag; // lowes prio
				}
			}
		}
		return bestMatch;
	}

	private Collection<FullMemberReference> getFullMemberRefsFromType(Doclet testTypeDoclet) {
		Map<String, FullMemberReference> refsByName = new HashMap<>();
		for (LineTag tag : testTypeDoclet.getLineTags()) {
			if ("testee".equals(tag.getTitle().getTitle())) {
				FullMemberReference ref = getFullMemberRef(tag);
				if (ref != null) {
					refsByName.put(ref.toString(), ref);
				}
			}
		}
		return refsByName.values();
	}

	private Collection<FullMemberReference> getFullTypeRefsFromType(Doclet testTypeDoclet) {
		Map<String, FullMemberReference> refsByName = new HashMap<>();
		for (LineTag tag : testTypeDoclet.getLineTags()) {
			if ("testeeType".equals(tag.getTitle().getTitle())) {
				FullMemberReference ref = getFullMemberRef(tag);
				if (ref != null) {
					refsByName.put(ref.toString(), ref);
				}
			}
		}
		return refsByName.values();
	}

	/**
	 * Adds test info to an identified element.
	 */
	private void addTestInfoForCodeElement(RepoRelativePath rrp, Doclet testMethodDoclet, FullMemberReference ref,
			TMember testMember, Multimap<String, SpecInfo> typesByName) {

		String fullTypeName = ref.fullTypeName();
		String regionName = KeyUtils.getSpecKeyWithoutProjectFolder(rrp, fullTypeName);
		Collection<SpecInfo> specInfos = typesByName.get(regionName);

		boolean testeeMemberFound = false;
		for (SpecInfo specInfo : specInfos) {
			for (Type testee : specInfo.specElementRef.getTypes()) {
				if (testee instanceof ContainerType<?> && ref.memberNameSet()) {
					TMember testeeMember = getRefMember((ContainerType<?>) testee, ref);
					if (testeeMember != null) {
						String testeeName = testeeMember.getName();
						SpecTestInfo testSpecInfo = createTestSpecInfo(testeeName, testMethodDoclet, testMember, rrp);
						specInfo.addMemberTestInfo(testeeMember, testSpecInfo);
					}
					testeeMemberFound = true;
				}
			}
		}

		if (!testeeMemberFound) {
			for (SpecInfo specInfo : specInfos) {
				// Type, TFunction of TVariable
				String elementName = specInfo.specElementRef.identifiableElement.getName();
				SpecTestInfo testSpecInfo = createTestSpecInfo(elementName, testMethodDoclet, testMember, rrp);
				specInfo.addTypeTestInfo(testSpecInfo);
			}

			if (specInfos.isEmpty()) {
				issueAcceptor.addWarning("Testee " + fullTypeName + " not found", testMember);
			}
		}
	}

	private void addTestInfoForRequirement(RepoRelativePath rrp, Doclet testMethodDoclet, String reqid,
			TMember testMember, Multimap<String, SpecInfo> typesByName) {

		Collection<SpecInfo> specInfos = typesByName.get(SpecElementRef.reqidKey(reqid));
		if (specInfos.isEmpty()) {
			SpecInfo specInfo = new SpecInfo(reqid);
			typesByName.put(SpecElementRef.reqidKey(reqid), specInfo);
		}
		for (SpecInfo specInfo : specInfos) {
			specInfo.addTypeTestInfo(createTestSpecInfo(reqid, testMethodDoclet, testMember, rrp));
		}
	}

	private FullMemberReference getFullMemberRef(LineTag tag) {
		EList<ContentNode> contents = tag.getValueByKey(LineTagWithFullElementReference.REF).getContents();
		if (!contents.isEmpty()) {
			return (FullMemberReference) contents.get(0);
		}
		return null;
	}

	private SpecTestInfo createTestSpecInfo(String testeeName, Doclet doclet, TMember testMember,
			RepoRelativePath rrp) {

		String moduleName = testMember.getContainingModule().getModuleSpecifier();
		String typeName = testMember.getContainingType().getName();
		String memberName = testMember.getName();
		QualifiedName qualifiedName = QualifiedName.create(moduleName, typeName, memberName);
		RepoRelativePath repoRelPath = rrp != null ? rrp.withLine(testMember) : null;
		return new SpecTestInfo(testeeName, qualifiedName, doclet, repoRelPath);
	}

	// TODO fqn of getter vs setter, fqn of static vs instance
	private TMember getRefMember(ContainerType<?> ct, FullMemberReference ref) {
		TMember member = null;
		String memberName = ref.getMemberName();
		boolean _static = ref.isStaticMember();
		MemberCollector memberCollector = containerTypesHelper.fromContext(ct);
		member = memberCollector.findMember(ct, memberName, false, _static);

		if (member == null) {
			member = memberCollector.findMember(ct, memberName, false, !_static);
			if (member == null) {
				member = memberCollector.findMember(ct, memberName, true, _static);
				if (member == null) {
					member = memberCollector.findMember(ct, memberName, true, !_static);
				}
			}
		}
		return member;

	}

}
