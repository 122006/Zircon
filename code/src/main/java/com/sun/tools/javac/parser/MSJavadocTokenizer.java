package com.sun.tools.javac.parser;

import java.nio.CharBuffer;

public class MSJavadocTokenizer extends MSJavaTokenizer {
    public MSJavadocTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
//        System.out.println("OOJavadocTokenizer1");
    }

    public MSJavadocTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
//        System.out.println("OOJavadocTokenizer2");
    }

    public MSJavadocTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
//        System.out.println("OOJavadocTokenizer3");
    }
}
