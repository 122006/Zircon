package com.sun.tools.javac.parser;

import java.nio.CharBuffer;

public class ZrJavadocTokenizer extends ZrJavaTokenizer {
    public ZrJavadocTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
//        System.out.println("OOJavadocTokenizer1");
    }

    public ZrJavadocTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
//        System.out.println("OOJavadocTokenizer2");
    }

    public ZrJavadocTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
//        System.out.println("OOJavadocTokenizer3");
    }
}
