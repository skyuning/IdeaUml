package me.skyun.java2uml;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;

import org.stathissideris.ascii2image.core.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by linyun on 14-10-16.
 */
public class MethodUmlParser extends UmlParser {

    private static String CONTINUE_STATEMENT = "-d-> %s\n";
    private static String CODE_FRAG = "-d-> code fragment: (%d-%d)\n";
    private static String REFERENCE = "\"%s\" -r-> [%s] %s << Begin >>\n";

    private PsiMethod mPsiMethod;

    public MethodUmlParser(PsiMethod psiMethod) {
        super(psiMethod);
        mPsiMethod = psiMethod;
    }

    @Override
    public String parse() {
        String fullMethodName = getFullMethodName(mPsiMethod);
        String uml = String.format("=== %s_start === ", fullMethodName) + parseStatements();
        uml = addIndent(uml);
        uml = formatPartition(fullMethodName, uml);
        return uml;
//        uml += getInnerClassUml();
    }

    private String getInnerClassUml() {
        String innerClassUml = "";
        List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(mPsiMethod.getBody(), PsiAnonymousClass.class, true);
        for (int i = 0; i < anonyClasses.size(); i++) {
            PsiJavaCodeReferenceElement referenceElement =
                PsiUtils.findPsiElement(anonyClasses.get(i), PsiJavaCodeReferenceElement.class);
            String innerClassName = getFullMethodName(mPsiMethod) + "#" + i + referenceElement.getText();
            innerClassUml += new ClassUmlParser(anonyClasses.get(i), innerClassName).parse();
        }
        return innerClassUml;
    }

    private String parseStatements() {
        String uml = "";
        String callUml = "";
        List<PsiStatement> codeFrag = new ArrayList<PsiStatement>();
        for (PsiStatement statement : mPsiMethod.getBody().getStatements()) {
            // handle anonymous class
            List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(statement, PsiAnonymousClass.class, true);
            if (!anonyClasses.isEmpty()) {
                uml += String.format(CONTINUE_STATEMENT, getSimpleText(statement));
                continue;
            }

            // get resolved methods in statement
            List<PsiMethodCallExpression> statementCalls =
                PsiUtils.findPsiElements(statement, PsiMethodCallExpression.class, true);
            List<PsiMethod> resolvedMethods = new ArrayList<PsiMethod>();
            for (PsiMethodCallExpression call : statementCalls) {
                PsiReferenceExpression callRef = PsiUtils.findPsiElement(call, PsiReferenceExpression.class);
                PsiMethod resolvedMethod = (PsiMethod) callRef.resolve();
                if (resolvedMethod != null)
                    resolvedMethods.add(resolvedMethod);
            }

            // normal statement with no resolved method call, add to code fragment
            if (resolvedMethods.isEmpty()) {
                codeFrag.add(statement);
                continue;
            }

            // is a method call, parse pre codeFrag first
            uml += getCodeFragmentUml(codeFrag);

            // parse the method call
            for (PsiMethod resolvedMthod : resolvedMethods) {
                uml += String.format(CONTINUE_STATEMENT, getSimpleText(statement, false));
                String refMethodFullName = getFullMethodName(resolvedMthod);
                callUml += String.format(REFERENCE, getSimpleText(statement, false), resolvedMthod.getName(), refMethodFullName);
            }
        }
        uml += getCodeFragmentUml(codeFrag);
        return uml + callUml;
    }

    private String getCodeFragmentUml(List<PsiStatement> codeFrag) {
        if (codeFrag.isEmpty())
            return "";

        int codeFragStart = codeFrag.get(0).getTextOffset();
        int codeFragEnd = codeFrag.get(codeFrag.size() - 1).getTextRange().getEndOffset();
        String fragNode = String.format(CODE_FRAG, codeFragStart, codeFragEnd);

        String note = "";
        for (PsiStatement fragStatement : codeFrag)
            note += fragStatement.getText() + "\n";
        note = addIndent(note);
        note = "note right\n" + note + "end note\n\n";
        note = addIndent(note);
        codeFrag.clear();
        return fragNode + note;
    }

    private String getReferenceUml() {
        if (mPsiMethod.getBody().getStatements().length == 0)
            return "";
        List<PsiReferenceExpression> references = PsiUtils.findPsiElements(mPsiMethod.getBody(), PsiReferenceExpression.class, true);
        if (references.isEmpty())
            return "";
        List<Pair<PsiStatement, PsiMethod>> statementRefs = new ArrayList<Pair<PsiStatement, PsiMethod>>();
        for (PsiReferenceExpression ref : references) {
            if (!(ref.resolve() instanceof PsiMethod))
                continue;
            PsiStatement statement = PsiUtils.getContainingParent(ref, PsiStatement.class);
            Pair<PsiStatement, PsiMethod> pair = new Pair<PsiStatement, PsiMethod>(statement, (PsiMethod) ref.resolve());
            statementRefs.add(pair);
        }
        if (statementRefs.isEmpty())
            return "";

        String referenceUml = "";
        for (Pair<PsiStatement, PsiMethod> pair : statementRefs)
            referenceUml += String.format(CONTINUE_STATEMENT, getSimpleText(pair.first));
        for (Pair<PsiStatement, PsiMethod> pair : statementRefs)
            referenceUml += String.format(REFERENCE, getSimpleText(pair.first, false),
                pair.second.getName(), getFullMethodName(pair.second));
        return referenceUml + "\n";
    }

    private static String getSimpleText(PsiElement element) {
        return getSimpleText(element, true);
    }

    private static String getSimpleText(PsiElement element, boolean endline) {
        PsiElement _element = element.copy();
        List<PsiAnonymousClass> innerClasses = PsiUtils.findPsiElements(_element, PsiAnonymousClass.class, true);
        for (PsiAnonymousClass innerClass : innerClasses)
            innerClass.deleteChildRange(PsiUtils.findJavaToken(innerClass, "{"), PsiUtils.findJavaToken(innerClass, "}"));
        String uml = _element.getText().replace("\n", "\\n");
        if (endline)
            uml += "\n";
        return uml;
    }

    public static String getFullMethodName(PsiMethod method) {
        return method.getContainingClass().getName() + "__" + method.getName();
    }

    private static String getIfUml(PsiIfStatement ifStatement) {
        String ifUml = "if " + getSimpleText(ifStatement.getCondition());

        if (ifStatement.getThenBranch() instanceof PsiExpressionStatement) {
            ifUml += "  --> [yes] " + getSimpleText(ifStatement.getThenBranch());
        } else if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement thenBlock = (PsiBlockStatement) ifStatement.getThenBranch();
            PsiStatement[] thenStatements = thenBlock.getCodeBlock().getStatements();
            ifUml += "  --> [yes] " + getSimpleText(thenStatements[0]);
            for (int i = 1; i < thenStatements.length; i++) {
                ifUml += "  --> " + getSimpleText(thenStatements[i]);
            }
        }

        ifUml += "else\n";

        if (ifStatement.getElseBranch() instanceof PsiExpressionStatement) {
            ifUml += "  --> [no] " + getSimpleText(ifStatement.getElseBranch());
        } else if (ifStatement.getElseBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement elseBlock = (PsiBlockStatement) ifStatement.getElseBranch();
            PsiStatement[] elseStatements = elseBlock.getCodeBlock().getStatements();
            ifUml += "  --> [no] " + getSimpleText(elseStatements[0]);
            for (int i = 1; i < elseStatements.length; i++) {
                ifUml += "  --> " + getSimpleText(elseStatements[i]);
            }
        }

        ifUml += "endif\n\n";

        return ifUml;
    }
}
