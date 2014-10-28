package me.skyun.ideauml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * Created by linyun on 14-10-16.
 */
public class ClassUmlParser extends UmlParser {

    private PsiClass mPsiClass;
    private String mName;

    public ClassUmlParser(PsiClass psiClass) {
        this(psiClass, psiClass.getName());
    }

    public ClassUmlParser(PsiClass psiClass, String name) {
        super(psiClass);
        mPsiClass = psiClass;
        mName = name;
    }

    public String parse() {
        String uml = "";
        for (PsiMethod classMethod : mPsiClass.getMethods())
            uml += new MethodUmlParser(mName, classMethod).parse();
        uml = addIndent(uml);
        uml = String.format("\"call %s\" --> %s\n", mName, mName) + formatPartition(mName, uml);
        return uml;
    }
}
