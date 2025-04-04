/*******************************************************************************
 * Copyright (c) 2020, 2025 Fabrice TIERCELIN and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Fabrice TIERCELIN - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.fix;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.IfStatement;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Pattern;
import org.eclipse.jdt.core.dom.PatternInstanceofExpression;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.TypePattern;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFix;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.fix.LinkedProposalModelCore;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;

import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

/**
 * A fix that merge conditions of if/else if/else that have the same blocks.
 */
public class MergeConditionalBlocksCleanUp extends AbstractMultiFix {
	public MergeConditionalBlocksCleanUp() {
		this(Collections.emptyMap());
	}

	public MergeConditionalBlocksCleanUp(Map<String, String> options) {
		super(options);
	}

	@Override
	public CleanUpRequirements getRequirements() {
		boolean requireAST= isEnabled(CleanUpConstants.MERGE_CONDITIONAL_BLOCKS);
		return new CleanUpRequirements(requireAST, false, false, null);
	}

	@Override
	public String[] getStepDescriptions() {
		if (isEnabled(CleanUpConstants.MERGE_CONDITIONAL_BLOCKS)) {
			return new String[] { MultiFixMessages.MergeConditionalBlocksCleanup_description };
		}

		return new String[0];
	}

	@Override
	public String getPreview() {
		if (isEnabled(CleanUpConstants.MERGE_CONDITIONAL_BLOCKS)) {
			return """
				if (isValid || (i != 1)) {
				    System.out.println("Duplicate");
				} else {
				    System.out.println("Different");
				}


				"""; //$NON-NLS-1$
		}

		return """
			if (isValid) {
			    System.out.println("Duplicate");
			} else if (i == 1) {
			    System.out.println("Different");
			} else {
			    System.out.println("Duplicate");
			}
			"""; //$NON-NLS-1$
	}

	private final class IfVisitor extends ASTVisitor {
		final List<CompilationUnitRewriteOperationWithSourceRange> fRewriteOperations;

		public IfVisitor(List<CompilationUnitRewriteOperationWithSourceRange> rewriteOperations) {
			fRewriteOperations= rewriteOperations;
		}

		@Override
		public boolean visit(final IfStatement visited) {
			if (visited.getElseStatement() != null) {
				IfStatement innerIf= ASTNodes.as(visited.getThenStatement(), IfStatement.class);

				if (innerIf != null
						&& innerIf.getElseStatement() != null
						&& !ASTNodes.asList(visited.getElseStatement()).isEmpty()
						&& ASTNodes.getNbOperands(visited.getExpression()) + ASTNodes.getNbOperands(innerIf.getExpression()) < ASTNodes.EXCESSIVE_OPERAND_NUMBER) {
					if (ASTNodes.match(visited.getElseStatement(), innerIf.getElseStatement())) {
						fRewriteOperations.add(new InnerIfOperation(visited, innerIf, true));
						return false;
					}

					if (ASTNodes.match(visited.getElseStatement(), innerIf.getThenStatement())) {
						fRewriteOperations.add(new InnerIfOperation(visited, innerIf, false));
						return false;
					}
				}

				List<IfStatement> duplicateIfBlocks= new ArrayList<>(4);
				Set<String> patternNames= new HashSet<>();
				PatternNameVisitor visitor= new PatternNameVisitor();
				visited.accept(visitor);
				patternNames= visitor.getPatternNames();
				List<Boolean> isThenStatement= new ArrayList<>(4);
				AtomicInteger operandCount= new AtomicInteger(ASTNodes.getNbOperands(visited.getExpression()));
				duplicateIfBlocks.add(visited);
				isThenStatement.add(Boolean.TRUE);

				while (addOneMoreIf(duplicateIfBlocks, isThenStatement, operandCount, patternNames)){
					// OK continue
				}

				if (duplicateIfBlocks.size() > 1) {
					fRewriteOperations.add(new MergeConditionalBlocksOperation(duplicateIfBlocks, isThenStatement));
					return false;
				}
			}

			return true;
		}

