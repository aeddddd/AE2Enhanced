#version 120

varying vec4 vColor;
varying vec3 vPos;
varying vec3 vView;

uniform float uTime;
uniform float uIntensity;
uniform float uScale;

// 缩放由 Java 端每帧通过 uniform 上传（当前 scale 固定返回 1.0）
#define SCALE uScale

float hash(float n) {
    return fract(sin(n) * 43758.5453123);
}

float hash2(vec2 p) {
    return hash(p.x * 12.9898 + p.y * 78.233);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash2(i);
    float b = hash2(i + vec2(1.0, 0.0));
    float c = hash2(i + vec2(0.0, 1.0));
    float d = hash2(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p);
        p *= 2.0;
        amplitude *= 0.5;
    }
    return value;
}

// 六边形蜂窝铺砌：返回当前位置到胞格边界的距离（0 为边界）,cellId 输出胞格索引
float hexCell(vec2 p, out vec2 cellId) {
    p.x *= 0.57735 * 2.0;
    p.y += mod(floor(p.x), 2.0) * 0.5;
    cellId = floor(p);
    p = abs(mod(p, 1.0) - 0.5);
    return abs(max(p.x * 1.5 + p.y, p.y * 2.0) - 1.0);
}

void main() {
    int part = int(vColor.r * 255.0 + 0.5);
    float intensity = clamp(uIntensity, 0.0, 2.0);

    if (part == 0) {
        // 事件视界：纯黑实心,外侧带极细暗红色边缘,使其在亮背景下也能被辨认
        float r = length(vPos);
        float edge = 1.0 - smoothstep(SCALE * 1.75, SCALE * 1.85, r);
        vec3 edgeCol = vec3(0.4, 0.05, 0.05) * edge * 0.4 * intensity;
        gl_FragColor = vec4(edgeCol, 1.0);
    } else if (part == 1) {
        // 吸积盘：赤道面旋转环
        float r = length(vPos.xz);
        float y = vPos.y;
        float diskH = 0.10 * SCALE * intensity;

        // 通过 y 做软裁剪,使扁平几何也有体积厚度感
        float yFade = 1.0 - smoothstep(0.0, diskH, abs(y));
        if (yFade <= 0.0) {
            discard;
        }

        float t = clamp((r - 4.6 * SCALE) / (7.4 * SCALE), 0.0, 1.0);
        float angle = atan(vPos.z, vPos.x);
        float rot = angle + uTime * 0.6;

        float n = fbm(vec2(rot * 2.0, r * 2.0 - uTime * 0.25));
        n = clamp(n, 0.0, 1.0);

        vec3 innerCol = vec3(1.0, 0.85, 0.55);
        vec3 midCol = vec3(0.9, 0.25, 0.55);
        vec3 outerCol = vec3(0.25, 0.0, 0.45);
        vec3 col = mix(innerCol, midCol, t);
        col = mix(col, outerCol, t * t);
        col += n * 0.45;

        float edgeFade = (1.0 - t) * yFade;
        float alpha = edgeFade * 1.2 * intensity;
        gl_FragColor = vec4(col * alpha * 1.4, alpha);
    } else if (part == 2) {
        // 相对论性喷流：沿 Y 轴锥形,使用硬编码 SCALE
        float r = length(vPos.xz);
        float y = vPos.y;
        float height = 12.8 * SCALE * intensity;
        float base = 1.6 * SCALE;
        float maxR = base * (1.0 - abs(y) / height);

        if (abs(y) > height || r > maxR) {
            discard;
        }

        float t = abs(y) / height;
        float flicker = fbm(vec2(t * 4.0 - uTime * 1.2, r * 5.0 + uTime * 0.5));
        flicker = clamp(flicker * 1.5, 0.0, 1.0);

        vec3 col = vec3(0.7, 0.25, 1.0) * (1.0 - t) * flicker * intensity;
        float alpha = (1.0 - t) * flicker * 1.0 * intensity;
        gl_FragColor = vec4(col * alpha * 1.3, alpha);
    } else if (part == 3) {
        // 约束壳（简约科幻风）：稀疏大胞格六边形框架,单层干净细线,
        // 微弱逐胞呼吸脉冲 + 极轻菲涅尔轮廓提亮,不叠加次网格与动态波带
        vec3 n = normalize(vPos);
        float theta = atan(n.z, n.x);
        float phi = asin(clamp(n.y, -1.0, 1.0));
        // 等距柱状近似：经度按 cos(phi) 收缩,缓解两极胞格拉伸
        vec2 suv = vec2(theta * cos(phi), phi);

        vec2 cellId;
        float d = hexCell(suv * 6.5, cellId);
        // 细核心亮线 + 极窄柔光过渡
        float line = 1.0 - smoothstep(0.0, 0.055, d);
        float glow = 1.0 - smoothstep(0.02, 0.20, d);
        if (line <= 0.0 && glow <= 0.0) {
            discard;
        }

        // 呼吸脉冲幅度收敛,避免闪烁感
        float pulse = 0.78 + 0.22 * sin(uTime * 1.2 + hash2(cellId) * 6.2831);
        float poleFade = smoothstep(1.50, 1.15, abs(phi));

        // 菲涅尔：仅极轻提亮球体轮廓（vPos/vView 同为相机空间向量）
        vec3 viewDir = normalize(vView);
        float fresnel = pow(1.0 - abs(dot(n, -viewDir)), 3.0);

        vec3 baseCol = vec3(0.30, 0.75, 1.0);
        vec3 col = baseCol * (line * pulse * 1.25 + glow * 0.18 * pulse) * intensity;
        col += baseCol * fresnel * 0.22 * intensity;
        float alpha = vColor.a * clamp(line + glow * 0.18 + fresnel * 0.18, 0.0, 1.0) * poleFade;
        if (alpha <= 0.002) {
            discard;
        }
        gl_FragColor = vec4(col, alpha);
    } else {
        discard;
    }
}
