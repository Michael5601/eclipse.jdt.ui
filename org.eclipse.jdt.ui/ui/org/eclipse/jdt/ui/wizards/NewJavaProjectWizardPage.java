/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.jdt.ui.wizards;import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.wizard.WizardPage;

import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.help.WorkbenchHelp;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathsBlock;

/**
 * Standard wizard page for creating new Java projects. This page can be used in 
 * project creation wizards for projects and will configure the project with the 
 * Java nature. This page also allows the user to configure the Java project's 
 * output location for class files generated by the Java builder.
 * <p>
 */
public class NewJavaProjectWizardPage extends WizardPage {
	
	private static final String PAGE_NAME= "NewJavaProjectWizardPage"; //$NON-NLS-1$

	private WizardNewProjectCreationPage fMainPage;
	
	private IPath fOutputLocation;
	private IClasspathEntry[] fClasspathEntries;
	private boolean fAddJRE;
	
	private BuildPathsBlock fBuildPathsBlock;

	private IStatus fCurrStatus;
	
	private boolean fPageVisible;
	private boolean fProjectModified;
	
	/**
	 * Creates a Java project wizard creation page.
	 * <p>
	 * The Java project wizard reads project name and location from the main page.
	 * </p>
	 *
	 * @param root the workspace root
	 * @param mainpage the main page of the wizard
	 */	
	public NewJavaProjectWizardPage(IWorkspaceRoot root, WizardNewProjectCreationPage mainpage) {
		super(PAGE_NAME);
		
		setTitle(NewWizardMessages.getString("NewJavaProjectWizardPage.title")); //$NON-NLS-1$
		setDescription(NewWizardMessages.getString("NewJavaProjectWizardPage.description")); //$NON-NLS-1$
		
		fMainPage= mainpage;
		IStatusChangeListener listener= new IStatusChangeListener() {
			public void statusChanged(IStatus status) {
				updateStatus(status);
			}
		};

		fBuildPathsBlock= new BuildPathsBlock(root, listener, true);
		fCurrStatus= new StatusInfo();
		fPageVisible= false;
		
		fProjectModified= true;
		fOutputLocation= null;
		fClasspathEntries= null;
		fAddJRE= false;
	}		
	
	/**
	 * Sets the default output location to be used for the new Java project.
	 * This is the path of the folder (with the project) into which the Java builder 
	 * will generate binary class files corresponding to the project's Java source
	 * files.
	 * <p>
	 * The wizard will create this folder if required.
	 * </p>
	 * <p>
	 * The default class path will be applied when <code>initBuildPaths</code> is
	 * called. This is done automatically when the page becomes visible and
	 * the project or the default paths have changed.
	 * </p>
	 *
	 * @param path the folder to be taken as the default output path
	 */
	public void setDefaultOutputFolder(IPath path) {
		fOutputLocation= path;
		setProjectModified();
	}	

	/**
	 * Sets the default classpath to be used for the new Java project.
	 * <p>
	 * The caller of this method is responsible for creating the classpath entries 
	 * for the <code>IJavaProject</code> that corresponds to the created project.
	 * The caller is responsible for creating any new folders that might be mentioned
	 * on the classpath.
	 * </p>
	 * <p>
	 * The default output location will be applied when <code>initBuildPaths</code> is
	 * called. This is done automatically when the page becomes visible and
	 * the project or the default paths have changed.
	 * </p>
	 *
	 * @param entries the default classpath entries
	 * @param appendDefaultJRE <code>true</code> a variable entry for the
	 *  default JRE (specified in the preferences) will be added to the classpath.
	 */
	public void setDefaultClassPath(IClasspathEntry[] entries, boolean appendDefaultJRE) {
		if (entries != null && appendDefaultJRE) {
			IClasspathEntry[] newEntries= new IClasspathEntry[entries.length + 1];
			System.arraycopy(entries, 0, newEntries, 0, entries.length);
			newEntries[entries.length]= JavaRuntime.getJREVariableEntry();
			entries= newEntries;
		}
		fClasspathEntries= entries;
		fAddJRE= appendDefaultJRE;
		setProjectModified();
	}
	
	/**
	 * Sets the project to modified. This will initialize the page when becomes visible.
	 */
	public void setProjectModified() {
		fProjectModified= true;
	}

	/**
	 * Gets the project handle from the main page.
	 * Overwrite this method if you do not have a main page
	 */
	protected IProject getProjectHandle() {
		Assert.isNotNull(fMainPage);
		return fMainPage.getProjectHandle();
	}
	
