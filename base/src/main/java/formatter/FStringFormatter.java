package formatter;

import com.sun.tools.javac.parser.Item;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class FStringFormatter implements Formatter {
    Logger logger = Logger.getLogger(FStringFormatter.class.getSimpleName());

    @Override
    public String prefix() {
        return "f";
    }

    @Override
    public String printOut(List<GroupStringRange.StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append( "String.format(\"" );
        stringBuilder.append(GroupStringRange.map2FormatString(text, build));
        stringBuilder.append( "\"" );
        for (GroupStringRange.StringRange a : build) {
            if (a.codeStyle != 0 && a.codeStyle != 1) continue;
            String str = text.substring(a.startIndex, a.endIndex);
            if (a.codeStyle == 1) {
                stringBuilder.append( "," );
                String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'", "$1\"$2\"" )
                        .replaceAll( "\\\\?([a-z0-9\"']{1})", "$1" )
                        .replace( "\\\\", "\\" );
                stringBuilder.append(toStr);
            }
        }
        stringBuilder.append( ")" );
        return stringBuilder.toString();
    }

    @Override
    public List<Item> stringRange2Group(JavaTokenizer javaTokenizer, char[] buf, List<GroupStringRange.StringRange> build, String text, int groupStartIndex) throws Exception {
        if (build.isEmpty()) return new ArrayList<>();
        int prefixLength = prefix().length();
        List<Item> items = new ArrayList<>();
        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "String" ));
        items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "format" ));
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
        StringBuilder formatStr = new StringBuilder();
        String itemFormat = null;
        for (int i = 0; i < build.size(); i++) {
            GroupStringRange.StringRange a = build.get(i);
            String str = text.substring(a.startIndex, a.endIndex);
            if (a.codeStyle == 0) {
                formatStr.append(str);
            } else if (a.codeStyle == 2) {
                itemFormat = text.substring(a.startIndex, a.endIndex);
            } else if (a.codeStyle == 1) {
                if (itemFormat != null) {
                    formatStr.append(itemFormat);
                    items = null;
                } else formatStr.append( "%s" );
            }
        }
        items.add(Item.loadStringToken(prefixLength, prefixLength, formatStr.toString()));
        for (int i = 0; i < build.size(); i++) {
            GroupStringRange.StringRange a = build.get(i);
            int startIndex = a.startIndex;
            int endIndex = a.endIndex;
            items.add(Item.loadCommaToken(Tokens.TokenKind.COMMA, endIndex, endIndex));
            if (a.codeStyle == 1) {
                String str = text.substring(a.startIndex, a.endIndex);
                String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'", "$1\"$2\"" )
                        .replaceAll( "\\\\?([a-z0-9\"']{1})", "$1" )
                        .replace( "\\\\", "\\" );
                int replaceCount = str.length() - toStr.length();
                if (!Objects.equals(str, toStr)) {
                    logger.info( "替代后续文本 ${" + str + "}->${" + toStr + "}" );
                    System.arraycopy(toStr.toCharArray(), 0, buf, startIndex, toStr.length());
                    char[] array = new char[replaceCount];
                    Arrays.fill(array, ' ');
                    System.arraycopy(array, 0, buf, groupStartIndex + startIndex + toStr.length(), replaceCount);
                }
                items.add(Item.loadJavacCode(startIndex, endIndex));
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
    }
}
