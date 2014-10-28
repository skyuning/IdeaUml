package me.skyun.ideauml;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaFile;

/**
 * Created by linyun on 14-10-16.
 */
public class JavaUmlParser extends UmlParser {

    private static String UML_START = "@startuml\n\n";
    private static String UML_END = "@enduml";

    private static String UML_SKINPARAM = "skinparam activity {\n"
        + "  BackgroundColor<< Begin >> Olive\n"
        + "}" + "\n\n";

    private PsiJavaFile mPsiJavaFile;

    public JavaUmlParser(PsiJavaFile psiJavaFile) {
        super(psiJavaFile);
        mPsiJavaFile = psiJavaFile;
    }

    private String getJavaUml() {
        String umlCode = UML_START;
        umlCode += UML_SKINPARAM;
        for (PsiClass psiClass : mPsiJavaFile.getClasses())
            umlCode += new ClassUmlParser(psiClass).parse();
        umlCode += UML_END;
        return umlCode;
    }

    @Override
    public String parse() {
        return getJavaUml();
    }
}
