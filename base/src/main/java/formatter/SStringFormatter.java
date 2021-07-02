package formatter;

import com.sun.tools.javac.parser.Item;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

public class SStringFormatter implements Formatter{
    Logger logger = Logger.getLogger(SStringFormatter.class.getSimpleName());
    @Override
    public String prefix() {
        return "$";
    }

    @Override
    public List<Item> stringRange2Group(JavaTokenizer javaTokenizer, char[] buf, List<GroupStringRange.StringRange> build, String text, int groupStartIndex) throws Exception {
        if (build.isEmpty()) return new ArrayList<>();
        int prefixLength = prefix().length();
        List<Item> items = new ArrayList<>();
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
        if (build.size()>0){
            GroupStringRange.StringRange stringRange = build.get(0);
            int startIndex = stringRange.startIndex;
            int endIndex = stringRange.endIndex;
            if (stringRange.codeStyle == 1) {
                items.add(Item.loadIdentifierToken(javaTokenizer, startIndex, startIndex, "String"));
                items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, startIndex,startIndex));
                items.add(Item.loadIdentifierToken(javaTokenizer,startIndex, startIndex, "valueOf"));
                items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex, startIndex));
                items.add(Item.loadJavacCode(startIndex, endIndex));
                items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex));
            } else if (stringRange.codeStyle == 0) {
                items.add(Item.loadStringToken(startIndex, startIndex, text.substring(startIndex,endIndex)));
            } else {
                logger.warning("[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]\n原始字符串："+text);
            }
            for (int i = 1; i < build.size(); i++) {
                stringRange = build.get(i);
                startIndex = stringRange.startIndex ;
                endIndex = stringRange.endIndex ;
                items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex,startIndex));
                if (stringRange.codeStyle == 1) {
                    items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
                    String str = text.substring(startIndex,endIndex);
                    String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'" , "$1\"$2\"")
                            .replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
                            .replace( "\\\\" , "\\");
                    int replaceCount = str.length() - toStr.length();
                    if (!Objects.equals(str, toStr)) {
                        logger.info( "替代后续文本 ${" + str + "}->${" + toStr + "}");
                        System.arraycopy(toStr.toCharArray(), 0,buf, startIndex, toStr.length());
                        char[] array = new char[replaceCount];
                        Arrays.fill(array, ' ');
                        System.arraycopy(array, 0, buf, groupStartIndex+startIndex + toStr.length(), replaceCount);
                    }
                    items.add(Item.loadJavacCode(startIndex, endIndex));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, prefixLength, prefixLength));
                } else if (stringRange.codeStyle == 0) {
                    items.add(Item.loadStringToken(startIndex, startIndex, text.substring(startIndex,endIndex)));
                }else {
                    logger.warning("[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]\n原始字符串："+text);
                }
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
    }

    @Override
    public String printOut(List<GroupStringRange.StringRange> build, String text) {
        StringBuilder stringBuilder=new StringBuilder();
        if (build.size() > 0) {
            stringBuilder.append( "(");
            if (build.get(0).codeStyle == 1) {
                stringBuilder.append( "String.valueOf(");
                stringBuilder.append(text, build.get(0).startIndex, build.get(0).endIndex);
                stringBuilder.append( ")");
            } else if (build.get(0).codeStyle == 0) {
                stringBuilder.append( "\"");
                stringBuilder.append(text, build.get(0).startIndex, build.get(0).endIndex);
                stringBuilder.append( "\"");
            } else {
                stringBuilder.append("[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
            }
            for (int i = 1; i < build.size(); i++) {
                stringBuilder.append( "+");
                GroupStringRange.StringRange stringRange = build.get(i);
                if (stringRange.codeStyle == 1) {
                    stringBuilder.append( "(");
                    stringBuilder.append(text, stringRange.startIndex, stringRange.endIndex);
                    stringBuilder.append( ")");
                } else if (stringRange.codeStyle == 0) {
                    stringBuilder.append( "\"");
                    stringBuilder.append(text, stringRange.startIndex, stringRange.endIndex);
                    stringBuilder.append( "\"");
                }else {
                    stringBuilder.append("[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                }
            }
            stringBuilder.append( ")");
        }
        return stringBuilder.toString();
    }
}
