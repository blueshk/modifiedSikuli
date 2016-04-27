/*
 * Copyright 2010-2011, Sikuli.org
 * Released under the MIT License.
 *
 */
package org.sikuli.ide.sikuli_test;

import org.sikuli.ide.SikuliIDE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestFailure;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.python.util.PythonInterpreter;
import org.python.core.*;

public class TextUnitTestRunner extends junit.textui.TestRunner {

	String list = null;
	String level = null;

	public TextUnitTestRunner(String[] args) {
		PythonInterpreter.initialize(System.getProperties(), null, args);
	}

	public TextUnitTestRunner(String[] args, String list) throws Exception {
		this.list = list;
		PythonInterpreter.initialize(System.getProperties(), null, args);
	}

	public TextUnitTestRunner(String[] args, String list, PrintStream ps,
			String level) throws Exception {
		super(ps);
		this.list = list;
		this.level = level;
		PythonInterpreter.initialize(System.getProperties(), null, args);
	}

	public TextUnitTestRunner(String[] args, PrintStream ps, String level)
			throws Exception {
		super(ps);
		this.level = level;
		PythonInterpreter.initialize(System.getProperties(), null, args);
	}

	public boolean testSikuli(String bundle) throws Exception {
		File fBundle = new File(bundle);
		String path = fBundle.getAbsolutePath();
		String filename = fBundle.getName();
		filename = filename.substring(0, filename.lastIndexOf(".")) + ".py";
		File fPy = new File(bundle, filename);
		Test suite = genTestSuite(fPy.getAbsolutePath(), path);
		return doRun(suite).wasSuccessful();
	}

	public TestResult testSikuli1(String bundle) throws Exception {
		File fBundle = new File(bundle);
		String path = fBundle.getAbsolutePath();
		String filename = fBundle.getName();
		filename = filename.substring(0, filename.lastIndexOf(".")) + ".py";
		File fPy = new File(bundle, filename);
		Test suite = genTestSuite(fPy.getAbsolutePath(), path);
		return doRun(suite);
	}

	private String genTestClassName(String filename) {
		String fname = new File(filename).getName();
		int dot = fname.indexOf(".");

		return fname.substring(0, dot);
	}


   private Test genTestSuite(String filename, String bundlePath) throws IOException{
      String className = genTestClassName(filename);
      TestSuite ret = new TestSuite(className);
      PythonInterpreter interp = new PythonInterpreter();
      String testCode = 
         "# coding=utf-8\n"+
         "from __future__ import with_statement\n"+
         "import junit\n"+
         "from junit.framework.Assert import *\n"+
         "from sikuli.Sikuli import *\n"+
         "class "+className+" (junit.framework.TestCase):\n"+
         "\tdef __init__(self, name):\n"+
         "\t\tjunit.framework.TestCase.__init__(self,name)\n"+
         "\t\tself.theTestFunction = getattr(self,name)\n"+
         "\t\tsetBundlePath('"+bundlePath.replace("\\", "\\\\")+"')\n"+
         "\tdef runTest(self):\n"+
         "\t\tself.theTestFunction()\n";

		BufferedReader in = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = in.readLine()) != null) {
                    if (line.indexOf("from sikuli import")!=-1){
                        continue;
                    }
                    if (line.startsWith("def ")){
                        testCode += "\t" + line + "\n";
                        testCode += "\t    import ForAll" + "\n";
                    }else{
                        testCode += "\t" + line + "\n";
                    }
		}
		interp.exec(testCode);
		PyList tests;
		if (list != null) {
			tests = (PyList) interp.eval("[" + className + "(f) for f in dir("
					+ className + ") if " + list + ".has_key(f)]");
		} else {
			if (level != null) {
				String t = "_lv_" + level;
				tests = (PyList) interp.eval("[" + className
						+ "(f) for f in dir(" + className
						+ ") if f.startswith(\"test\") & f.endswith('" + t
						+ "')]");
			} else {
				tests = (PyList) interp.eval("[" + className
						+ "(f) for f in dir(" + className
						+ ") if f.startswith(\"test\")]");
			}
		}
		while (tests.size() > 0) {
			PyObject t = tests.pop();
			Test t2 = (Test) (t).__tojava__(TestCase.class);
			ret.addTest(t2);
		}

		return ret;
	}
}
