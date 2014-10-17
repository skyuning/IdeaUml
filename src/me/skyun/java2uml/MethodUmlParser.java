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
        String ifUml = "";
        List<PsiStatement> codeFrag = new ArrayList<PsiStatement>();
//        for (PsiStatement statement : mPsiMethod.getBody().getStatements()) {
        PsiStatement[] statements = mPsiMethod.getBody().getStatements();
        for (int i = 0; i < statements.length; i++) {
            PsiStatement statement = statements[i];
            if (statement instanceof PsiIfStatement) {
                if (i + 1 < statements.length)
                    uml += getIfUml((PsiIfStatement) statement, getSimpleText(statements[i + 1]));
                else
                    uml += getIfUml((PsiIfStatement) statement, String.format("=== %s_end === ", getFullMethodName(mPsiMethod)));
                continue;
            }

            // get resolved methods in statement
            List<PsiMethod> resolvedMethods = new ArrayList<PsiMethod>();
            List<PsiMethodCallExpression> statementCalls =
                PsiUtils.findPsiElements(statement, PsiMethodCallExpression.class, true);
            for (PsiMethodCallExpression call : statementCalls) {
                PsiReferenceExpression callRef = PsiUtils.findPsiElement(call, PsiReferenceExpression.class);
                PsiMethod resolvedMethod = (PsiMethod) callRef.resolve();
                if (resolvedMethod != null)
                    resolvedMethods.add(resolvedMethod);
            }

            // no method call in statement
            if (resolvedMethods.isEmpty()) {
                codeFrag.add(statement);
                continue;
            }
            // has method call in statement
            else {
                // flush pre code fragment
                uml += getCodeFragmentUml(codeFrag);

                // parse the method call
                String statementText = getSimpleText(statement, false);
                uml += String.format(CONTINUE_STATEMENT, statementText);
                for (PsiMethod resolvedMthod : resolvedMethods) {
                    callUml += String.format(REFERENCE,
                        statementText,                      // from node
                        resolvedMthod.getName(),            // -> [arrow tag]
                        getFullMethodName(resolvedMthod));  // to node
                }

                // handle anonymous class in call
                List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(statement, PsiAnonymousClass.class, true);
                for (PsiAnonymousClass anonyClass : anonyClasses) {
                    String anonyClassName = PsiUtils.findPsiElement(anonyClass, PsiJavaCodeReferenceElement.class).getText();
                    callUml += String.format(REFERENCE,
                        statementText,                                          // from node
                        anonyClassName,                                         // -> [arrow tag]
                        getFullMethodName(mPsiMethod) + "__" + anonyClassName); // to node
                }

            }
        }
        uml += getCodeFragmentUml(codeFrag);
        return uml + "\n" + callUml;
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
        String simpleText = _element.getText().replace("\n", "\\n").replace("\"", "\\\"");
        if (endline)
            simpleText += "\n";
        return simpleText;
    }

    public static String getFullMethodName(PsiMethod method) {
        return method.getContainingClass().getName() + "__" + method.getName();
    }

    private static String getIfUml(PsiIfStatement ifStatement, String nextStatementText) {

        String thenBranchUml = "";
        if (ifStatement.getThenBranch() instanceof PsiExpressionStatement) {
            thenBranchUml += "  --> [yes] " + getSimpleText(ifStatement.getThenBranch());
        } else if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement thenBlock = (PsiBlockStatement) ifStatement.getThenBranch();
            PsiStatement[] thenStatements = thenBlock.getCodeBlock().getStatements();
            thenBranchUml += "  --> [yes] " + getSimpleText(thenStatements[0]);
            for (int i = 1; i < thenStatements.length; i++) {
                thenBranchUml += "  --> " + getSimpleText(thenStatements[i]);
            }
        }
        thenBranchUml += "  --> " + nextStatementText;
        thenBranchUml = addIndent(thenBranchUml);
        thenBranchUml = "if " + getSimpleText(ifStatement.getCondition()) + thenBranchUml;

        String elseBranchUml = "";
        if (ifStatement.getElseBranch() instanceof PsiExpressionStatement) {
            elseBranchUml += "  --> [no] " + getSimpleText(ifStatement.getElseBranch());
        } else if (ifStatement.getElseBranch() instanceof PsiBlockStatement) {
            PsiBlockStatement elseBlock = (PsiBlockStatement) ifStatement.getElseBranch();
            PsiStatement[] elseStatements = elseBlock.getCodeBlock().getStatements();
            elseBranchUml += "  --> [no] " + getSimpleText(elseStatements[0]);
            for (int i = 1; i < elseStatements.length; i++) {
                elseBranchUml += "  --> " + getSimpleText(elseStatements[i]);
            }
        }
        elseBranchUml += "  --> " + nextStatementText;
        elseBranchUml = addIndent(elseBranchUml);
        elseBranchUml = "else\n" + elseBranchUml;

        String ifUml = thenBranchUml + elseBranchUml + "endif\n\n";

        return ifUml;
    }
}
