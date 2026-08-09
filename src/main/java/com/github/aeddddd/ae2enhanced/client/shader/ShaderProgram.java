package com.github.aeddddd.ae2enhanced.client.shader;

import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

/**
 * GL20 shader 程序封装：编译、链接、uniform 定位与上传.
 * 1.12.2 使用兼容性 profile(GL 2.1),顶点属性走 gl_Vertex/gl_Color 内建通道,
 * 矩阵走 gl_ModelViewProjectionMatrix/gl_ModelViewMatrix 内建 uniform.
 */
public class ShaderProgram {

    private final int programId;
    private final Map<String, Integer> uniformLocations = new HashMap<>();

    private ShaderProgram(int programId) {
        this.programId = programId;
    }

    /**
     * 编译并链接 shader 程序,失败时抛出异常并释放已分配资源.
     */
    public static ShaderProgram create(String vertexSource, String fragmentSource) throws ShaderException {
        int vert = 0;
        int frag = 0;
        int program = 0;
        try {
            vert = compile(GL20.GL_VERTEX_SHADER, vertexSource);
            frag = compile(GL20.GL_FRAGMENT_SHADER, fragmentSource);

            program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vert);
            GL20.glAttachShader(program, frag);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0) {
                String log = GL20.glGetProgramInfoLog(program, 4096);
                throw new ShaderException("Link failed: " + log);
            }
            return new ShaderProgram(program);
        } finally {
            if (vert != 0) {
                GL20.glDeleteShader(vert);
            }
            if (frag != 0) {
                GL20.glDeleteShader(frag);
            }
        }
    }

    private static int compile(int type, String source) throws ShaderException {
        int shader = GL20.glCreateShader(type);
        if (shader == 0) {
            throw new ShaderException("glCreateShader returned 0 (no GL context?)");
        }
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) {
            String log = GL20.glGetShaderInfoLog(shader, 4096);
            GL20.glDeleteShader(shader);
            throw new ShaderException("Compile failed: " + log);
        }
        return shader;
    }

    public void use() {
        GL20.glUseProgram(programId);
    }

    public static void stop() {
        GL20.glUseProgram(0);
    }

    private int loc(String name) {
        Integer loc = uniformLocations.get(name);
        if (loc == null) {
            loc = GL20.glGetUniformLocation(programId, name);
            uniformLocations.put(name, loc);
        }
        return loc;
    }

    public void setFloat(String name, float value) {
        int loc = loc(name);
        if (loc >= 0) {
            GL20.glUniform1f(loc, value);
        }
    }

    public void setVec3(String name, float x, float y, float z) {
        int loc = loc(name);
        if (loc >= 0) {
            GL20.glUniform3f(loc, x, y, z);
        }
    }

    public void delete() {
        if (programId != 0) {
            GL20.glDeleteProgram(programId);
        }
    }

    public static class ShaderException extends Exception {
        public ShaderException(String message) {
            super(message);
        }
    }
}
