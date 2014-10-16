package me.skyun.java2uml;

import com.intellij.psi.PsiElement;

/**
 * Created by linyun on 14-10-16.
 */
public abstract class UmlParser {

    private PsiElement mPsiElement;

    public UmlParser(PsiElement psiElement) {
        mPsiElement = psiElement;
    }

    public abstract String parse();
}
