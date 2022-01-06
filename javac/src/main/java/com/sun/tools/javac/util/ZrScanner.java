package com.sun.tools.javac.util;

import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.parser.ZrJavaTokenizer;

import java.nio.CharBuffer;

public class ZrScanner extends Scanner {

    public ZrScanner(ScannerFactory scannerFactory, CharBuffer charBuffer) {
        this(scannerFactory, new ZrJavaTokenizer(scannerFactory, charBuffer));
    }

    public ZrScanner(ScannerFactory scannerFactory, char[] chars, int i) {
        this(scannerFactory, new ZrJavaTokenizer(scannerFactory, chars, i));
    }

    public ZrScanner(ScannerFactory scannerFactory, JavaTokenizer javaTokenizer) {
        super(scannerFactory,javaTokenizer);
    }
}
