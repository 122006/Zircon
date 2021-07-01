package formatter;


import com.sun.tools.javac.parser.ZrJavaTokenizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface Formatter {
    List<Formatter> FORMATTERS = new ArrayList<>();
    List<String> PREFIXES=new ArrayList<>();

    static List<Formatter> getAllFormatters() {
        if (!FORMATTERS.isEmpty()) {
            return FORMATTERS;
        }
        List<Class<? extends Formatter>> classes = Arrays.asList($StringFormatter.class, FStringFormatter.class);
        List<Formatter> collect = classes.stream().map(a -> {
            try {
                return a.newInstance();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(Objects::nonNull).collect(Collectors.toList());
        FORMATTERS.addAll(collect);
        return collect;
    }
    static List<String> getPrefixes(){
        if (!PREFIXES.isEmpty()) {
            return PREFIXES;
        }
        return getAllFormatters().stream().map(Formatter::prefix).collect(Collectors.toList());
    }

    public String prefix();

    public void code2Tokens(char[] buf,Group group, String searchStr) throws Exception;

    public String printOut(List<GroupStringRange.StringRange> build,String text);





}
