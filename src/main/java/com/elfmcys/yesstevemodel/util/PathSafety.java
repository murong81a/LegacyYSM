package com.elfmcys.yesstevemodel.util;

import javax.annotation.Nullable;
import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * 路径安全工具。
 * <p>
 * 所有来自客户端的数据包（文件名、重命名目标等）在落盘、删除、移动前都必须经过这里。
 * 这里只做“路径是否仍在指定根目录内”的校验，不负责创建目录或判断文件是否存在。
 */
public final class PathSafety {

    private PathSafety() {
    }

    /**
     * 判断是否为“纯文件名”：不允许分隔符、盘符、Windows 保留字符和控制字符。
     * 用于 UploadFile / HandleFile 这类只应操作根目录下一级文件/文件夹的入口。
     */
    public static boolean isPlainName(@Nullable String name) {
        if (name == null || name.isEmpty() || name.trim().isEmpty()) return false;
        // Windows 单个文件名/目录名上限为 255 个 UTF-16 单元，超长直接拒绝
        if (name.length() > 255) return false;
        if (".".equals(name) || "..".equals(name)) return false;
        // Windows 会静默去掉结尾的点和空格，避免 "foo.ysm." 与 "foo.ysm" 指向同一文件造成混淆
        char last = name.charAt(name.length() - 1);
        if (last == '.' || last == ' ') return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?'
                    || c == '"' || c == '<' || c == '>' || c == '|' || c < 0x20 || c == 0x7F) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把客户端提供的相对路径解析到 {@code root} 目录下。
     * <p>
     * 明确拒绝：null/空白、绝对路径、盘符（包括 Windows 的 {@code C:foo} 形式）、
     * 任何 {@code ..} 路径段、控制字符以及包含冒号的路径（防止 NTFS ADS）。
     *
     * @param root     允许写入/删除/移动的根目录
     * @param relative 客户端提供的相对路径或纯文件名
     * @return 归一化后、确定位于 root 内的文件
     * @throws IllegalArgumentException 路径为空、非法或归一化后逃逸出 root
     */
    public static File resolveInside(File root, String relative) {
        if (root == null) {
            throw new IllegalArgumentException("根目录不能为空");
        }
        if (relative == null || relative.trim().isEmpty()) {
            throw new IllegalArgumentException("文件名为空");
        }
        // 控制字符（含 NUL）会让不同平台/文件系统产生歧义，直接拒绝
        for (int i = 0; i < relative.length(); i++) {
            char c = relative.charAt(i);
            if (c < 0x20 || c == 0x7F) {
                throw new IllegalArgumentException("文件名包含非法控制字符");
            }
        }
        // 先按需求构造 File，再取 Path；显式拒绝盘符，兼容 "C:foo" 这种非绝对路径
        File input;
        try {
            input = new File(relative);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("非法路径", e);
        }
        if (relative.indexOf(':') >= 0) {
            throw new IllegalArgumentException("路径不能包含盘符或冒号");
        }
        Path inputPath;
        try {
            inputPath = input.toPath();
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("非法路径", e);
        }
        if (input.isAbsolute() || inputPath.getRoot() != null) {
            throw new IllegalArgumentException("不允许绝对路径");
        }
        // 归一化之前检查原始路径段，任何 ".." 都拒绝（即使归一化后仍在 root 内）
        String separatorsNormalized = relative.replace('\\', '/');
        for (String segment : separatorsNormalized.split("/", -1)) {
            if ("..".equals(segment)) {
                throw new IllegalArgumentException("路径不允许包含 ..");
            }
            // Windows 会去掉路径段结尾的点和空格，".. " 会被系统当成 ".."，必须拒绝
            if (!segment.isEmpty() && !".".equals(segment)
                    && (segment.endsWith(".") || segment.endsWith(" "))) {
                throw new IllegalArgumentException("路径段不能以点或空格结尾");
            }
        }
        Path base = root.toPath().toAbsolutePath().normalize();
        Path target = base.resolve(inputPath).normalize();
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("路径越界");
        }
        return target.toFile();
    }

    /**
     * 删除/移动前的最后一道校验：目标必须严格位于 root 之内（不能是 root 本身）。
     *
     * @throws IllegalArgumentException file 为 null、等于 root 或位于 root 之外
     */
    public static File assertInside(File root, File file) {
        if (root == null) {
            throw new IllegalArgumentException("根目录不能为空");
        }
        if (file == null) {
            throw new IllegalArgumentException("目标文件不能为空");
        }
        Path base = root.toPath().toAbsolutePath().normalize();
        Path target = file.toPath().toAbsolutePath().normalize();
        if (target.equals(base)) {
            throw new IllegalArgumentException("目标不能是根目录本身");
        }
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("目标路径越界");
        }
        // 再按路径段检查一次，防止调用方绕过 resolveInside 直接传入 ".. " 之类的别名
        for (Path part : target) {
            String segment = part.toString();
            if (!segment.isEmpty() && (segment.endsWith(".") || segment.endsWith(" "))) {
                throw new IllegalArgumentException("目标路径段不能以点或空格结尾");
            }
        }
        return file;
    }

    /**
     * Path 重载，方便直接使用 {@code ServerModelManager} 里的 Path 常量。
     */
    public static Path resolveInside(Path root, String relative) {
        if (root == null) {
            throw new IllegalArgumentException("根目录不能为空");
        }
        return resolveInside(root.toFile(), relative).toPath();
    }

    /**
     * Path 重载，方便直接使用 {@code ServerModelManager} 里的 Path 常量。
     */
    public static Path assertInside(Path root, Path file) {
        if (root == null) {
            throw new IllegalArgumentException("根目录不能为空");
        }
        if (file == null) {
            throw new IllegalArgumentException("目标文件不能为空");
        }
        return assertInside(root.toFile(), file.toFile()).toPath();
    }
}
