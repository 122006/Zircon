package com.sun.tools.javac.parser;

import java.nio.CharBuffer;

public class MSScanner extends Scanner {

    public MSScanner(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        this(scannerFactory, new MSJavaTokenizer(scannerFactory, charBuffer));
    }

    public MSScanner(ScannerFactory scannerFactory, char[] chars, int i) {
        this(scannerFactory, new MSJavaTokenizer(scannerFactory, chars, i));
    }

    public MSScanner(ScannerFactory scannerFactory, JavaTokenizer javaTokenizer) {
        super(scannerFactory,javaTokenizer);
    }
}
