/*******************************************************************************
 * Copyright (c) 2000, 2020 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.browsing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.eclipse.jdt.testplugin.JavaProjectHelper;
import org.eclipse.jdt.testplugin.JavaTestPlugin;

import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jface.viewers.IStructuredContentProvider;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.util.CoreUtility;


public class PackagesViewContentProviderTests2 {

	private IJavaProject fJProject1;
	private IJavaProject fJProject2;

	private IPackageFragmentRoot fRoot1;
	private IWorkspace fWorkspace;
	private IWorkbench fWorkbench;
	private MockPluginView fMyPart;

	private IStructuredContentProvider fProvider;
	private IPackageFragmentRoot fArchiveFragmentRoot;
	private IPackageFragment fPackJunit;
	private IPackageFragment fPackJunitSamples;
	private IPackageFragment fPackJunitSamplesMoney;

	private IWorkbenchPage fPage;
	private IPackageFragmentRoot fRoot2;
	private IPackageFragment fPack12;
	private IPackageFragment fPack32;
	private IPackageFragment fPack42;
	private IPackageFragment fPack52;
	private IPackageFragment fPack62;
	private IPackageFragment fPack21;
	private IPackageFragment fPack61;
	private IPackageFragment fPack51;
	private IPackageFragment fPack41;
	private IPackageFragment fPack31;
	private IPackageFragment fPackDefault1;
	private IPackageFragment fPackDefault2;
	private IPackageFragment fPack17;
	private IPackageFragment fPack81;
	private IPackageFragment fPack91;
	private IPackageFragmentRoot fInternalJarRoot;
	private IPackageFragment fInternalPackDefault;
	private IPackageFragment fInternalPack3;
	private IPackageFragment fInternalPack4;
	private IPackageFragment fInternalPack5;
	private IPackageFragment fInternalPack10;
	private IPackageFragment fInternalPack6;
	private boolean fEnableAutoBuildAfterTesting;
	private IPackageFragment fPack102;

	//----------- getElement in flat view ------------------------------

	@Test
	public void testGetElementsPackageFragmentRoot() throws Exception {
		Object[] children= fProvider.getElements(fRoot1);
		Object[] expectedChildren= new Object[] { fPack21, fPack31, fPack41, fPack51, fPack61, fPack81, fPack91, fPackDefault1 };
		assertTrue(compareArrays(children, expectedChildren), "Wrong children found for PackageFragment"); //$NON-NLS-1$
	}

	@Test
	public void testGetElementsProject() throws Exception {

		LogicalPackage cp3= new LogicalPackage(fPack31);
		cp3.add(fPack32);
		cp3.add(fInternalPack3);

		LogicalPackage defaultCp= new LogicalPackage(fPackDefault1);
		defaultCp.add(fPackDefault2);
		defaultCp.add(fInternalPackDefault);

		LogicalPackage cp4= new LogicalPackage(fPack41);
		cp4.add(fPack42);
		cp4.add(fInternalPack4);

		LogicalPackage cp5= new LogicalPackage(fPack51);
		cp5.add(fPack52);
		cp5.add(fInternalPack5);

		LogicalPackage cp6= new LogicalPackage(fPack61);
		cp6.add(fPack62);
		cp6.add(fInternalPack6);

		LogicalPackage cp10= new LogicalPackage(fPack102);
		cp10.add(fInternalPack10);

		Object[] children= fProvider.getElements(fJProject2);
		Object[] expectedChildren= new Object[] { defaultCp, cp3, cp4, cp5, cp6, cp10, fPack21, fPack12, fPack91, fPack81, fPack17 };
		assertTrue(compareArrays(children, expectedChildren), "Wrong children founf for PackageFragment"); //$NON-NLS-1$
	}

	//---------------Delta Tests-----------------------------
	@Test
	public void testRemovePackageNotLogicalPackage() throws Exception {
		//initialise Map
		fProvider.getElements(fJProject2);

		fMyPart.clear();

		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= new TestDelta(IJavaElementDelta.REMOVED, fPack12);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		assertTrue(fMyPart.hasRemoveHappened(), "Remove happened"); //$NON-NLS-1$
		assertTrue(fMyPart.getRemovedObject().contains(fPack12), "Correct package removed"); //$NON-NLS-1$
	}

	@Test
	public void testRemovePackageInTwoPackageLogicalPackage() throws Exception {

		//initialise map
		fProvider.getElements(fJProject2);

		//create CompoundElement containning
		LogicalPackage cp10= new LogicalPackage(fInternalPack10);

		fMyPart.clear();

		//send a delta indicating fragment deleted
		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= new TestDelta(IJavaElementDelta.REMOVED, fPack102);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		//assert remove happened (delta worked)
		assertTrue(fMyPart.hasRemoveHappened(), "Refresh happened"); //$NON-NLS-1$
		assertTrue(fMyPart.hasAddHappened(), "Refresh happened"); //$NON-NLS-1$
		Object addedObject= fMyPart.getAddedObject().get(0);
		Object removedObject= fMyPart.getRemovedObject().get(0);
		assertTrue(canFindEqualCompoundElement(cp10, new Object[] { removedObject }), "Correct guy removed"); //$NON-NLS-1$
		assertEquals(fInternalPack10, addedObject, "Correct guy added"); //$NON-NLS-1$
	}

	@Test
	public void testRemovePackageFromLogicalPackage() throws Exception {

		//initialise map
		fProvider.getElements(fJProject2);

		fMyPart.clear();

		//send a delta indicating fragment deleted
		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= new TestDelta(IJavaElementDelta.REMOVED, fPack62);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		//assert remove happened (delta worked)
		assertFalse(fMyPart.hasRefreshHappened(), "Refresh did not happened"); //$NON-NLS-1$
	}

	@Test
	public void testRemoveCUFromPackageNotLogicalPackage() throws Exception {

		//initialise Map
		ICompilationUnit cu= fPack81.createCompilationUnit("Object.java", "", true, null); //$NON-NLS-1$//$NON-NLS-2$

		//make sure parent is visible
		fMyPart.fViewer.setInput(fJProject2);

		fMyPart.clear();

		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= TestDelta.createCUDelta(new ICompilationUnit[] { cu }, fPack81, IJavaElementDelta.REMOVED);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		assertPack81RefreshedOnce();
	}

	private void assertPack81RefreshedOnce() {
		assertTrue(fMyPart.hasRefreshHappened(), "Refresh happened"); //$NON-NLS-1$
		assertTrue(fMyPart.getRefreshedObject().contains(fPack81), "fPack81 not refreshed:\n" + fMyPart.getRefreshedObject());
		assertEquals(1, fMyPart.getRefreshedObject().size(), "Too many refreshes (" + fMyPart.getRefreshedObject().size() + "):\n" + fMyPart.getRefreshedObject());
	}

	@Test
	public void testAddCUFromPackageNotLogicalPackage() throws Exception {

		ICompilationUnit cu= fPack81.createCompilationUnit("Object.java", "", true, null); //$NON-NLS-1$//$NON-NLS-2$

		//initialise Map
		fMyPart.fViewer.setInput(fJProject2);

		fMyPart.clear();

		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= TestDelta.createCUDelta(new ICompilationUnit[] { cu }, fPack81, IJavaElementDelta.ADDED);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		assertPack81RefreshedOnce();
		assertTrue(fMyPart.hasRefreshHappened(), "Refresh happened"); //$NON-NLS-1$
		assertTrue(fMyPart.getRefreshedObject().contains(fPack81), "fPack81 not refreshed:\n" + fMyPart.getRefreshedObject());
		assertEquals(1, fMyPart.getRefreshedObject().size(), "Too many refreshes (" + fMyPart.getRefreshedObject().size() + "):\n" + fMyPart.getRefreshedObject());
	}

	@Test
	public void testAddFragmentToLogicalPackage() throws Exception {

		//create package fragment to be added
		IPackageFragment pack101= fRoot1.createPackageFragment("pack3.pack4.pack10", true, null);//$NON-NLS-1$

		//initialise map
		fProvider.getElements(fJProject2);

		fMyPart.clear();

		//send delta
		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= new TestDelta(IJavaElementDelta.ADDED, pack101);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		//make sure no refresh happened
		assertFalse(fMyPart.hasRefreshHappened(), "Refresh did not happened"); //$NON-NLS-1$
	}

	@Test
	public void testAddPackageNotLogicalPackage() throws Exception {

		//initialise Map
		fProvider.getElements(fJProject2);

		fMyPart.clear();

		IPackageFragment test= fRoot1.createPackageFragment("pack3.test", true, null); //$NON-NLS-1$
		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= new TestDelta(IJavaElementDelta.ADDED, test);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		assertTrue(fMyPart.hasAddHappened(), "Add happened"); //$NON-NLS-1$
		assertTrue(fMyPart.getAddedObject().contains(test), "Correct package added"); //$NON-NLS-1$
	}

	@Test
	public void testAddPackageToCreateLogicalPackage() throws Exception {
		//initialise map
		fProvider.getElements(fJProject2);

		//create new package
    	IPackageFragment pack11= fRoot1.createPackageFragment("pack1", true, null);//$NON-NLS-1$

		//create a logical package for testing
		LogicalPackage lp1= new LogicalPackage(fPack12);
		lp1.add(pack11);

		fMyPart.clear();

		//send a delta indicating fragment deleted
		IElementChangedListener listener= (IElementChangedListener) fProvider;
		IJavaElementDelta delta= new TestDelta(IJavaElementDelta.ADDED, pack11);
		listener.elementChanged(new ElementChangedEvent(delta, ElementChangedEvent.POST_CHANGE));

		//force events from display
		fMyPart.pushDisplay();

		//assert remove and add happened (delta worked)
		assertTrue(fMyPart.hasRemoveHappened(), "Remove and add happened"); //$NON-NLS-1$
		assertTrue(fMyPart.hasAddHappened(), "Remove and add happened"); //$NON-NLS-1$
		Object addedObject= fMyPart.getAddedObject().get(0);
		Object removedObject= fMyPart.getRemovedObject().get(0);
		assertEquals(fPack12, removedObject, "Correct guy removed"); //$NON-NLS-1$
		assertEquals(lp1, addedObject, "Correct guy added"); //$NON-NLS-1$
	}



	/*
	 * @see TestCase#setUp()
	 */
	@BeforeEach
	public void setUp() throws Exception {
		fWorkspace= ResourcesPlugin.getWorkspace();
		assertNotNull(fWorkspace);

		IWorkspaceDescription workspaceDesc= fWorkspace.getDescription();
		fEnableAutoBuildAfterTesting= workspaceDesc.isAutoBuilding();
		if (fEnableAutoBuildAfterTesting)
			CoreUtility.setAutoBuilding(false);

		fJProject1= JavaProjectHelper.createJavaProject("TestProject1", "bin");//$NON-NLS-1$//$NON-NLS-2$
		fJProject2= JavaProjectHelper.createJavaProject("TestProject2", "bin");//$NON-NLS-1$//$NON-NLS-2$

		assertNotNull(fJProject1, "project1 null");//$NON-NLS-1$
		assertNotNull(fJProject2, "project2 null");//$NON-NLS-1$


		//------------set up project #1 : External Jar and zip file-------

		IPackageFragmentRoot jdk= JavaProjectHelper.addVariableRTJar(fJProject1, "JRE_LIB_TEST", null, null);//$NON-NLS-1$
		assertNotNull(jdk, "jdk not found");//$NON-NLS-1$


		//---Create zip file-------------------

		java.io.File junitSrcArchive= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.JUNIT_SRC_381);

		assertNotNull(junitSrcArchive, "junit src not found");//$NON-NLS-1$
		assertTrue(junitSrcArchive.exists(), "junit src not found");//$NON-NLS-1$

		fArchiveFragmentRoot= JavaProjectHelper.addSourceContainerWithImport(fJProject1, "src", junitSrcArchive, JavaProjectHelper.JUNIT_SRC_ENCODING);//$NON-NLS-1$

		fPackJunit= fArchiveFragmentRoot.getPackageFragment("junit");//$NON-NLS-1$
		fPackJunitSamples= fArchiveFragmentRoot.getPackageFragment("junit.samples");//$NON-NLS-1$
		fPackJunitSamplesMoney= fArchiveFragmentRoot.getPackageFragment("junit.samples.money");//$NON-NLS-1$

		assertNotNull(fPackJunit, "creating fPackJunit");//$NON-NLS-1$
		assertNotNull(fPackJunitSamples, "creating fPackJunitSamples");//$NON-NLS-1$
		assertNotNull(fPackJunitSamplesMoney,"creating fPackJunitSamplesMoney");//$NON-NLS-1$

		fPackJunitSamplesMoney.getCompilationUnit("IMoney.java");//$NON-NLS-1$
		fPackJunitSamplesMoney.getCompilationUnit("Money.java");//$NON-NLS-1$
		fPackJunitSamplesMoney.getCompilationUnit("MoneyBag.java");//$NON-NLS-1$
		fPackJunitSamplesMoney.getCompilationUnit("MoneyTest.java");//$NON-NLS-1$

		//java.io.File mylibJar= JavaTestPlugin.getDefault().getFileInPlugin(JavaProjectHelper.MYLIB);
		//assertTrue("lib not found", mylibJar != null && mylibJar.exists());//$NON-NLS-1$
		//JavaProjectHelper.addLibraryWithImport(fJProject1, new Path(mylibJar.getPath()), null, null);

		//----------------Set up internal jar----------------------------
		File myInternalJar= JavaTestPlugin.getDefault().getFileInPlugin(new Path("testresources/compoundtest.jar"));//$NON-NLS-1$
		assertNotNull(myInternalJar, "lib not found");//$NON-NLS-1$
		assertTrue(myInternalJar.exists(), "lib not found");//$NON-NLS-1$

		fInternalJarRoot= JavaProjectHelper.addLibraryWithImport(fJProject2, Path.fromOSString(myInternalJar.getPath()), null, null);

		fInternalPackDefault= fInternalJarRoot.getPackageFragment("");//$NON-NLS-1$
		fInternalPack3= fInternalJarRoot.getPackageFragment("pack3");//$NON-NLS-1$
		fInternalPack4= fInternalJarRoot.getPackageFragment("pack3.pack4");//$NON-NLS-1$
		fInternalPack5= fInternalJarRoot.getPackageFragment("pack3.pack5");//$NON-NLS-1$
		fInternalPack6= fInternalJarRoot.getPackageFragment("pack3.pack5.pack6");//$NON-NLS-1$
		fInternalPack10= fInternalJarRoot.getPackageFragment("pack3.pack4.pack10");//$NON-NLS-1$

		//-----------------Set up source folder--------------------------

		fRoot2= JavaProjectHelper.addSourceContainer(fJProject2, "src2");//$NON-NLS-1$
		fPackDefault2= fRoot2.createPackageFragment("",true,null);//$NON-NLS-1$
		fPack12= fRoot2.createPackageFragment("pack1", true, null);//$NON-NLS-1$
		fPack17= fRoot2.createPackageFragment("pack1.pack7",true,null);//$NON-NLS-1$
		fPack32= fRoot2.createPackageFragment("pack3",true,null);//$NON-NLS-1$
		fPack42= fRoot2.createPackageFragment("pack3.pack4", true,null);//$NON-NLS-1$
		fPack52= fRoot2.createPackageFragment("pack3.pack5",true,null);//$NON-NLS-1$
		fPack62= fRoot2.createPackageFragment("pack3.pack5.pack6", true, null);//$NON-NLS-1$
		fPack102=fRoot2.createPackageFragment("pack3.pack4.pack10", true, null);//$NON-NLS-1$

		fPack12.createCompilationUnit("Object.java", "", true, null);//$NON-NLS-1$//$NON-NLS-2$
		fPack62.createCompilationUnit("Object.java","", true, null);//$NON-NLS-1$//$NON-NLS-2$


		//set up project #2: file system structure with in a source folder

	//	JavaProjectHelper.addVariableEntry(fJProject2, new Path("JRE_LIB_TEST"), null, null);

		//----------------Set up source folder--------------------------

		fRoot1= JavaProjectHelper.addSourceContainer(fJProject2, "src1"); //$NON-NLS-1$
		fPackDefault1= fRoot1.createPackageFragment("",true,null); //$NON-NLS-1$
		fPack21= fRoot1.createPackageFragment("pack2", true, null);//$NON-NLS-1$
		fPack31= fRoot1.createPackageFragment("pack3",true,null);//$NON-NLS-1$
		fPack41= fRoot1.createPackageFragment("pack3.pack4", true,null);//$NON-NLS-1$
		fPack91= fRoot1.createPackageFragment("pack3.pack4.pack9",true,null);//$NON-NLS-1$
		fPack51= fRoot1.createPackageFragment("pack3.pack5",true,null);//$NON-NLS-1$
		fPack61= fRoot1.createPackageFragment("pack3.pack5.pack6", true, null);//$NON-NLS-1$
		fPack81= fRoot1.createPackageFragment("pack3.pack8", true, null);//$NON-NLS-1$

		fPack21.createCompilationUnit("Object.java", "", true, null);//$NON-NLS-1$//$NON-NLS-2$
		fPack61.createCompilationUnit("Object.java","", true, null);//$NON-NLS-1$//$NON-NLS-2$

		//set up the mock view
		setUpMockView();
	}

	public void setUpMockView() throws Exception{

		fWorkbench= PlatformUI.getWorkbench();
		assertNotNull(fWorkbench);

		fPage= fWorkbench.getActiveWorkbenchWindow().getActivePage();
		assertNotNull(fPage);

		MockPluginView.setListState(true);
		IViewPart myPart= fPage.showView("org.eclipse.jdt.ui.tests.browsing.MockPluginView");//$NON-NLS-1$
		if (myPart instanceof MockPluginView) {
			fMyPart= (MockPluginView) myPart;

			fProvider= (IStructuredContentProvider)fMyPart.getTreeViewer().getContentProvider();
			//create map and set listener
			fProvider.inputChanged(null,null,fJProject2);
			JavaCore.removeElementChangedListener((IElementChangedListener) fProvider);
		} else {
			fail("Unable to get view");//$NON-NLS-1$
		}

		assertNotNull(fProvider);
	}

	/*
	 * @see TestCase#tearDown()
	 */
	@AfterEach
	public void tearDown() throws Exception {

		JavaProjectHelper.delete(fJProject1);
		JavaProjectHelper.delete(fJProject2);
		fProvider.inputChanged(null, null, null);
		fPage.hideView(fMyPart);

		if (fEnableAutoBuildAfterTesting)
			CoreUtility.setAutoBuilding(true);
	}

	private boolean compareArrays(Object[] children, Object[] expectedChildren) {
		if(children.length!=expectedChildren.length)
			return false;
		for (Object child : children) {
			if (child instanceof IJavaElement) {
				IJavaElement el= (IJavaElement) child;
				if(!contains(el, expectedChildren))
					return false;
			} else if(child instanceof IResource){
				IResource res = (IResource) child;
				if(!contains(res, expectedChildren)){
					return false;
				}
			} else if (child instanceof LogicalPackage){
				if(!canFindEqualCompoundElement((LogicalPackage)child, expectedChildren))
					return false;
			}
		}
		return true;
	}

	private boolean canFindEqualCompoundElement(LogicalPackage compoundElement, Object[] expectedChildren) {
		for (Object object : expectedChildren) {
			if(object instanceof LogicalPackage){
				LogicalPackage el= (LogicalPackage) object;
				if(el.getElementName().equals(compoundElement.getElementName()) && (el.getJavaProject().equals(compoundElement.getJavaProject()))){
					if(compareArrays(el.getFragments(), compoundElement.getFragments()))
						return true;
				}
			}
		}
		return false;
	}

	private boolean contains(IResource res, Object[] expectedChildren) {
		for (Object object : expectedChildren) {
			if (object instanceof IResource) {
				IResource expres= (IResource) object;
				if(expres.equals(res))
					return true;
			}
		}
		return false;
	}

	private boolean contains(IJavaElement fragment, Object[] expectedChildren) {
		for (Object object : expectedChildren) {
			if (object instanceof IJavaElement) {
				IJavaElement expfrag= (IJavaElement) object;
				if(expfrag.equals(fragment))
					return true;
			}
		}
		return false;
	}
}
