package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Context;

import java.nio.CharBuffer;

public class OOParserFactory  extends ParserFactory{
    private Context context;

    protected OOParserFactory(Context context) {
        super(context);
        this.context = context;
    }
    public JavacParser newParser(CharSequence var1, boolean var2, boolean var3, boolean var4) {
        Scanner var5 = newScanner(var1, var2);
        System.out.println("newParser: "+var5);
        return new JavacParser(this, var5, var2, var4, var3);
    }
    public Scanner newScanner(CharSequence var1, boolean var2) {
        ScannerFactory scannerFactory=ScannerFactory.instance(context);
        if (var1 instanceof CharBuffer) {
            CharBuffer var4 = (CharBuffer)var1;
            return var2 ? new OOScanner(scannerFactory, new JavadocTokenizer(scannerFactory, var4)) : new OOScanner(scannerFactory, var4);
        } else {
            char[] var3 = var1.toString().toCharArray();
            return this.newScanner(var3, var3.length, var2);
        }
    }
    public Scanner newScanner(char[] var1, int var2, boolean var3) {
        return var3 ? new OOScanner(scannerFactory, new JavadocTokenizer(scannerFactory, var1, var2)) : new OOScanner(scannerFactory, var1, var2);
    }
}
