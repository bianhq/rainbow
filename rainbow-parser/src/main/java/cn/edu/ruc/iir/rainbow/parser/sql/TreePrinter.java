/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.edu.ruc.iir.rainbow.parser.sql;

import cn.edu.ruc.iir.rainbow.parser.sql.tree.AliasedRelation;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.AllColumns;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.ArithmeticBinaryExpression;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.AstVisitor;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.BinaryLiteral;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.BooleanLiteral;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.ComparisonExpression;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Cube;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.DefaultTraversalVisitor;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.DereferenceExpression;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Expression;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.FunctionCall;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.GroupingElement;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.GroupingSets;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Identifier;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.InPredicate;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.LikePredicate;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.LogicalBinaryExpression;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.LongLiteral;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Node;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.OrderBy;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.QualifiedName;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Query;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.QuerySpecification;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Rollup;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Row;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.SampledRelation;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Select;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.SimpleGroupBy;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.SingleColumn;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.SortItem;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.StringLiteral;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.SubqueryExpression;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Table;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.TableSubquery;
import cn.edu.ruc.iir.rainbow.parser.sql.tree.Values;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

import java.io.PrintStream;
import java.util.IdentityHashMap;
import java.util.Set;

public class TreePrinter
{
    private static final String INDENT = "   ";

    private final IdentityHashMap<Expression, QualifiedName> resolvedNameReferences;
    private final PrintStream out;

    public TreePrinter(IdentityHashMap<Expression, QualifiedName> resolvedNameReferences, PrintStream out)
    {
        this.resolvedNameReferences = new IdentityHashMap<>(resolvedNameReferences);
        this.out = out;
    }