	/**
	 * Gets the project location path from the main page
	 * Overwrite this method if you do not have a main page
	 */
	protected IPath getLocationPath() {
		Assert.isNotNull(fMainPage);
		return fMainPage.getLocationPath();
	}	

	/**
	 * Returns the Java project handle corresponding to the project defined in
	 * in the main page.
	 *
	 * @returns the Java project
	 */	
	public IJavaProject getNewJavaProject() {
		return JavaCore.create(getProjectHandle());
	}	

	/* (non-Javadoc)
	 * @see WizardPage#createControl
	 */	
	public void createControl(Composite parent) {
		Control control= fBuildPathsBlock.createControl(parent);
		setControl(control);
		
		WorkbenchHelp.setHelp(control, IJavaHelpContextIds.NEW_JAVAPROJECT_WIZARD_PAGE);
	}
	
	/**
	 * Forces the initialization of the Java project page. Default classpath or buildpath
	 * will be used if set. The initialization should only be performed when the project
	 * changed or default paths have changed. Toggeling back and forward the
	 * pages without changes should not re-initialize the page, as changes
	 * from the user will be overwritten.
	 */
	protected void initBuildPaths() {
		fBuildPathsBlock.init(getNewJavaProject(), fOutputLocation, fClasspathEntries);
	} 

	/**
	 * Extend this method to set a user defined default class path or output location.
	 * <code>initBuildPaths</code> is called when the page becomes visible the first time
	 * or the project or the default paths have changed.
	 */	
	public void setVisible(boolean visible) {
		if (visible) {
			// evaluate if a initialization is required
			if (fProjectModified || isNewProjectHandle()) {
				// only initialize the project when needed
				initBuildPaths();
				fProjectModified= false;
			}
		}
		super.setVisible(visible);
		fPageVisible= visible;
		updateStatus(fCurrStatus);
	}
	
	private boolean isNewProjectHandle() {
		IProject oldProject= fBuildPathsBlock.getJavaProject().getProject();
		return !oldProject.equals(getProjectHandle());
	}
	
	
	/**
	 * Returns the currently configured output location. Note that the returned path must not be valid.
	 */
	public IPath getOutputLocation() {
		return fBuildPathsBlock.getOutputLocation();
	}

	/**
	 * Returns the currently configured class path. Note that the class path must not be valid.
	 */	
	public IClasspathEntry[] getRawClassPath() {
		return fBuildPathsBlock.getRawClassPath();
	}
	

	/**
	 * Returns the runnable that will create the Java project. 
	 * The runnable will create and open the project if needed. The runnable will
	 * add the Java nature to the project, and set the project's classpath and
	 * output location. 
	 * <p>
	 * To create the new java project, execute this runnable
	 * </p>
	 *
	 * @return the runnable
	 */		
	public IRunnableWithProgress getRunnable() {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				if (monitor == null) {
					monitor= new NullProgressMonitor();
				}				
				monitor.beginTask(NewWizardMessages.getString("NewJavaProjectWizardPage.op_desc"), 10); //$NON-NLS-1$
				int workLeft= 10;

				// initialize if needed
				if (fProjectModified || isNewProjectHandle()) {
					initBuildPaths();
				}
				
				// create the project
				IWorkspace workspace= ResourcesPlugin.getWorkspace();
				IProject project= getProjectHandle();
				try {
					if (!project.exists()) {
						IProjectDescription desc= workspace.newProjectDescription(project.getName());
						IPath locationPath= getLocationPath();
						if (Platform.getLocation().equals(locationPath)) {
							locationPath= null;
						}
						desc.setLocation(locationPath);
						project.create(desc, new SubProgressMonitor(monitor, 1));
						workLeft--;
					}
					if (!project.isOpen()) {
						project.open(new SubProgressMonitor(monitor, 1));
						workLeft--;
					}
					IRunnableWithProgress jrunnable= fBuildPathsBlock.getRunnable();
					jrunnable.run(new SubProgressMonitor(monitor, workLeft));
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} finally {
					monitor.done();
				}
			}
		};	
	}
	
	/* (non-Javadoc)
	 * Updates the status line
	 */	
	private void updateStatus(IStatus status) {
		fCurrStatus= status;
		setPageComplete(!status.matches(IStatus.ERROR));
		if (fPageVisible) {
			StatusUtil.applyToStatusLine(this, status);
		}
	}
		
}