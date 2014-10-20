package me.skyun.java2uml;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiBlockStatement;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiIfStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiStatement;

import org.apache.commons.codec.digest.DigestUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by linyun on 14-10-16.
 */
public class MethodUmlParser extends UmlParser {

    private static String STATEMENT = "-d-> \"%s\" as %s\n";
    private static String CODE_FRAG = "-d-> code fragment: (%d-%d)\n";
    private static String REFERENCE = "\"%s\" -r-> \"call %s\" << Begin >>\n";

    private PsiMethod mPsiMethod;
    private String mClassName;

    public MethodUmlParser(String className, PsiMethod psiMethod) {
        super(psiMethod);
        mClassName = className;
        mPsiMethod = psiMethod;
    }

    @Override
    public String parse() {
        String fullMethodName = getFullMethodName(mPsiMethod);

        String blockUml = String.format("=== %s_start === --> === %s_start ===\n", fullMethodName, fullMethodName);
        blockUml += getBlockUml(mPsiMethod.getBody());
        blockUml += getInnerClassUml();
        blockUml = addIndent(blockUml);
        blockUml = formatPartition(fullMethodName, blockUml);

        String referenceUml = getBlockReferenceUml(mPsiMethod.getBody());

        String uml = String.format("\"call %s\" --> %s\n", fullMethodName, fullMethodName)
            + blockUml + referenceUml;
        return uml;
    }

    private String getBlockUml(PsiCodeBlock codeBlock) {
        String uml = "";
        for (PsiStatement statement : codeBlock.getStatements()) {
            String statementText = getStatementText(statement);
            uml += String.format(STATEMENT, statementText, getDigest(statementText));
        }
        return uml;
    }

    private static String getDigest(String s) {
        return DigestUtils.md5Hex(s.getBytes());
    }

    private String getInnerClassUml() {
        String innerClassUml = "";
        List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(mPsiMethod.getBody(), PsiAnonymousClass.class, true);
        for (PsiAnonymousClass anonyClass : anonyClasses) {
            PsiJavaCodeReferenceElement referenceElement =
                PsiUtils.findPsiElement(anonyClass, PsiJavaCodeReferenceElement.class);
            String innerClassName = referenceElement.getText() + "_" + referenceElement.getTextOffset();
            innerClassUml += new ClassUmlParser(anonyClass, innerClassName).parse();
        }
        return innerClassUml;
    }

