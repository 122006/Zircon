package formatter;

import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.parser.UnicodeReader;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class $StringFormatter implements Formatter{
    @Override
    public String prefix() {
        return "$";
    }

    @Override
    public void code2Tokens(ZrJavaTokenizer tokenizer,Group group, String searchStr) throws Exception {
        List<GroupStringRange.StringRange> build = GroupStringRange.build(searchStr);
        int searchIndex = group.mappingStartIndex;
        UnicodeReader reader= (UnicodeReader) ReflectionUtil.getDeclaredField(tokenizer, JavaTokenizer.class,"reader");
        char[] buf= (char[]) ReflectionUtil.getDeclaredField(reader,UnicodeReader.class,"buf");
        group.loadCommaToken(Tokens.TokenKind.LPAREN, searchIndex, searchIndex + 1);
        if (build.size()>0){
            GroupStringRange.StringRange stringRange = build.get(0);
            int startIndex = stringRange.startIndex + group.mappingStartIndex;
            int endIndex = stringRange.endIndex + group.mappingStartIndex;
            if (stringRange.codeStyle == 1) {
                group.loadIdentifierToken(startIndex, startIndex, "String");
                group.loadCommaToken(Tokens.TokenKind.DOT, startIndex,startIndex);
                group.loadIdentifierToken(startIndex, startIndex, "valueOf");
                group.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex, startIndex);
                group.items.add(new Item(startIndex, endIndex, null));
                group.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex);
            } else if (stringRange.codeStyle == 0) {
                group.loadStringToken(startIndex, endIndex,searchStr.substring(stringRange.startIndex, stringRange.endIndex));
            } else {
                tokenizer.throwError(startIndex,"[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
            }
            for (int i = 1; i < build.size(); i++) {
                stringRange = build.get(i);
                startIndex = stringRange.startIndex + group.mappingStartIndex;
                endIndex = stringRange.endIndex + group.mappingStartIndex;
                group.loadCommaToken(Tokens.TokenKind.PLUS, startIndex,startIndex);
                if (stringRange.codeStyle == 1) {
                    group.loadCommaToken(Tokens.TokenKind.LPAREN, startIndex,startIndex);
                    String str = searchStr.substring(stringRange.startIndex,stringRange.endIndex);
                    String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'" , "$1\"$2\"")
                            .replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
                            .replace( "\\\\" , "\\");
                    int replaceCount = str.length() - toStr.length();
                    if (!Objects.equals(str, toStr)) {
                        tokenizer.log( "替代后续文本 ${" + str + "}->${" + toStr + "}");
                        System.arraycopy(toStr.toCharArray(), 0,buf, startIndex, toStr.length());
                        char[] array = new char[replaceCount];
                        Arrays.fill(array, ' ');
                        System.arraycopy(array, 0, buf, startIndex + toStr.length(), replaceCount);
                    }
                    group.items.add(new Item(startIndex,endIndex, null));
                    group.loadCommaToken(Tokens.TokenKind.RPAREN, endIndex, endIndex);
                } else if (stringRange.codeStyle == 0) {
                    group.loadStringToken(startIndex, endIndex
                            ,searchStr.substring(stringRange.startIndex, stringRange.endIndex));
                }else {
                    tokenizer.throwError(startIndex,"[error(使用了$字符串语法不支持格式化字符串功能，请使用f前缀字符串)]");
                }
            }
        }
        group.loadCommaToken(Tokens.TokenKind.RPAREN, group.mappingEndIndex, group.mappingEndIndex);
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
