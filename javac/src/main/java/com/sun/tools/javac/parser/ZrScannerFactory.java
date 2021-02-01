package com.sun.tools.javac.parser;

import com.sun.tools.javac.util.Context;

import java.nio.CharBuffer;

public class ZrScannerFactory extends ScannerFactory {

    public ZrScannerFactory(Context context) {
        super(preSuper(context));
//        System.out.println("OOScannerFactory");
    }
    public static ZrScannerFactory instance(Context var0) {
//        System.out.println("OOScannerFactory instance");
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
    public  void preSuper22(Context context) {

    }
//    public Scanner newScanner(CharSequence var1, boolean var2) {
//        try {
//            if (var1 instanceof CharBuffer) {
//                CharBuffer var4 = (CharBuffer)var1;
//                Constructor<?> constructor = OOScanner.class.getDeclaredConstructor(ScannerFactory.class, JavaTokenizer.class);
//                Constructor<JavadocTokenizer> javadocTokenizerConstructor = JavadocTokenizer.class.getDeclaredConstructor(ScannerFactory.class, CharBuffer.class);
//                Scanner scanner = var2 ? (Scanner) constructor.newInstance(this,javadocTokenizerConstructor.newInstance(this, var4)) : (Scanner) constructor.newInstance(this, var4);
//                return scanner;
//            } else {
//                char[] var3 = var1.toString().toCharArray();
//                return this.newScanner(var3, var3.length, var2);
//            }
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    public Scanner newScanner(char[] var1, int var2, boolean var3) {
//        try {
//            Constructor<?> constructor = OOScanner.class.getDeclaredConstructor(ScannerFactory.class, JavaTokenizer.class);
//            Constructor<JavadocTokenizer> javadocTokenizerConstructor = JavadocTokenizer.class.getDeclaredConstructor(ScannerFactory.class, CharBuffer.class);
//            Scanner scanner = var3 ? (Scanner) constructor.newInstance(this, javadocTokenizerConstructor.newInstance(this, var1, var2)) : (Scanner) constructor.newInstance(this, var1, var2);
//            return scanner;
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        }
//    }

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

//    public Scanner replace(Scanner scanner){
////        Object o = Proxy.newProxyInstance(OOScannerFactory.class.getClassLoader(), new Class[]{requestClass}, (proxy, method, args) -> {
////        });
//        OOProcessor.set(scanner,"tokenizer",new OOJavaTokenizer());
//    }


}