    private String parseBlock(PsiCodeBlock block) {
        String uml = "";
        String callUml = "";
        String ifUml = "";
        List<PsiStatement> codeFrag = new ArrayList<PsiStatement>();
//        for (PsiStatement statement : mPsiMethod.getBody().getStatements()) {
        PsiStatement[] statements = block.getStatements();
        for (int i = 0; i < statements.length; i++) {
            PsiStatement statement = statements[i];
            if (statement instanceof PsiIfStatement) {
                if (i + 1 < statements.length)
                    uml += getIfUml((PsiIfStatement) statement, getStatementText(statements[i + 1]));
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
                String statementText = getStatementText(statement);
                uml += String.format(STATEMENT, statementText);
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

    private String getStatementReferenceUml(PsiStatement statement) {
        PsiStatement _statement = removeInnerClass(statement);
        List<PsiMethodCallExpression> calls =
            PsiUtils.findPsiElements(_statement, PsiMethodCallExpression.class, true);
        if (calls.isEmpty())
            return "";
        String uml = "";
        for (PsiMethodCallExpression call : calls) {
            PsiReferenceExpression reference = PsiUtils.findPsiElement(call, PsiReferenceExpression.class);
            PsiMethod resovedMethod = (PsiMethod) reference.resolve();
            if (resovedMethod == null)
                continue;
            if (!resovedMethod.getContainingFile().equals(statement.getContainingFile()))
                continue;
            uml += String.format(REFERENCE,
                getDigest(getStatementText(statement)), getFullMethodName(resovedMethod));
        }
        return uml;
    }

    private String getScopePrefix(PsiElement element) {
        PsiClass containingClass = PsiUtils.getContainingParent(element, PsiClass.class);
        PsiMethod continingMethod = PsiUtils.getContainingParent(element, PsiMethod.class);
        String scopePrefix = containingClass.getName() + "_" + continingMethod.getName() + "_";
        return scopePrefix;
    }

    private String getBlockReferenceUml(PsiCodeBlock codeBlock) {
        String uml = "";
        for (PsiStatement statement : codeBlock.getStatements())
            uml += getStatementReferenceUml(statement);
        return uml;
    }

    private static <T extends PsiElement> T removeInnerClass(T element) {
        T _element = (T) element.copy();
        List<PsiAnonymousClass> innerClasses = PsiUtils.findPsiElements(_element, PsiAnonymousClass.class, true);
        for (PsiAnonymousClass innerClass : innerClasses) {
            List<PsiField> fields = PsiUtils.findPsiElements(innerClass, PsiField.class, true);
            List<PsiMethod> methods = PsiUtils.findPsiElements(innerClass, PsiMethod.class, true);
            for (PsiField f : fields)
                f.delete();
            for (PsiMethod m : methods)
                m.delete();
        }
        return _element;
    }

    private static String getStatementText(PsiStatement statement) {
        PsiElement _element = removeInnerClass(statement);
        return statement.getTextOffset() + ":" + _element.getText().replace("\"", "'");
    }

    public String getFullMethodName(PsiMethod method) {
        return mClassName + "__" + method.getName();
    }

    private String getIfUml(PsiIfStatement ifStatement, String nextStatementText) {

        String thenBranchUml = "";
        if (ifStatement.getThenBranch() instanceof PsiExpressionStatement) {
            thenBranchUml += "  --> [yes] " + getStatementText(ifStatement.getThenBranch());
        } else if (ifStatement.getThenBranch() instanceof PsiBlockStatement) {
//            PsiBlockStatement thenBlock = (PsiBlockStatement) ifStatement.getThenBranch();
//            PsiStatement[] thenStatements = thenBlock.getCodeBlock().getStatements();
//            thenBranchUml += "  --> [yes] " + getSimpleText(thenStatements[0]);
//            for (int i = 1; i < thenStatements.length; i++) {
//                thenBranchUml += "  --> " + getSimpleText(thenStatements[i]);
//            }
            thenBranchUml += parseBlock(((PsiBlockStatement) ifStatement.getThenBranch()).getCodeBlock());
        }
        thenBranchUml += "  --> " + nextStatementText;
        thenBranchUml = addIndent(thenBranchUml);
//        thenBranchUml = "if " + getStatementText(ifStatement.getCondition()) + thenBranchUml;

        String elseBranchUml = "";
        if (ifStatement.getElseBranch() instanceof PsiExpressionStatement) {
            elseBranchUml += "  --> [no] " + getStatementText(ifStatement.getElseBranch());
        } else if (ifStatement.getElseBranch() instanceof PsiBlockStatement) {
            elseBranchUml += ((PsiBlockStatement) ifStatement.getElseBranch()).getCodeBlock();
//            PsiBlockStatement elseBlock = (PsiBlockStatement) ifStatement.getElseBranch();
//            PsiStatement[] elseStatements = elseBlock.getCodeBlock().getStatements();
//            elseBranchUml += "  --> [no] " + getSimpleText(elseStatements[0]);
//            for (int i = 1; i < elseStatements.length; i++) {
//                elseBranchUml += "  --> " + getSimpleText(elseStatements[i]);
//            }
        }
        elseBranchUml += "  --> " + nextStatementText;
        elseBranchUml = addIndent(elseBranchUml);
        elseBranchUml = "else\n" + elseBranchUml;

        String ifUml = thenBranchUml + elseBranchUml + "endif\n\n";

        return ifUml;
    }
}
