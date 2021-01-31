package com.sun.tools.javac.parser;

import java.nio.CharBuffer;

public class OOScanner extends Scanner {

    public OOScanner(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        this(scannerFactory, new JSFJavaTokenizer(scannerFactory, charBuffer));
    }

    public OOScanner(ScannerFactory scannerFactory, char[] chars, int i) {
        this(scannerFactory, new JSFJavaTokenizer(scannerFactory, chars, i));
    }

    public OOScanner(ScannerFactory scannerFactory, JavaTokenizer javaTokenizer) {
        super(scannerFactory,javaTokenizer);
    }
}
