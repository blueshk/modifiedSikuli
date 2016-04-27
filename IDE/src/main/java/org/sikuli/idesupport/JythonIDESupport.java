/*
 * Copyright 2010-2014, Sikuli.org, sikulix.com
 * Released under the MIT License.
 *
 * added RaiMan 2013
 */
package org.sikuli.idesupport;

/**
 * all methods from/for IDE, that are Python specific
 */
public class JythonIDESupport implements IIDESupport {

	@Override
	public String[] getEndings() {
		return new String [] {"py"};
	}

	@Override
	public IIndentationLogic getIndentationLogic() {
		return new PythonIndentation();
	}
}
