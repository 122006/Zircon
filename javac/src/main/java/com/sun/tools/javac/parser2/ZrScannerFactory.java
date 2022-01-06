package com.sun.tools.javac.parser2;

import com.sun.tools.javac.parser.Scanner;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.util.Context;

import java.nio.CharBuffer;

public class ZrScannerFactory extends ScannerFactory {

    public ZrScannerFactory(Context context) {
        super(preSuper(context));
    }
    public static ZrScannerFactory instance(Context var0) {
        Object var1 = var0.get(scannerFactoryKey);
        if (!(var1 instanceof ZrScannerFactory)) {
            var1 = new ZrScannerFactory(var0);
            var0.put(scannerFactoryKey,(ZrScannerFactory)var1);
        }
        return (ZrScannerFactory) var1;
    }
    private static Context preSuper(Context context) {
        context.put(ScannerFactory.scannerFactoryKey,(ScannerFactory) null);
        return context;
    }

    public Scanner newScanner(CharSequence var1, boolean var2) {
        if (var1 instanceof CharBuffer) {
            CharBuffer var4 = (CharBuffer)var1;
            return var2 ? new ZrScanner(this, new ZrJavadocTokenizer(this, var4)) : new ZrScanner(this, var4);
        } else {
            char[] var3 = var1.toString().toCharArray();
            return this.newScanner(var3, var3.length, var2);
        }
    }

    public Scanner newScanner(char[] var1, int var2, boolean var3) {
        return var3 ? new ZrScanner(this, new ZrJavadocTokenizer(this, var1, var2)) : new ZrScanner(this, var1, var2);
    }


}
