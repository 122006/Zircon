package formatter;

import com.sun.tools.javac.parser.Item;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class SStringFormatter implements Formatter {
    Logger logger = Logger.getLogger(SStringFormatter.class.getSimpleName());

    @Override
    public String prefix() {
        return "$";
    }

    @Override
    public List<Item> stringRange2Group(JavaTokenizer javaTokenizer, char[] buf, List<StringRange> build, String text, int groupStartIndex) throws Exception {
        List<Item> items = new ArrayList<>();
        if (build.isEmpty()) {
            items.add(Item.loadStringToken(0, 0, "" ));
            return items;
        }
        int prefixLength = prefix().length();
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
        if (build.size() > 0) {
            for (int i = 0; i < build.size(); i++) {
                StringRange stringRange = build.get(i);
                int startIndex = stringRange.startIndex;
                int endIndex = stringRange.endIndex;
                if (stringRange.codeStyle == 1) {
                    if (i == 0) {
                        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "String" ));
                        items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
                        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "valueOf" ));
                    } else {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
                    codeTransfer(buf, groupStartIndex, text, startIndex, endIndex);
                    items.add(Item.loadJavacCode(startIndex, endIndex));
                    items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, prefixLength, prefixLength));
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0) {
                        items.add(Item.loadCommaToken(Tokens.TokenKind.PLUS, startIndex, startIndex));
                    }
                    items.add(Item.loadStringToken(startIndex, startIndex, stringRange.stringVal));
                } else {
                    logger.warning( "[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]\n原始字符串：" + text);
                }
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
    }

    @Override
    public String printOut(List<StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
        if (build.size() > 0) {
            stringBuilder.append( "(" );
            for (int i = 0; i < build.size(); i++) {
                StringRange stringRange = build.get(i);
                if (stringRange.codeStyle == 1) {
                    if (i == 0) {
                        stringBuilder.append( "String.valueOf" );
                    } else {
                        stringBuilder.append( "+" );
                    }
                    stringBuilder.append( "(" );
                    stringBuilder.append(stringRange.stringVal);
                    stringBuilder.append( ")" );
                } else if (stringRange.codeStyle == 0) {
                    if (i > 0)
                        stringBuilder.append( "+" );
                    stringBuilder.append( "\"" );
                    stringBuilder.append(stringRange.stringVal);
                    stringBuilder.append( "\"" );
                } else {
                    stringBuilder.append( "[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]" );
                }
            }
            stringBuilder.append( ")" );
        }
        return stringBuilder.toString();
    }

    @Override
    public List<StringRange> build(String text) {
        List<StringRange> list = new ArrayList<>();
        int startI = prefix().length() + 1;
        int selectModel = -1;
        int pCount = 0;
        for (int thisIndex = startI; thisIndex < text.length() - 1; thisIndex++) {
            char ch = text.charAt(thisIndex);
            if (text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) != '\\') {
                continue;
            }
            if (selectModel == 2) {
                if (ch == '{') pCount++;
                if (ch == '}') {
                    pCount--;
                    if (pCount == 0) {
                        if (thisIndex - startI > 0) {
                            list.add(StringRange.code(this, text, startI, thisIndex));
                        }
                        startI = thisIndex + 1;
                        selectModel = -1;
                    }
                }
                continue;
            }
            if (selectModel == 1) {
                if (String.valueOf(ch).matches(pCount > 0 ? "[^)]{1}" : "[A-Za-z0-9_\\u4e00-\\u9fa5.$]{1}" )) continue;
                if (ch == '(') {
                    if (text.substring(thisIndex).matches( "^\\([^)]*\\).*" )) {
                        pCount++;
                        continue;
                    }
                } else if (ch == ')') {
                    if (pCount > 0) {
                        if (text.substring(thisIndex).matches( "^\\)\\.[A-Za-z_\\u4e00-\\u9fa5$]+.*" )) {
                            pCount--;
                            continue;
                        }
                        thisIndex++;
                    }
                }
                list.add(StringRange.code(this, text, startI, thisIndex));
                startI = thisIndex;
                selectModel = -1;
                continue;
            }
            if (ch == '$' && !(text.charAt(thisIndex - 1) == '\\' && text.charAt(thisIndex - 2) == '\\')
                    && String.valueOf(text.charAt(thisIndex + 1)).matches( "[A-Za-z_\\u4e00-\\u9fa5{$]{1}" )) {
                if (thisIndex - startI != 0)
                    list.add(StringRange.string(this, text, startI, thisIndex));
                if (text.charAt(thisIndex + 1) == '{') {
                    startI = thisIndex + 2;
                    selectModel = 2;
                    pCount = 0;
                } else {
                    startI = thisIndex + 1;
                    selectModel = 1;
                    pCount = 0;
                }
            }
        }
        if (text.length() - 1 > startI) {
            if (selectModel > 0) {
                list.add(StringRange.code(this, text, startI, text.length() - 1));
            } else {
                list.add(StringRange.string(this, text, startI, text.length() - 1));
            }
        }
        return list;
    }

    @Override
    public String stringTransfer(String str) {
        return str.replace( "\\$", "$" );
    }
}
