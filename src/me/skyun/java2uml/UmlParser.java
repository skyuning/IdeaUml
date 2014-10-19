package me.skyun.java2uml;

import com.intellij.psi.PsiElement;

/**
 * Created by linyun on 14-10-16.
 */
public abstract class UmlParser {

    private static final String PARTITION = "partition %s {\n%s\n}\n\n";

    private PsiElement mPsiElement;

    public UmlParser(PsiElement psiElement) {
        mPsiElement = psiElement;
    }

    public abstract String parse();

    protected String formatPartition(String name, String content) {
        return String.format(PARTITION, name, content);
    }

    protected static String addIndent(String uml) {
        String[] lines = uml.split("\n");
        uml = "";
        for (String l : lines)
            uml += "    " + l + "\n";
        return uml;
    }

//    protected static String alignLeft(String uml) {
//        String[] lines = uml.split("\n");
//        for (int i=0;)
//    }
}
