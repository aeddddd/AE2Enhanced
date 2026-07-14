#version 150

in vec4 vColor;
in vec3 vPos;

uniform float uTime;
uniform float uIntensity;
uniform float uScale;
uniform vec4 ColorModulator;

out vec4 fragColor;

// 由于 getScaleFactor 已固定为 1.0，shader 内使用硬编码缩放，避免 uniform 未上传时效果异常
const float SCALE = 1.0;

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
        // 事件视界：纯黑实心，外侧带极细暗红色边缘，使其在亮背景下也能被辨认
        float r = length(vPos);
        float edge = 1.0 - smoothstep(SCALE * 2.45, SCALE * 2.55, r);
        vec3 edgeCol = vec3(0.4, 0.05, 0.05) * edge * 0.4 * intensity;
        fragColor = vec4(edgeCol, 1.0);
    } else if (part == 1) {
        // 吸积盘：赤道面旋转环，使用硬编码 SCALE 保证缩放稳定
        float r = length(vPos.xz);
        float y = vPos.y;
        float diskH = 0.10 * SCALE * intensity;

        // 通过 y 做软裁剪，使扁平几何也有体积厚度感
        float yFade = 1.0 - smoothstep(0.0, diskH, abs(y));
        if (yFade <= 0.0) {
            discard;
        }

        float outerR = 8.0 * SCALE;
        float t = clamp(r / outerR, 0.0, 1.0);
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
        fragColor = vec4(col * alpha * 1.4, alpha) * ColorModulator;
    } else if (part == 2) {
        // 相对论性喷流：沿 Y 轴锥形，使用硬编码 SCALE
        float r = length(vPos.xz);
        float y = vPos.y;
        float height = 8.0 * SCALE * intensity;
        float base = 1.0 * SCALE;
        float maxR = base * (1.0 - abs(y) / height);

        if (abs(y) > height || r > maxR) {
            discard;
        }

        float t = abs(y) / height;
        float flicker = fbm(vec2(t * 4.0 - uTime * 1.2, r * 5.0 + uTime * 0.5));
        flicker = clamp(flicker * 1.5, 0.0, 1.0);

        vec3 col = vec3(0.7, 0.25, 1.0) * (1.0 - t) * flicker * intensity;
        float alpha = (1.0 - t) * flicker * 1.0 * intensity;
        fragColor = vec4(col * alpha * 1.3, alpha) * ColorModulator;
    } else {
        discard;
    }
}
