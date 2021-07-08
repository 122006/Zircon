package formatter;

import com.sun.tools.javac.parser.Item;
import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class FStringFormatter implements Formatter {
    Logger logger = Logger.getLogger(FStringFormatter.class.getSimpleName());

    @Override
    public String prefix() {
        return "f";
    }

    @Override
    public String printOut(List<StringRange> build, String text) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append( "String.format(\"" );
        stringBuilder.append(map2FormatString(text, build));
        stringBuilder.append( "\"" );
        for (StringRange a : build) {
            if (a.codeStyle != 0 && a.codeStyle != 1) continue;
            if (a.codeStyle == 1) {
                stringBuilder.append( "," );
                String toStr = a.stringVal;
                stringBuilder.append(toStr);
            }
        }
        stringBuilder.append( ")" );
        return stringBuilder.toString();
    }

    public static String map2FormatString(String text, List<StringRange> ranges) {
        StringRange formatRange = null;
        StringBuilder stringBuilder = new StringBuilder();
        for (StringRange range : ranges) {
            if (range.codeStyle == 2) formatRange = range;
            else if (range.codeStyle == 0) {
                stringBuilder.append(range.stringVal);
            } else if (range.codeStyle == 1) {
                if (formatRange == null) {
                    stringBuilder.append( "%s" );
                } else {
                    stringBuilder.append(range.stringVal);
                    formatRange = null;
                }
            }
        }
        return stringBuilder.toString();
    }

    @Override
    public List<Item> stringRange2Group(JavaTokenizer javaTokenizer, char[] buf, List<StringRange> build, String text, int groupStartIndex) throws Exception {
        List<Item> items = new ArrayList<>();
        if (build.isEmpty()) {
            items.add(Item.loadStringToken(0, 0, "" ));
            return items;
        }
        int prefixLength = prefix().length();

        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "String" ));
        items.add(Item.loadCommaToken(Tokens.TokenKind.DOT, prefixLength, prefixLength));
        items.add(Item.loadIdentifierToken(javaTokenizer, 0, prefixLength, "format" ));
        items.add(Item.loadCommaToken(Tokens.TokenKind.LPAREN, prefixLength, prefixLength));
        StringBuilder formatStr = new StringBuilder();
        String itemFormat = null;
        for (int i = 0; i < build.size(); i++) {
            StringRange a = build.get(i);
            if (a.codeStyle == 0) {
                formatStr.append(a.stringVal);
            } else if (a.codeStyle == 2) {
                itemFormat = text.substring(a.startIndex, a.endIndex);
            } else if (a.codeStyle == 1) {
                if (itemFormat != null) {
                    formatStr.append(itemFormat);
                    itemFormat = null;
                } else formatStr.append( "%s" );
            }
        }
        items.add(Item.loadStringToken(prefixLength, prefixLength, formatStr.toString()));
        for (int i = 0; i < build.size(); i++) {
            StringRange a = build.get(i);
            int startIndex = a.startIndex;
            int endIndex = a.endIndex;
            if (a.codeStyle == 1) {
                items.add(Item.loadCommaToken(Tokens.TokenKind.COMMA, endIndex, endIndex));
                codeTransfer(buf, groupStartIndex, text, startIndex, endIndex);
                items.add(Item.loadJavacCode(startIndex, endIndex));
            }
        }
        items.add(Item.loadCommaToken(Tokens.TokenKind.RPAREN, text.length(), text.length()));
        return items;
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
                            String substring = text.substring(startI, thisIndex);
                            if (substring.startsWith( "%" )) {
                                int splitChar = substring.indexOf( ":" );
                                if (splitChar == -1) {
                                    list.add(StringRange.code(this, text, startI, thisIndex));
                                } else {
                                    list.add(StringRange.of(2, startI, startI + splitChar));
                                    list.add(StringRange.code(this, text, startI + splitChar + 1, thisIndex));
                                }
                            } else {
                                list.add(StringRange.code(this, text, startI, thisIndex));
                            }
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
        return str.replace( "%", "%%" ).replace( "\\$", "$" );
    }

}
