package com.sun.tools.javac.parser2;

import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.UnicodeReader;
import com.sun.tools.javac.parser.ZrJavaTokenizer;

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
