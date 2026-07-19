#version 150

#define AA 1
#define _Speed 3.0
#define _Steps  12.
#define _Size 0.3

// 坐标约定：黑洞中心为世界原点。
// eye = 相机位置 - 黑洞中心；target = eye + 相机视线方向；u_up = 相机上向量。
// u_fov 为游戏实际视场角（由投影矩阵推导）；u_invProj 为投影矩阵的逆，用于深度重建。
// 吸积盘体渲染保持 GTCEu 原始比例（_Size 基准），黑洞本体由对象空间球体承担。
uniform float u_time;
uniform vec2 u_resolution;
uniform float u_intensity;
uniform float u_fov;
uniform mat4 u_invProj;
uniform vec3 eye;
uniform vec3 target;
uniform vec3 u_up;
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

out vec4 fragColor;

float hash(float x) { return fract(sin(x) * 152754.742); }
float hash(vec2 x) { return hash(x.x + hash(x.y)); }

float value(vec2 p, float f) {
    float bl = hash(floor(p * f + vec2(0., 0.)));
    float br = hash(floor(p * f + vec2(1., 0.)));
    float tl = hash(floor(p * f + vec2(0., 1.)));
    float tr = hash(floor(p * f + vec2(1., 1.)));
    vec2 fr = fract(p * f);
    fr = (3. - 2. * fr) * fr * fr;
    float b = mix(bl, br, fr.x);
    float t = mix(tl, tr, fr.x);
    return mix(b, t, fr.y);
}

// 场景直通采样：受控黑洞不扭曲周围环境
vec3 background(vec2 fragCoord) {
    return texture(Sampler0, fragCoord / u_resolution).rgb;
}

// 由深度缓冲重建当前像素场景几何到相机的距离，用于方块遮挡剔除
float sceneDistance(vec2 fragCoord) {
    vec2 uv = fragCoord / u_resolution;
    float depth = texture(Sampler1, uv).r;
    vec4 viewPos = u_invProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return length(viewPos.xyz / viewPos.w);
}

vec4 raymarchDisk(vec3 ray, vec3 zeroPos) {
    vec3 position = zeroPos;
    float lengthPos = length(position.xz);
    float dist = min(1., lengthPos * (1. / _Size) * 0.5) * _Size * 0.4 * (1. / _Steps) / (abs(ray.y));
    position += dist * _Steps * ray * 0.5;

    vec2 deltaPos;
    deltaPos.x = -zeroPos.z * 0.01 + zeroPos.x;
    deltaPos.y = zeroPos.x * 0.01 + zeroPos.z;
    deltaPos = normalize(deltaPos - zeroPos.xz);
    float parallel = dot(ray.xz, deltaPos);
    parallel /= sqrt(lengthPos);
    parallel *= 0.5;
    float redShift = parallel + 0.3;
    redShift *= redShift;
    redShift = clamp(redShift, 0., 1.);
    float disMix = clamp((lengthPos - _Size * 2.) * (1. / _Size) * 0.24, 0., 1.);
    vec3 insideCol = mix(vec3(1.0, 0.8, 0.0), vec3(0.5, 0.13, 0.02) * 0.2, disMix);
    insideCol *= mix(vec3(0.4, 0.2, 0.1), vec3(1.6, 2.4, 4.0), redShift);
    insideCol *= 1.25;
    redShift += 0.12;
    redShift *= redShift;
    vec4 o = vec4(0.);

    for (float i = 0.; i < _Steps; i++) {
        position -= dist * ray;
        float intensity = clamp(1. - abs((i - 0.8) * (1. / _Steps) * 2.), 0., 1.);
        float lengthPos = length(position.xz);
        float distMult = 1.;
        distMult *= clamp((lengthPos - _Size * 0.75) * (1. / _Size) * 1.5, 0., 1.);
        distMult *= clamp((_Size * 10. - lengthPos) * (1. / _Size) * 0.20, 0., 1.);
        distMult *= distMult;
        float u = lengthPos + u_time * _Size * 0.3 + intensity * _Size * 0.2;
        vec2 xy;
        float rot = mod(u_time * _Speed, 8192.);
        xy.x = -position.z * sin(rot) + position.x * cos(rot);
        xy.y = position.x * sin(rot) + position.z * cos(rot);
        float x = abs(xy.x / (xy.y));
        float angle = 0.02 * atan(x);
        const float f = 70.;
        float noise = value(vec2(angle, u * (1. / _Size) * 0.05), f);
        noise = noise * 0.66 + 0.33 * value(vec2(angle, u * (1. / _Size) * 0.05), f * 2.);
        float extraWidth = noise * 1. * (1. - clamp(i * (1. / _Steps) * 2. - 1., 0., 1.));
        float alpha = clamp(noise * (intensity + extraWidth) * ((1. / _Size) * 10. + 0.01) * dist * distMult, 0., 1.);
        vec3 col = 2. * mix(vec3(0.3, 0.2, 0.15) * insideCol, insideCol, min(1., intensity * 2.));
        o = clamp(vec4(col * alpha + o.rgb * (1. - alpha), o.a * (1. - alpha) + alpha), vec4(0.), vec4(1.));
        lengthPos *= (1. / _Size);
        o.rgb += redShift * (intensity * 1. + 0.5) * (1. / _Steps) * 100. * distMult / (lengthPos * lengthPos);
    }
    o.rgb = clamp(o.rgb - 0.005, 0., 1.);
    return o;
}

