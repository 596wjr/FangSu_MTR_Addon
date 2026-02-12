package com.fangsu.scripting;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Condition {

    private final List<Node> ast;

    private Condition(List<Node> ast) {
        this.ast = ast;
    }

    /* =========================
       对外入口
       ========================= */

    public static Condition compile(String conditionStr) {
        if (conditionStr == null || conditionStr.trim().isEmpty()) {
            return new Condition(Collections.emptyList());
        }

        try {
            List<Node> ast = parseExpression(conditionStr);
            return new Condition(ast);
        } catch (Exception e) {
            return new Condition(Collections.emptyList());
        }
    }

    public boolean test(Map<String, Object> context) {
        if (context == null || ast.isEmpty()) {
            return false;
        }
        return evaluateAST(ast, context);
    }

    /* =========================
       AST 节点定义
       ========================= */

    private interface Node {
    }

    private static class LogicNode implements Node {
        final String op;

        LogicNode(String op) {
            this.op = op;
        }
    }

    private static class GroupNode implements Node {
        final List<Node> children;

        GroupNode(List<Node> children) {
            this.children = children;
        }
    }

    private static class ComparisonNode implements Node {
        final String variable;
        final String operator;
        final Object value;
        final boolean negated;

        ComparisonNode(String variable, String operator, Object value, boolean negated) {
            this.variable = variable;
            this.operator = operator;
            this.value = value;
            this.negated = negated;
        }
    }

    private static class VariableNode implements Node {
        final String variable;
        final boolean negated;

        VariableNode(String variable, boolean negated) {
            this.variable = variable;
            this.negated = negated;
        }
    }

    private static class BooleanNode implements Node {
        final boolean value;
        final boolean negated;

        BooleanNode(boolean value, boolean negated) {
            this.value = value;
            this.negated = negated;
        }
    }

    private static class InvalidNode implements Node {
        final boolean negated;

        InvalidNode(boolean negated) {
            this.negated = negated;
        }
    }

    /* =========================
       解析逻辑
       ========================= */

    private static List<Node> parseExpression(String str) {
        str = str.trim();
        List<Node> result = new ArrayList<>();

        if (!str.contains("(")) {
            result.addAll(parseSimpleExpression(str));
            return result;
        }

        StringBuilder current = new StringBuilder();
        int depth = 0;

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c == '(') {
                if (depth == 0 && current.length() > 0) {
                    result.addAll(parseSimpleExpression(current.toString()));
                    current.setLength(0);
                } else if (depth > 0) {
                    current.append(c);
                }
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    result.add(new GroupNode(parseExpression(current.toString())));
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            result.addAll(parseSimpleExpression(current.toString()));
        }

        return result;
    }

    private static List<Node> parseSimpleExpression(String str) {
        List<Node> nodes = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);

            if (c == '&' && i + 1 < str.length() && str.charAt(i + 1) == '&') {
                flushComparison(nodes, current);
                nodes.add(new LogicNode("&&"));
                i++;
            } else if (c == '|' && i + 1 < str.length() && str.charAt(i + 1) == '|') {
                flushComparison(nodes, current);
                nodes.add(new LogicNode("||"));
                i++;
            } else {
                current.append(c);
            }
        }

        flushComparison(nodes, current);
        return nodes;
    }

    private static void flushComparison(List<Node> nodes, StringBuilder current) {
        String s = current.toString().trim();
        if (!s.isEmpty()) {
            nodes.add(parseComparison(s));
        }
        current.setLength(0);
    }

    private static Node parseComparison(String str) {
        boolean negated = false;
        if (str.startsWith("!")) {
            negated = true;
            str = str.substring(1).trim();
        }

        Pattern cmp = Pattern.compile(
                "^([a-zA-Z_$][a-zA-Z0-9_$]*)\\s*(>=|<=|>|<|===|==|!==|!=)\\s*(.+)$"
        );
        Matcher m = cmp.matcher(str);

        if (m.matches()) {
            String var = m.group(1);
            String op = m.group(2);
            String valueStr = m.group(3).trim();

            Object value = parseValue(valueStr);
            return new ComparisonNode(var, op, value, negated);
        }

        if (str.matches("^[a-zA-Z_$][a-zA-Z0-9_$]*$")) {
            return new VariableNode(str, negated);
        }

        if (str.equalsIgnoreCase("TRUE") || str.equalsIgnoreCase("FALSE")) {
            return new BooleanNode(Boolean.parseBoolean(str.toLowerCase()), negated);
        }

        return new InvalidNode(negated);
    }

    private static Object parseValue(String s) {
        String u = s.toUpperCase();
        if (u.equals("TRUE")) return true;
        if (u.equals("FALSE")) return false;
        if (u.equals("NULL") || u.equals("UNDEFINED")) return null;

        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ignored) {
        }

        if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }

        return s;
    }

    /* =========================
       执行逻辑
       ========================= */

    private static boolean evaluateAST(List<Node> ast, Map<String, Object> ctx) {
        Boolean result = null;
        String logic = "&&";

        for (Node node : ast) {
            if (node instanceof LogicNode ln) {
                logic = ln.op;
                continue;
            }

            boolean value = false;

            if (node instanceof GroupNode g) {
                value = evaluateAST(g.children, ctx);
            } else if (node instanceof ComparisonNode c) {
                Object left = ctx.get(c.variable);
                if (left != null) {
                    value = safeCompare(left, c.operator, c.value);
                }
                if (c.negated) value = !value;
            } else if (node instanceof VariableNode v) {
                value = Boolean.TRUE.equals(ctx.get(v.variable));
                if (v.negated) value = !value;
            } else if (node instanceof BooleanNode b) {
                value = b.value;
                if (b.negated) value = !value;
            }

            if (result == null) {
                result = value;
            } else {
                result = logic.equals("&&") ? result && value : result || value;
            }
        }

        return result != null && result;
    }

    private static boolean safeCompare(Object l, String op, Object r) {
        try {
            if (l instanceof Number && r instanceof Number) {
                double a = ((Number) l).doubleValue();
                double b = ((Number) r).doubleValue();
                return switch (op) {
                    case ">" -> a > b;
                    case "<" -> a < b;
                    case ">=" -> a >= b;
                    case "<=" -> a <= b;
                    case "==" -> a == b;
                    case "!=" -> a != b;
                    default -> false;
                };
            }

            return switch (op) {
                case "==" -> Objects.equals(l, r);
                case "!=" -> !Objects.equals(l, r);
                case "===" -> l == r;
                case "!==" -> l != r;
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }
}
