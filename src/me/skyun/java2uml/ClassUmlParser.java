package me.skyun.java2uml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;

/**
 * Created by linyun on 14-10-16.
 */
public class ClassUmlParser extends UmlParser {

    private static String UML_CLASS_DIVIDER = "'========== %s ==========\n";

    private String mUml = "";
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
        mUml += "partition " + mName + "\n";
        PsiMethod[] classMethods = mPsiClass.getMethods();
        mUml += String.format(JavaUmlParser.UML_DIVIDER, "Methods");
        for (PsiMethod classMethod : classMethods) {
            String methodUml = new CodeBlockUmlParser(
                mPsiClass, classMethod.getBody(), classMethod.getName() + "()"
            ).parse();
            mUml += methodUml;
        }

        mUml += "}\n\n";
        return mUml;
    }
}
