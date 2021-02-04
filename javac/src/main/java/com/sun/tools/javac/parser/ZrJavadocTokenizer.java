package com.sun.tools.javac.parser;

import java.nio.CharBuffer;

public class ZrJavadocTokenizer extends ZrJavaTokenizer {
    public ZrJavadocTokenizer(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        super(scannerFactory, charBuffer);
    }

    public ZrJavadocTokenizer(ScannerFactory scannerFactory, char[] chars, int i) {
        super(scannerFactory, chars, i);
    }

    public ZrJavadocTokenizer(ScannerFactory scannerFactory, UnicodeReader unicodeReader) {
        super(scannerFactory, unicodeReader);
    }
}
