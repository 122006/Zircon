package com.by122006.zircon;


public class Magic {
    /**
     * <a href="https://github.com/122006/Zircon">Zircon</a>
     * <p>
     * 这是一个动态字符串标识方法。<br>
     * <br>
     * <code>
     * String add="world";<br>
     * String will=<b>$(</b>"hello ${add}"<b>)</b>;<br>
     * assert "hello world".equals(will);
     * </code>
     * <br><br>
     * <b>该方法不会实际调用，主要逻辑使用javac进行重写</b><br>
     * <br>
     * FAQ:<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:如何让idea自动补全？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:<a href="https://github.com/122006/Zircon">参考README.md文件</a>，在idea中配置Language Injection<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:我的字符串为什么没有变化？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:该功能实现需要配合插件使用，请<a href="https://github.com/122006/Zircon">参考README.md文件</a>确认您的配置正确性 <br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:我怎么确认我的字符串替换成功？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:观察程序编译时控制台输出，是否提示加载该插件<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:字符串替换出错了？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:${}中需要使用正确的java代码。其中'string'会被替换为"string",如果还需要使用char，使用转义\'c\'<br>
     *
     * @param o 接受一个字符串字段，你可以在其中增加${}代码标识，以格式化特殊字符串
     * @return 格式化后的字符串
     */
    public static String $(String o) {
        return o;
    }

    /**
     * <a href="https://github.com/122006/Zircon">Zircon</a>
     * <p>
     * 如果你在代码中调用该方法：<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;会对内部字符串参数进行格式化<br>
     * <p>
     * 如果你在生成class中发现该方法：<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;该方法为字符串参数合并的实现<br>
     * 单参数方法{@link com.by122006.zircon.Magic#$(java.lang.String)  $(String)}
     *
     * @param s 需要拼接的参数
     * @return 格式化后字符串
     */
    public static String $(Object... s) {
        if (s.length == 0) return "";
        if (s.length == 1) return String.valueOf(s[0]);
        StringBuilder stringBuilder = new StringBuilder();
        for (Object o : s) {
            stringBuilder.append(o);
        }
        return stringBuilder.toString();
    }
    /**
     * <a href="https://github.com/122006/Zircon">Zircon</a>
     * <p>
     * 这是一个动态字符串标识方法。<br>
     * <br>
     * <code>
     * String add="world";<br>
     * String will=<b>$(</b>"hello ${add}"<b>)</b>;<br>
     * assert "hello world".equals(will);
     * </code>
     * <br><br>
     * <b>该方法不会实际调用，主要逻辑使用javac进行重写</b><br>
     * <br>
     * FAQ:<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:如何让idea自动补全？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:<a href="https://github.com/122006/Zircon">参考README.md文件</a>，在idea中配置Language Injection <br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:我的字符串为什么没有变化？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:该功能实现需要配合插件使用，请<a href="https://github.com/122006/Zircon">参考README.md文件</a>确认您的配置正确性 <br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:我怎么确认我的字符串替换成功？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:观察程序编译时控制台输出，是否提示加载该插件<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;F:字符串替换出错了？<br>
     * &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Q:${}中需要使用正确的java代码。其中'string'会被替换为"string",如果还需要使用char，使用转义\'c\'<br>
     *
     * @param s 接受一个字符串字段，你可以在其中增加${}代码标识，以格式化特殊字符串
     * @return 格式化后的字符串
     */
    public static String $(String... s) {
        if (s.length == 0) return "";
        if (s.length == 1) return String.valueOf(s[0]);
        StringBuilder stringBuilder = new StringBuilder();
        for (Object o : s) {
            stringBuilder.append(o);
        }
        return stringBuilder.toString();
    }
}