		private class PatternNameVisitor extends ASTVisitor {
			private Set<String> patternNames= new HashSet<>();

			@Override
			public boolean visit(PatternInstanceofExpression node) {
				Pattern p= node.getPattern();
				if (p instanceof TypePattern typePattern) {
					final VariableDeclaration patternVariable= node.getAST().apiLevel() < AST.JLS22 ? typePattern.getPatternVariable() : typePattern.getPatternVariable2();
					patternNames.add(patternVariable.getName().getFullyQualifiedName());
				}
				return true;
			}

			public Set<String> getPatternNames() {
				return patternNames;
			}
		}

		private boolean addOneMoreIf(final List<IfStatement> duplicateIfBlocks, final List<Boolean> isThenStatement, final AtomicInteger operandCount, Set<String> patternNames) {
			IfStatement lastBlock= getLast(duplicateIfBlocks);
			Statement previousStatement= getLast(isThenStatement) ? lastBlock.getThenStatement() : lastBlock.getElseStatement();
			Statement nextStatement= getLast(isThenStatement) ? lastBlock.getElseStatement() : lastBlock.getThenStatement();

			if (nextStatement != null) {
				IfStatement nextElse= ASTNodes.as(nextStatement, IfStatement.class);

				if (nextElse != null
						&& operandCount.get() + ASTNodes.getNbOperands(nextElse.getExpression()) < ASTNodes.EXCESSIVE_OPERAND_NUMBER) {
					PatternNameVisitor visitor= new PatternNameVisitor();
					nextElse.getExpression().accept(visitor);
					Set<String> siblingPatternNames= visitor.getPatternNames();
					for (String siblingPatternName : siblingPatternNames) {
						if (!patternNames.add(siblingPatternName)) {
							return false;
						}
					}
					if (ASTNodes.match(previousStatement, nextElse.getThenStatement())) {
						operandCount.addAndGet(ASTNodes.getNbOperands(nextElse.getExpression()));
						duplicateIfBlocks.add(nextElse);
						isThenStatement.add(Boolean.TRUE);
						return true;
					}

					if (nextElse.getElseStatement() != null
							&& ASTNodes.match(previousStatement, nextElse.getElseStatement())) {
						operandCount.addAndGet(ASTNodes.getNbOperands(nextElse.getExpression()));
						duplicateIfBlocks.add(nextElse);
						isThenStatement.add(Boolean.FALSE);
						return true;
					}
				}
			}

			return false;
		}
	}

	@Override
	protected ICleanUpFix createFix(CompilationUnit unit) throws CoreException {
		if (!isEnabled(CleanUpConstants.MERGE_CONDITIONAL_BLOCKS)) {
			return null;
		}

		final List<CompilationUnitRewriteOperationWithSourceRange> rewriteOperations= new ArrayList<>();

		IfVisitor visitor= new IfVisitor(rewriteOperations);
		unit.accept(visitor);

		if (rewriteOperations.isEmpty()) {
			return null;
		}

		return new CompilationUnitRewriteOperationsFix(MultiFixMessages.MergeConditionalBlocksCleanup_description, unit,
				rewriteOperations.toArray(new CompilationUnitRewriteOperationWithSourceRange[0]));
	}

	@Override
	public boolean canFix(final ICompilationUnit compilationUnit, final IProblemLocation problem) {
		return false;
	}

	@Override
	protected ICleanUpFix createFix(final CompilationUnit unit, final IProblemLocation[] problems) throws CoreException {
		return null;
	}

	private static class InnerIfOperation extends CompilationUnitRewriteOperationWithSourceRange {
		private final IfStatement visited;
		private final IfStatement innerIf;
		private final boolean isInnerMainFirst;

		public InnerIfOperation(final IfStatement visited, final IfStatement innerIf,
				final boolean isInnerMainFirst) {
			this.visited= visited;
			this.innerIf= innerIf;
			this.isInnerMainFirst= isInnerMainFirst;
		}