mat4 viewMatrix(vec3 eye, vec3 center, vec3 up) {
    vec3 f = normalize(center - eye);
    vec3 s = normalize(cross(f, up));
    vec3 u = cross(s, f);
    return mat4(
        vec4(s, 0.0),
        vec4(u, 0.0),
        vec4(-f, 0.0),
        vec4(0.0, 0.0, 0.0, 1.0)
    );
}

vec3 rayDirection(float fieldOfView, vec2 size, vec2 fragCoord) {
    vec2 xy = fragCoord - size / 2.0;
    float z = size.y / tan(radians(fieldOfView) / 2.0);
    return normalize(vec3(xy, -z));
}

void main() {
    fragColor = vec4(0.);
    float intensity = clamp(u_intensity, 0.0, 2.0);
    float sceneDist = sceneDistance(gl_FragCoord.xy);

    for (int j = 0; j < AA; j++)
    for (int i = 0; i < AA; i++) {
        // 使用游戏实际 FOV 与相机朝向逐像素构建视线，保证效果与场景对齐
        vec3 viewDir = rayDirection(u_fov, u_resolution.xy, gl_FragCoord.xy);
        vec3 pos = eye;
        mat4 viewToWorld = viewMatrix(pos, target, u_up);
        vec3 ray = (viewToWorld * vec4(viewDir, 0.0)).xyz;
        vec4 col = vec4(0.);
        vec4 glow = vec4(0.);
        vec4 outCol = vec4(100.);

        for (int disks = 0; disks < 20; disks++) {
            for (int h = 0; h < 6; h++) {
                float dotpos = dot(pos, pos);
                float invDist = inversesqrt(dotpos);
                float centDist = dotpos * invDist;
                float stepDist = 0.92 * abs(pos.y / (ray.y));
                float farLimit = centDist * 0.5;
                float closeLimit = centDist * 0.1 + 0.05 * centDist * centDist * (1. / _Size);
                stepDist = min(stepDist, min(farLimit, closeLimit));
                float invDistSqr = invDist * invDist;
                float bendForce = stepDist * invDistSqr * _Size * 0.625;
                ray = normalize(ray - (bendForce * invDist) * pos);
                pos += stepDist * ray;
                glow += vec4(1.2, 1.1, 1, 1.0) * (0.01 * stepDist * invDistSqr * invDistSqr * clamp(centDist * (2.) - 1.2, 0., 1.)) * intensity;
            }
            // 遮挡剔除：光线行进距离超过场景几何 → 被方块挡住，直接输出场景
            if (length(pos - eye) > sceneDist) {
                vec3 bg = background(gl_FragCoord.xy);
                outCol = vec4(col.rgb * col.a + bg.rgb * (1. - col.a) + glow.rgb * (1. - col.a), 1.);
                break;
            }
            float dist2 = length(pos);
            if (dist2 < _Size * 0.1) {
                outCol = vec4(col.rgb * col.a + glow.rgb * (1. - col.a), 1.);
                break;
            } else if (dist2 > _Size * 1000.) {
                vec3 bg = background(gl_FragCoord.xy);
                outCol = vec4(col.rgb * col.a + bg.rgb * (1. - col.a) + glow.rgb * (1. - col.a), 1.);
                break;
            } else if (abs(pos.y) <= _Size * 0.002) {
                vec4 diskCol = raymarchDisk(ray, pos);
                diskCol *= intensity;
                pos.y = 0.;
                pos += abs(_Size * 0.001 / ray.y) * ray;
                col = vec4(diskCol.rgb * (1. - col.a) + col.rgb, col.a + diskCol.a * (1. - col.a));
            }
        }

        if (outCol.r == 100.)
            outCol = vec4(col.rgb + glow.rgb * (col.a + glow.a), 1.);
        col = outCol;
        col.rgb = pow(col.rgb, vec3(0.6));
        fragColor += col / float(AA * AA);
    }
}
