package org.eclipse.jdt.internal.junit.ui;

import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class JavaBrowsingMessages {

	private static final String BUNDLE_NAME = "org.eclipse.jdt.internal.ui.browsing.JavaBrowsingMessages"; //$NON-NLS-1$

	private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME);

	private JavaBrowsingMessages() {
	}

	public static String getString(String key) {
		try {
			return RESOURCE_BUNDLE.getString(key);
		} catch (MissingResourceException e) {
			return '!' + key + '!';
		}
	}
}