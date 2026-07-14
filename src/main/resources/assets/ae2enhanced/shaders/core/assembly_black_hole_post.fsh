#version 150

#define AA 1
#define _Speed 3.0
#define _Steps  12.

uniform float u_time;
uniform vec2 u_resolution;
uniform float u_intensity;
uniform float u_size;
uniform float u_fov;
uniform vec2 u_targetScreen;
uniform vec3 eye;
uniform vec3 target;
uniform sampler2D Sampler0;

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

vec3 background(vec2 fragCoord, float r) {
    // 以黑洞在屏幕上的投影位置 u_targetScreen 为透镜中心，避免固定为屏幕中心导致的漂移
    vec2 centered = fragCoord - u_targetScreen;
    vec2 uv = fragCoord / u_resolution.xy;
    vec2 targetUv = u_targetScreen / u_resolution.xy;
    // 将距离归一化到 x 分辨率，避免窗口比例导致的不对称扭曲
    float dist = length(centered) / u_resolution.x;
    float factor = 20. * r / max(dist - r, 0.001);
    factor = clamp(factor, -2.0, 2.0);
    vec2 texC = mix(uv, targetUv, factor);
    texC = clamp(texC, 0.0, 1.0);
    return texture(Sampler0, texC).rgb;
}

vec4 raymarchDisk(vec3 ray, vec3 zeroPos) {
    vec3 position = zeroPos;
    float lengthPos = length(position.xz);
    float dist = min(1., lengthPos * (1. / u_size) * 0.5) * u_size * 0.4 * (1. / _Steps) / (abs(ray.y));
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
    float disMix = clamp((lengthPos - u_size * 1.5) * (1. / u_size) * 0.24, 0., 1.);
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
        distMult *= clamp((lengthPos - u_size * 0.55) * (1. / u_size) * 1.5, 0., 1.);
        distMult *= clamp((u_size * 3. - lengthPos) * (1. / u_size) * 0.20, 0., 1.);
        distMult *= distMult;
        float u = lengthPos + u_time * u_size * 0.3 + intensity * u_size * 0.2;
        vec2 xy;
        float rot = mod(u_time * _Speed, 8192.);
        xy.x = -position.z * sin(rot) + position.x * cos(rot);
        xy.y = position.x * sin(rot) + position.z * cos(rot);
        float x = abs(xy.x / (xy.y));
        float angle = 0.02 * atan(x);
        const float f = 70.;
        float noise = value(vec2(angle, u * (1. / u_size) * 0.05), f);
        noise = noise * 0.66 + 0.33 * value(vec2(angle, u * (1. / u_size) * 0.05), f * 2.);
        float extraWidth = noise * 1. * (1. - clamp(i * (1. / _Steps) * 2. - 1., 0., 1.));
        float alpha = clamp(noise * (intensity + extraWidth) * ((1. / u_size) * 10. + 0.01) * dist * distMult, 0., 1.);
        vec3 col = 2. * mix(vec3(0.3, 0.2, 0.15) * insideCol, insideCol, min(1., intensity * 2.));
        o = clamp(vec4(col * alpha + o.rgb * (1. - alpha), o.a * (1. - alpha) + alpha), vec4(0.), vec4(1.));
        lengthPos *= (1. / u_size);
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
    vec2 uv = gl_FragCoord.xy / u_resolution.xy;
    // 限制后处理影响范围为 u_targetScreen 附近的圆形区域，避免远离像素被错误扭曲
    float screenDist = length(gl_FragCoord.xy - u_targetScreen) / u_resolution.x;
    float maxRadius = 0.35;
    if (screenDist > maxRadius) {
        fragColor = texture(Sampler0, uv);
        return;
    }

    fragColor = vec4(0.);

    for (int j = 0; j < AA; j++)
    for (int i = 0; i < AA; i++) {
        // 以 u_targetScreen 为虚拟屏幕中心计算方向，保证黑洞固定在其屏幕投影位置
        vec3 viewDir = rayDirection(u_fov, u_resolution.xy, gl_FragCoord.xy - u_targetScreen + u_resolution.xy * 0.5);
        // 坐标系平移到以黑洞 target 为中心，否则引力计算会把世界原点当成黑洞中心
        vec3 pos = eye - target;
        float r = length(pos);
        mat4 viewToWorld = viewMatrix(pos, vec3(0.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0));
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
                float closeLimit = centDist * 0.1 + 0.05 * centDist * centDist * (1. / u_size);
                stepDist = min(stepDist, min(farLimit, closeLimit));
                float invDistSqr = invDist * invDist;
                float bendForce = stepDist * invDistSqr * u_size * 0.625;
                ray = normalize(ray - (bendForce * invDist) * pos);
                pos += stepDist * ray;
                glow += vec4(1.2, 1.1, 1, 1.0) * (0.01 * stepDist * invDistSqr * invDistSqr * clamp(centDist * (2.) - 1.2, 0., 1.));
            }
            float dist2 = length(pos);
            if (dist2 < u_size * 0.5) {
                outCol = vec4(col.rgb * col.a + glow.rgb * (1. - col.a), 1.);
                break;
            } else if (dist2 > u_size * 1000.) {
                vec3 bg = background(gl_FragCoord.xy, 1. / r);
                outCol = vec4(col.rgb * col.a + bg.rgb * (1. - col.a) + glow.rgb * (1. - col.a), 1.);
                break;
            } else if (abs(pos.y) <= u_size * 0.002) {
                vec4 diskCol = raymarchDisk(ray, pos);
                pos.y = 0.;
                pos += abs(u_size * 0.001 / ray.y) * ray;
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
