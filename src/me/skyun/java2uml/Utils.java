package me.skyun.java2uml;

/**
 * Created by linyun on 14-10-16.
 */
public class Utils {
    public static String join(String[] array, String jointer) {
        if (array == null || array.length == 0)
            return "";

        String result = array[0];
        for (int i = 1; i < array.length; i++) {
            result += jointer + array[i];
        }
        return result;
    }

    public static String[] arrayAppend(String[] array, String appendStr) {
        String[] newArray;
        if (array != null) {
            newArray = new String[array.length + 1];
            for (int i = 0; i < array.length; i++)
                newArray[i] = array[i];
        } else
            newArray = new String[1];
        newArray[newArray.length - 1] = appendStr;
        return newArray;
    }

    public static String multiLineJoin(String multiLineText, String jointer) {
        String[] lines = multiLineText.split("\n");
        if (lines.length == 0)
            return "";

        String result = lines[0];
        for (int i = 1; i < lines.length; i++) {
            result += jointer + lines[i].trim();
        }
        return result;
    }
}
