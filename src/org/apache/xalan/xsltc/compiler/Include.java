/*
 * @(#)$Id$
 *
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 2001 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xalan" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 2001, Sun
 * Microsystems., http://www.sun.com.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 * @author Jacek Ambroziak
 * @author Morten Jorgensen
 * @author Erwin Bolwidt <ejb@klomp.org>
 * @author Gunnlaugur Briem <gthb@dimon.is>
 */

package org.apache.xalan.xsltc.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URL;
import java.net.MalformedURLException;
import java.util.Enumeration;

import javax.xml.parsers.*;

import org.xml.sax.*;

import org.apache.xalan.xsltc.compiler.util.Type;
import org.apache.xalan.xsltc.compiler.util.*;

import org.apache.bcel.generic.*;

final class Include extends TopLevelElement {

    private Stylesheet _included = null;

    public Stylesheet getIncludedStylesheet() {
	return _included;
    }

    public void parseContents(final Parser parser) {
	final Stylesheet context = parser.getCurrentStylesheet();

	String docToLoad = getAttribute("href");
	try {
	    if (context.checkForLoop(docToLoad)) {
		final int errno = ErrorMsg.CIRCULAR_INCLUDE_ERR;
		final ErrorMsg msg = new ErrorMsg(errno, docToLoad, this);
		parser.reportError(Constants.FATAL, msg);
		return;
	    }

	    String currLoadedDoc = context.getSystemId();
	    SourceLoader loader = context.getSourceLoader();
	    InputSource input = null;

	    if (loader != null) {
		final XSLTC xsltc = parser.getXSLTC();
		input = loader.loadSource(docToLoad, currLoadedDoc, xsltc);
	    }
	    else {
		// bug 7835, patch by Stefan Kost (s.kost@webmacher.de)
		if ((currLoadedDoc != null) && (currLoadedDoc.length() > 0)) {
		    File file = new File(currLoadedDoc);
		    if (file.exists()) {
		        currLoadedDoc = "file:" + file.getCanonicalPath();
		    }
		    final URL url = new URL(new URL(currLoadedDoc), docToLoad);
		    docToLoad = url.toString();
		    input = new InputSource(docToLoad);
		}
		else {
		    File file = new File(System.getProperty("user.dir"),
			docToLoad);
		    if (file.exists()) {
			docToLoad = "file:" + file.getCanonicalPath();
		    }
		    else {
			throw new FileNotFoundException(
			  "Could not load file " + docToLoad);
		    }
		    input = new InputSource(docToLoad);
		}
	    }

	    final SyntaxTreeNode root = parser.parse(input);
	    if (root == null) return;
	    _included = parser.makeStylesheet(root);
	    if (_included == null) return;

	    _included.setSourceLoader(loader);
	    _included.setSystemId(docToLoad);
	    _included.setParentStylesheet(context);
	    _included.setIncludingStylesheet(context);
	    _included.setTemplateInlining(context.getTemplateInlining());

	    // An included stylesheet gets the same import precedence
	    // as the stylesheet that included it.
	    final int precedence = context.getImportPrecedence();
	    _included.setImportPrecedence(precedence);
	    parser.setCurrentStylesheet(_included);
	    _included.parseContents(parser);

	    final Enumeration elements = _included.elements();
	    final Stylesheet topStylesheet = parser.getTopLevelStylesheet();
	    while (elements.hasMoreElements()) {
		final Object element = elements.nextElement();
		if (element instanceof TopLevelElement) {
		    if (element instanceof Variable) {
			topStylesheet.addVariable((Variable) element);
		    }
		    else if (element instanceof Param) {
			topStylesheet.addParam((Param) element);
		    }
		    else {
			topStylesheet.addElement((TopLevelElement) element);
		    }
		}
	    }
	}
	catch (FileNotFoundException e) {
	    // Update systemId in parent stylesheet for error reporting
	    context.setSystemId(getAttribute("href"));

	    final ErrorMsg msg = 
		new ErrorMsg(ErrorMsg.FILE_NOT_FOUND_ERR, docToLoad, this);
	    parser.reportError(Constants.FATAL, msg);
	}
	catch (MalformedURLException e) {
	    // Update systemId in parent stylesheet for error reporting
	    context.setSystemId(getAttribute("href"));

	    final ErrorMsg msg = 
		new ErrorMsg(ErrorMsg.FILE_NOT_FOUND_ERR, docToLoad, this);
	    parser.reportError(Constants.FATAL, msg);
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
	finally {
	    parser.setCurrentStylesheet(context);
	}
    }
    
    public Type typeCheck(SymbolTable stable) throws TypeCheckError {
	return Type.Void;
    }
    
    public void translate(ClassGenerator classGen, MethodGenerator methodGen) {
	// do nothing
    }
}
