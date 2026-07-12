#version 150

in vec4 vColor;
in vec3 vPos;

uniform float uTime;
uniform float uIntensity;
uniform vec4 ColorModulator;

out vec4 fragColor;

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

void main() {
    int part = int(vColor.r * 255.0 + 0.5);
    float intensity = clamp(uIntensity, 0.0, 2.0);

    if (part == 0) {
        // 事件视界：纯黑实心
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    } else if (part == 1) {
        // 吸积盘：赤道面旋转环，几何本身定义内外半径，shader 只处理厚度与颜色
        float r = length(vPos.xz);
        float y = vPos.y;
        float diskH = 0.10 * intensity;

        if (abs(y) > diskH) {
            discard;
        }

        float t = clamp(r / 8.0, 0.0, 1.0);
        float angle = atan(vPos.z, vPos.x);
        float rot = angle + uTime * 0.6;

        float n = fbm(vec2(rot * 2.0, r * 2.0 - uTime * 0.25));
        n = clamp(n, 0.0, 1.0);

        vec3 innerCol = vec3(1.0, 0.85, 0.55);
        vec3 midCol = vec3(0.8, 0.2, 0.5);
        vec3 outerCol = vec3(0.15, 0.0, 0.35);
        vec3 col = mix(innerCol, midCol, t);
        col = mix(col, outerCol, t * t);
        col += n * 0.35;

        float edgeFade = (1.0 - t) * (1.0 - abs(y) / diskH);
        float alpha = edgeFade * 0.9 * intensity;
        fragColor = vec4(col * alpha, alpha) * ColorModulator;
    } else if (part == 2) {
        // 相对论性喷流：沿 Y 轴锥形
        float r = length(vPos.xz);
        float y = vPos.y;
        float height = 8.0 * intensity;
        float base = 1.0;
        float maxR = base * (1.0 - abs(y) / height);

        if (abs(y) > height || r > maxR) {
            discard;
        }

        float t = abs(y) / height;
        float flicker = fbm(vec2(t * 4.0 - uTime * 1.2, r * 5.0 + uTime * 0.5));
        flicker = clamp(flicker * 1.5, 0.0, 1.0);

        vec3 col = vec3(0.5, 0.15, 0.9) * (1.0 - t) * flicker * intensity;
        float alpha = (1.0 - t) * flicker * 0.7 * intensity;
        fragColor = vec4(col * alpha, alpha) * ColorModulator;
    } else {
        discard;
    }
}