		@Override
		public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getRoot().getAST();
			TextEditGroup group= createTextEditGroup(MultiFixMessages.MergeConditionalBlocksCleanup_description_inner_if, cuRewrite);

			InfixExpression newInfixExpression= ast.newInfixExpression();

			Expression outerCondition;
			if (isInnerMainFirst) {
				outerCondition= ASTNodes.createMoveTarget(rewrite, visited.getExpression());
			} else {
				outerCondition= ASTNodeFactory.negate(ast, rewrite, visited.getExpression(), true);
			}

			newInfixExpression.setLeftOperand(ASTNodeFactory.parenthesizeIfNeeded(ast, outerCondition));
			newInfixExpression.setOperator(isInnerMainFirst ? InfixExpression.Operator.CONDITIONAL_AND
					: InfixExpression.Operator.CONDITIONAL_OR);
			newInfixExpression.setRightOperand(ASTNodeFactory.parenthesizeIfNeeded(ast,
					ASTNodes.createMoveTarget(rewrite, innerIf.getExpression())));

			ASTNodes.replaceButKeepComment(rewrite, innerIf.getExpression(), newInfixExpression, group);
			ASTNodes.replaceButKeepComment(rewrite, visited, ASTNodes.createMoveTarget(rewrite, innerIf), group);
		}
	}

	private static class MergeConditionalBlocksOperation extends CompilationUnitRewriteOperationWithSourceRange {
		private final List<IfStatement> duplicateIfBlocks;
		private final List<Boolean> isThenStatement;

		public MergeConditionalBlocksOperation(final List<IfStatement> duplicateIfBlocks, final List<Boolean> isThenStatement) {
			this.duplicateIfBlocks= duplicateIfBlocks;
			this.isThenStatement= isThenStatement;
		}

		@Override
		public void rewriteASTInternal(final CompilationUnitRewrite cuRewrite, final LinkedProposalModelCore linkedModel) throws CoreException {
			ASTRewrite rewrite= cuRewrite.getASTRewrite();
			AST ast= cuRewrite.getRoot().getAST();
			TextEditGroup group= createTextEditGroup(MultiFixMessages.MergeConditionalBlocksCleanup_description_if_suite, cuRewrite);

			List<Expression> newConditions= new ArrayList<>(duplicateIfBlocks.size());

			for (int i= 0; i < duplicateIfBlocks.size(); i++) {
				if (isThenStatement.get(i)) {
					newConditions.add(ASTNodeFactory.parenthesizeIfNeeded(ast, ASTNodes.createMoveTarget(rewrite, duplicateIfBlocks.get(i).getExpression())));
				} else {
					newConditions.add(ASTNodeFactory.parenthesizeIfNeeded(ast, ASTNodeFactory.negate(ast, rewrite, duplicateIfBlocks.get(i).getExpression(), true)));
				}
			}

			IfStatement lastBlock= getLast(duplicateIfBlocks);
			Statement remainingStatement= getLast(isThenStatement) ? lastBlock.getElseStatement() : lastBlock.getThenStatement();
			InfixExpression newCondition= ast.newInfixExpression();
			newCondition.setOperator(InfixExpression.Operator.CONDITIONAL_OR);
			newCondition.setLeftOperand(newConditions.remove(0));
			newCondition.setRightOperand(newConditions.remove(0));
			newCondition.extendedOperands().addAll(newConditions);
			ASTNode node= duplicateIfBlocks.get(0).getExpression();

			ASTNodes.replaceButKeepComment(rewrite, node, newCondition, group);

			if (remainingStatement != null) {
				ASTNode node1= duplicateIfBlocks.get(0).getElseStatement();
				ASTNode replacement= ASTNodes.createMoveTarget(rewrite, remainingStatement);
				ASTNodes.replaceButKeepComment(rewrite, node1, replacement, group);
			} else if (duplicateIfBlocks.get(0).getElseStatement() != null) {
				rewrite.remove(duplicateIfBlocks.get(0).getElseStatement(), group);
			}
		}
	}

	private static <E> E getLast(final List<E> list) {
		return list.get(list.size() - 1);
	}
}
