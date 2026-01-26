package com.meitou.admin.util;

/**
 * 标题生成工具类
 */
public class TitleUtil {

    /**
     * 根据内容生成标题
     * 截取第一句话，如果还有内容则加上省略号
     * 
     * @param content 内容
     * @return 标题
     */
    public static String generateTitle(String content) {
        if (content == null || content.isEmpty()) {
            return "无标题";
        }
        
        // 截取第一句话
        // 分隔符：句号、感叹号、问号、换行符 (中英文)
        String[] delimiters = {"。", "！", "？", "\n", ".", "!", "?"};
        int minIndex = content.length();
        
        for (String delimiter : delimiters) {
            int index = content.indexOf(delimiter);
            if (index != -1 && index < minIndex) {
                // 包含分隔符
                minIndex = index + 1;
            }
        }
        
        String title = content.substring(0, minIndex);
        
        // 如果截取后还有内容，或者原内容被截断（比如最大长度限制），则添加省略号
        if (minIndex < content.length()) {
            title += "...";
        }
        
        // 限制最大长度，防止第一句话太长
        if (title.length() > 50) {
            title = title.substring(0, 50) + "...";
        }
        
        return title;
    }
}
