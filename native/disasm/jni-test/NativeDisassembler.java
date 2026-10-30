package com.vcode.studio.core.disasm;

// Same names as the Kotlin object, used to try the Rust library through JNI on the build machine
public class NativeDisassembler {
    public static native String info(String path);
    public static native String symbols(String path);
    public static native String strings(String path);
    public static native String functions(String path);
    public static native String chunks(String path);
    public static native String disassemble(String path, long start, long end);

    public static void main(String[] args) {
        System.load(args[0]);
        String lib = args[1];
        String info = info(lib);
        System.out.println(info.substring(0, Math.min(300, info.length())));
        String functions = functions(lib);
        String[] lines = functions.split("\n");
        System.out.println("functions: " + lines.length);
        for (String line : lines) {
            String[] p = line.split("\t");
            if (p.length >= 4 && Long.parseLong(p[1], 16) >= 32 && !p[2].contains("@plt")) {
                long start = Long.parseLong(p[0], 16);
                String text = disassemble(lib, start, start + 32);
                System.out.println("disassembly of " + p[3] + ":\n" + text);
                if (!text.contains("  ")) throw new RuntimeException("empty disassembly");
                break;
            }
        }
        System.out.println("chunks: " + chunks(lib).split("\n").length);
        System.out.println("strings: " + strings(lib).split("\n").length);
        System.out.println("symbols: " + symbols(lib).split("\n").length);
        try {
            info("/does/not/exist");
            throw new RuntimeException("no exception");
        } catch (IllegalStateException e) {
            System.out.println("error path ok: " + e.getMessage());
        }
        System.out.println("JNI OK");
    }
}
