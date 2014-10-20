package me.skyun.java2uml;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiBlockStatement;
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
import org.apache.http.util.TextUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by linyun on 14-10-16.
 */
public class MethodUmlParser extends UmlParser {

    private static String STATEMENT = "--> %s \"%s\" as %s\n";
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
        String fullMethodName = getFullName(mPsiMethod);

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
            if (statement instanceof PsiIfStatement)
                uml += getIfUml((PsiIfStatement) statement);
            else {
                uml += getStatementUml(statement, null);
            }
        }
        return uml;
    }

    private String getStatementUml(PsiStatement statement, String tag) {
        String _tag = "";
        if (!TextUtils.isEmpty(tag))
            _tag = "[" + tag + "]";
        String statementText = getStatementText(statement);
        return String.format(STATEMENT, _tag, statementText, getDigest(statementText));
    }

    private static String getDigest(String s) {
        return DigestUtils.md5Hex(s.getBytes());
    }

    private String getInnerClassUml() {
        String innerClassUml = "";
        String refInnerClassUml = "";
        List<PsiAnonymousClass> anonyClasses =
            PsiUtils.findPsiElements(mPsiMethod.getBody(), PsiAnonymousClass.class, true, true);
        for (PsiAnonymousClass anonyClass : anonyClasses) {
            PsiJavaCodeReferenceElement referenceElement =
                PsiUtils.findPsiElement(anonyClass, PsiJavaCodeReferenceElement.class);
            String innerClassName = referenceElement.getText() + "_" + referenceElement.getTextOffset();
            innerClassUml += new ClassUmlParser(anonyClass, innerClassName).parse();

            PsiStatement statement = PsiUtils.getContainingParent(anonyClass, PsiStatement.class, null);
            refInnerClassUml += String.format(REFERENCE, getDigest(getStatementText(statement)), innerClassName);
        }
        return innerClassUml + refInnerClassUml;
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
//                if (i + 1 < statements.length)
//                    uml += getIfUml((PsiIfStatement) statement, getStatementText(statements[i + 1]));
//                else
//                    uml += getIfUml((PsiIfStatement) statement, String.format("=== %s_end === ", getFullName(mPsiMethod)));
//                continue;
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
                        getFullName(resolvedMthod));  // to node
                }

                // handle anonymous class in call
                List<PsiAnonymousClass> anonyClasses = PsiUtils.findPsiElements(statement, PsiAnonymousClass.class, true);
                for (PsiAnonymousClass anonyClass : anonyClasses) {
                    String anonyClassName = PsiUtils.findPsiElement(anonyClass, PsiJavaCodeReferenceElement.class).getText();
                    callUml += String.format(REFERENCE,
                        statementText,                                          // from node
                        anonyClassName,                                         // -> [arrow tag]
                        getFullName(mPsiMethod) + "__" + anonyClassName); // to node
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

    private String getIfReferenceUml(PsiIfStatement ifStatement) {
        return getIfBranchReferenceUml(ifStatement.getThenBranch())
            + getIfBranchReferenceUml(ifStatement.getElseBranch());
    }

    private String getIfBranchReferenceUml(PsiStatement ifBranch) {
        if (ifBranch == null)
            return "";
        if (ifBranch instanceof PsiBlockStatement)
            return getBlockReferenceUml(((PsiBlockStatement) ifBranch).getCodeBlock());
        else if (ifBranch instanceof PsiIfStatement)
            return getIfReferenceUml((PsiIfStatement) ifBranch);
        else
            return getStatementReferenceUml(ifBranch);
    }

    private String getStatementReferenceUml(PsiStatement statement) {
        if (statement instanceof PsiIfStatement) {
            return getIfReferenceUml((PsiIfStatement) statement);
        }

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

    private static String getStatementText(PsiElement element) {
        PsiElement _element = removeInnerClass(element);
        return element.getTextOffset() + ":" + _element.getText().replace("\"", "'");
    }

    public static String getFullMethodName(PsiMethod method) {
        return method.getContainingClass().getName() + "__" + method.getName();
    }

    public String getFullName(PsiMethod method) {
        return mClassName + "__" + method.getName();
    }

    private String getIfUml(PsiIfStatement ifStatement) {
        String endStatementText = "--> " + "end if: " + ifStatement.getTextOffset();

        String thenBranchUml = getIfBrancheUml(ifStatement.getThenBranch());
        thenBranchUml += endStatementText;
        thenBranchUml = addIndent(thenBranchUml);
        String condition = "if " + getStatementText(ifStatement.getCondition());
        condition = Utils.multiLineJoin(condition, "\\n ") + "\n";
        thenBranchUml = condition + thenBranchUml;

        String elseBranchUml = getIfBrancheUml(ifStatement.getElseBranch());
        elseBranchUml += endStatementText;
        elseBranchUml = addIndent(elseBranchUml);
        elseBranchUml = "else\n" + elseBranchUml;

        String ifUml = thenBranchUml + elseBranchUml + "endif\n\n";
        return ifUml;
    }

    private String getIfBrancheUml(PsiStatement ifBranch) {
        if (ifBranch == null)
            return "";
        String uml = "";
        if (ifBranch instanceof PsiExpressionStatement) {
            uml = getStatementUml(ifBranch, "yes");
        } else if (ifBranch instanceof PsiBlockStatement) {
            PsiBlockStatement thenBlock = (PsiBlockStatement) ifBranch;
            uml = getBlockUml(thenBlock.getCodeBlock());
        }
        return uml;
    }
}
