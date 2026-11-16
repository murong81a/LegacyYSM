package com.elfmcys.yesstevemodel.geckolib3.core.molang;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.binding.PrimaryBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.DoubleValue;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.MolangValue;
import com.elfmcys.yesstevemodel.molang.MolangEngine;
import com.elfmcys.yesstevemodel.molang.parser.ParseException;
import com.elfmcys.yesstevemodel.molang.runtime.binding.ObjectBinding;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class MolangParser {
    /// 已经报过错的表达式，避免同一个坏表达式刷屏
    private static final Set<String> WARNED = new LinkedHashSet<>();
    private static final int MAX_WARNED = 256;
    private static final Pattern NAN_TOKEN = Pattern.compile("(?<![A-Za-z0-9_.])NaN(?![A-Za-z0-9_])");

    private final MolangEngine engine;
    private final PrimaryBinding primaryBinding;

    public MolangParser(Map<String, ObjectBinding> extraBindings) {
        this.primaryBinding = new PrimaryBinding(extraBindings);
        this.engine = MolangEngine.fromCustomBinding(this.primaryBinding);
    }

    @SuppressWarnings("unused")
    public IValue parseExpression(String molangExpression) {
        if (molangExpression == null) return DoubleValue.ZERO;
        String expression = molangExpression.trim();
        if (expression.isEmpty()) return DoubleValue.ZERO;
        // 模型作者偶尔在表达式里写注释/占位（"# fire1;"、"[;"、纯字符串注释），
        // 这些永远不可能算成数值，当成 0 处理即可，不该刷 ERROR。
        char first = expression.charAt(0);
        if (first == '#' || first == '[' || (first == '\'' && expression.lastIndexOf('\'') == expression.length() - 1)
                || (first == '"' && expression.lastIndexOf('"') == expression.length() - 1)) {
            return DoubleValue.ZERO;
        }
        // NaN / Infinity 不是合法标识符，作者常直接写进表达式
        String normalized = NAN_TOKEN.matcher(expression).replaceAll("0.0");
        try {
            return this.parseExpressionUnsafe(normalized);
        } catch (Exception e) {
            // 同一模型里同一个坏表达式会被求值很多次，只报一次，降到 WARN，
            // 避免几千行 ERROR 把真正的错误淹掉（实测一份日志里有 1319 条）。
            if (WARNED.size() < MAX_WARNED && WARNED.add(normalized)) {
                YesSteveModel.LOGGER.warn("Failed to parse value \"{}\": {}", expression, e.getMessage());
            }
            return DoubleValue.ZERO;
        }
    }

    public IValue parseExpressionUnsafe(String molangExpression) throws ParseException {
        MolangValue value = new MolangValue(this.engine.parse(molangExpression));
        this.primaryBinding.popStackFrame();
        return value;
    }

    @SuppressWarnings("unused")
    public IValue getConstant(double value) {
        return new DoubleValue(value);
    }

    public void reset() {
        this.primaryBinding.reset();
    }
}
