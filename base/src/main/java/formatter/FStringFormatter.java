package formatter;

import com.sun.tools.javac.parser.JavaTokenizer;
import com.sun.tools.javac.parser.Tokens;
import com.sun.tools.javac.parser.UnicodeReader;
import com.sun.tools.javac.parser.ZrJavaTokenizer;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class FStringFormatter implements Formatter{
    @Override
    public Predicate<String> prefix() {
        return s -> Objects.equals("f",s);
    }

    public void code2Tokens(ZrJavaTokenizer tokenizer,Group group, String searchStr)throws Exception{
        List<GroupStringRange.StringRange> build = GroupStringRange.build(searchStr);
        int searchIndex = group.mappingStartIndex;
        UnicodeReader reader= (UnicodeReader) ReflectionUtil.getDeclaredField(tokenizer,JavaTokenizer.class,"reader");
        char[] buf= (char[]) ReflectionUtil.getDeclaredField(reader,UnicodeReader.class,"buf");
        group.loadIdentifierToken(searchIndex, searchIndex + 1, "String");
        group.loadCommaToken(Tokens.TokenKind.DOT, searchIndex+ 1, searchIndex + 1);
        group.loadIdentifierToken(searchIndex+ 1, searchIndex + 1, "format");
        group.loadCommaToken(Tokens.TokenKind.LPAREN, searchIndex + 1, searchIndex + 1);
        group.loadStringToken(searchIndex + 1, searchIndex + 1, GroupStringRange.map2FormatString(searchStr, build));
        for (int i = 0; i < build.size(); i++) {
            GroupStringRange.StringRange a = build.get(i);
            if (a.codeStyle != 0 && a.codeStyle != 1) continue;
            if (a.codeStyle==1) {
                int startIndex = a.startIndex + group.mappingStartIndex;
                int endIndex = a.endIndex + group.mappingStartIndex;
                group.loadCommaToken(Tokens.TokenKind.COMMA, endIndex, endIndex);
                String str = searchStr.substring(a.startIndex, a.endIndex );
                String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'" , "$1\"$2\"")
                        .replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
                        .replace( "\\\\" , "\\");
                int replaceCount = str.length() - toStr.length();
                if (!Objects.equals(str, toStr)) {
                    tokenizer.log( "替代后续文本 ${" + str + "}->${" + toStr + "}");
                    System.arraycopy(toStr.toCharArray(), 0, buf, startIndex, toStr.length());
                    char[] array = new char[replaceCount];
                    Arrays.fill(array, ' ');
                    System.arraycopy(array, 0, buf, startIndex + toStr.length(), replaceCount);
                }
                group.items.add(new Item(startIndex, endIndex, null));
            }
        }
        group.loadCommaToken(Tokens.TokenKind.RPAREN, group.mappingEndIndex, group.mappingEndIndex);
    }

    @Override
    public String printOut(List<GroupStringRange.StringRange> build,String text) {
        StringBuilder stringBuilder=new StringBuilder();
        stringBuilder.append( "String.format(\"");
        stringBuilder.append(GroupStringRange.map2FormatString(text, build));
        stringBuilder.append( "\"");
        for (GroupStringRange.StringRange a : build) {
            if (a.codeStyle != 0 && a.codeStyle != 1) continue;
            String str = text.substring(a.startIndex, a.endIndex);
            if (a.codeStyle == 1) {
                stringBuilder.append( ",");
                String toStr = str.replaceAll( "(^|[^\\\\])'([^']+?[^\\\\'])?'" , "$1\"$2\"")
                        .replaceAll( "\\\\?([a-z0-9\"']{1})" , "$1")
                        .replace( "\\\\" , "\\");
                stringBuilder.append(toStr);
            }
        }
        stringBuilder.append( ")");
        return stringBuilder.toString();
    }
}