    public void print(Node root)
    {
        AstVisitor<Void, Integer> printer = new DefaultTraversalVisitor<Void, Integer>()
        {
            @Override
            protected Void visitNode(Node node, Integer indentLevel)
            {
                throw new UnsupportedOperationException("not yet implemented: " + node);
            }

            @Override
            protected Void visitQuery(Query node, Integer indentLevel)
            {
                print(indentLevel, "Query ");

                indentLevel++;

                print(indentLevel, "QueryBody");
                process(node.getQueryBody(), indentLevel);
                if (node.getOrderBy().isPresent()) {
                    print(indentLevel, "OrderBy");
                    process(node.getOrderBy().get(), indentLevel + 1);
                }

                if (node.getLimit().isPresent()) {
                    print(indentLevel, "Limit: " + node.getLimit().get());
                }

                return null;
            }

            @Override
            protected Void visitQuerySpecification(QuerySpecification node, Integer indentLevel)
            {
                print(indentLevel, "QuerySpecification ");

                indentLevel++;

                process(node.getSelect(), indentLevel);

                if (node.getFrom().isPresent()) {
                    print(indentLevel, "From");
                    process(node.getFrom().get(), indentLevel + 1);
                }

                if (node.getWhere().isPresent()) {
                    print(indentLevel, "Where");
                    process(node.getWhere().get(), indentLevel + 1);
                }

                if (node.getGroupBy().isPresent()) {
                    String distinct = "";
                    if (node.getGroupBy().get().isDistinct()) {
                        distinct = "[DISTINCT]";
                    }
                    print(indentLevel, "GroupBy" + distinct);
                    for (GroupingElement groupingElement : node.getGroupBy().get().getGroupingElements()) {
                        print(indentLevel, "SimpleGroupBy");
                        if (groupingElement instanceof SimpleGroupBy) {
                            for (Expression column : ((SimpleGroupBy) groupingElement).getColumnExpressions()) {
                                process(column, indentLevel + 1);
                            }
                        }
                        else if (groupingElement instanceof GroupingSets) {
                            print(indentLevel + 1, "GroupingSets");
                            for (Set<Expression> column : groupingElement.enumerateGroupingSets()) {
                                print(indentLevel + 2, "GroupingSet[");
                                for (Expression expression : column) {
                                    process(expression, indentLevel + 3);
                                }
                                print(indentLevel + 2, "]");
                            }
                        }
                        else if (groupingElement instanceof Cube) {
                            print(indentLevel + 1, "Cube");
                            for (QualifiedName column : ((Cube) groupingElement).getColumns()) {
                                print(indentLevel + 1, column.toString());
                            }
                        }
                        else if (groupingElement instanceof Rollup) {
                            print(indentLevel + 1, "Rollup");
                            for (QualifiedName column : ((Rollup) groupingElement).getColumns()) {
                                print(indentLevel + 1, column.toString());
                            }
                        }
                    }
                }

                if (node.getHaving().isPresent()) {
                    print(indentLevel, "Having");
                    process(node.getHaving().get(), indentLevel + 1);
                }

                if (node.getOrderBy().isPresent()) {
                    print(indentLevel, "OrderBy");
                    process(node.getOrderBy().get(), indentLevel + 1);
                }

                if (node.getLimit().isPresent()) {
                    print(indentLevel, "Limit: " + node.getLimit().get());
                }

                return null;
            }

            protected Void visitOrderBy(OrderBy node, Integer indentLevel)
            {
                for (SortItem sortItem : node.getSortItems()) {
                    process(sortItem, indentLevel);
                }

                return null;
            }

            @Override
            protected Void visitSelect(Select node, Integer indentLevel)
            {
                String distinct = "";
                if (node.isDistinct()) {
                    distinct = "[DISTINCT]";
                }
                print(indentLevel, "Select" + distinct);

                super.visitSelect(node, indentLevel + 1); // visit children

                return null;
            }

            @Override
            protected Void visitAllColumns(AllColumns node, Integer indent)
            {
                if (node.getPrefix().isPresent()) {
                    print(indent, node.getPrefix() + ".*");
                }
                else {
                    print(indent, "*");
                }

                return null;
            }

            @Override
            protected Void visitSingleColumn(SingleColumn node, Integer indent)
            {
                if (node.getAlias().isPresent()) {
                    print(indent, "Alias: " + node.getAlias().get());
                }

                super.visitSingleColumn(node, indent + 1); // visit children

                return null;
            }

            @Override
            protected Void visitComparisonExpression(ComparisonExpression node, Integer indentLevel)
            {
                print(indentLevel, node.getType().toString());

                super.visitComparisonExpression(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitArithmeticBinary(ArithmeticBinaryExpression node, Integer indentLevel)
            {
                print(indentLevel, node.getType().toString());

                super.visitArithmeticBinary(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitLogicalBinaryExpression(LogicalBinaryExpression node, Integer indentLevel)
            {
                print(indentLevel, node.getType().toString());

                super.visitLogicalBinaryExpression(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitStringLiteral(StringLiteral node, Integer indentLevel)
            {
                print(indentLevel, "String[" + node.getValue() + "]");
                return null;
            }

            @Override
            protected Void visitBinaryLiteral(BinaryLiteral node, Integer indentLevel)
            {
                print(indentLevel, "Binary[" + node.toHexString() + "]");
                return null;
            }

            @Override
            protected Void visitBooleanLiteral(BooleanLiteral node, Integer indentLevel)
            {
                print(indentLevel, "Boolean[" + node.getValue() + "]");
                return null;
            }

            @Override
            protected Void visitLongLiteral(LongLiteral node, Integer indentLevel)
            {
                print(indentLevel, "Long[" + node.getValue() + "]");
                return null;
            }

            @Override
            protected Void visitLikePredicate(LikePredicate node, Integer indentLevel)
            {
                print(indentLevel, "LIKE");

                super.visitLikePredicate(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitIdentifier(Identifier node, Integer indentLevel)
            {
                QualifiedName resolved = resolvedNameReferences.get(node);
                String resolvedName = "";
                if (resolved != null) {
                    resolvedName = "=>" + resolved.toString();
                }
                print(indentLevel, "Identifier[" + node.getValue() + resolvedName + "]");
                return null;
            }

            @Override
            protected Void visitDereferenceExpression(DereferenceExpression node, Integer indentLevel)
            {
                QualifiedName resolved = resolvedNameReferences.get(node);
                String resolvedName = "";
                if (resolved != null) {
                    resolvedName = "=>" + resolved.toString();
                }
                print(indentLevel, "DereferenceExpression[" + node + resolvedName + "]");
                return null;
            }

            @Override
            protected Void visitFunctionCall(FunctionCall node, Integer indentLevel)
            {
                String name = Joiner.on('.').join(node.getName().getParts());
                print(indentLevel, "FunctionCall[" + name + "]");

                super.visitFunctionCall(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitTable(Table node, Integer indentLevel)
            {
                String name = Joiner.on('.').join(node.getName().getParts());
                print(indentLevel, "Table[" + name + "]");

                return null;
            }

            @Override
            protected Void visitValues(Values node, Integer indentLevel)
            {
                print(indentLevel, "Values");

                super.visitValues(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitRow(Row node, Integer indentLevel)
            {
                print(indentLevel, "Row");

                super.visitRow(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitAliasedRelation(AliasedRelation node, Integer indentLevel)
            {
                print(indentLevel, "Alias[" + node.getAlias() + "]");

                super.visitAliasedRelation(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitSampledRelation(SampledRelation node, Integer indentLevel)
            {
                print(indentLevel, "TABLESAMPLE[" + node.getType() + " (" + node.getSamplePercentage() + ")]");

                super.visitSampledRelation(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitTableSubquery(TableSubquery node, Integer indentLevel)
            {
                print(indentLevel, "SubQuery");

                super.visitTableSubquery(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitInPredicate(InPredicate node, Integer indentLevel)
            {
                print(indentLevel, "IN");

                super.visitInPredicate(node, indentLevel + 1);

                return null;
            }

            @Override
            protected Void visitSubqueryExpression(SubqueryExpression node, Integer indentLevel)
            {
                print(indentLevel, "SubQuery");

                super.visitSubqueryExpression(node, indentLevel + 1);

                return null;
            }
        };

        printer.process(root, 0);
    }

    private void print(Integer indentLevel, String value)
    {
        out.println(Strings.repeat(INDENT, indentLevel) + value);
    }
}
