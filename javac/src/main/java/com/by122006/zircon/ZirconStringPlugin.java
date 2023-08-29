package com.by122006.zircon;

import com.sun.tools.javac.main.JavaCompiler;
import com.sun.tools.javac.parser.ParserFactory;
import com.sun.tools.javac.parser.ScannerFactory;
import com.sun.tools.javac.util.Context;

public class ZirconStringPlugin extends ZirconPlugin {


    @Override
    public String getName() {
        return "ZrString";
    }

    @Override
    public String getCName() {
        return "模板字符串";
    }


    @Override
    public void startTask(Context context, JavaCompiler compiler, ClassLoader pcl, ClassLoader classLoader) throws Exception {

            reloadClass("com.sun.tools.javac.parser.Item", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.Formatter", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.SStringFormatter", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.FStringFormatter", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.ZrStringModel", pcl, classLoader);
            reloadClass("com.sun.tools.javac.parser.StringRange", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.parser.ZrJavaTokenizer$JavaCException", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.parser.ZrJavaTokenizer", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.parser.ZrParserFactory", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.util.ZrJavadocTokenizer", pcl, classLoader);
            reloadClassJavacVersion("com.sun.tools.javac.util.ZrScanner", pcl, classLoader);
            Class<?> OOScannerFactoryClass = reloadClassJavacVersion("com.sun.tools.javac.util.ZrScannerFactory", pcl, classLoader);
            context.get(ScannerFactory.scannerFactoryKey);
            ParserFactory parserFactory = (ParserFactory) get(compiler, "parserFactory");
            Object instance = getInstance(OOScannerFactoryClass, context);
            set(parserFactory, "scannerFactory", instance);
    }

}
