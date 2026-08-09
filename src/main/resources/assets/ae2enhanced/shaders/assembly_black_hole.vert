#version 120

uniform vec3 uCenter;

varying vec4 vColor;
varying vec3 vPos;
varying vec3 vView;

void main() {
    // TESR 已将矩阵平移到黑洞中心,gl_Vertex 即黑洞局部坐标,
    // 减去 uCenter 还原以黑洞为原点的局部坐标供片元着色器程序化计算
    vPos = gl_Vertex.xyz - uCenter;
    // 相机空间顶点位置（相机位于原点）,供片元着色器计算菲涅尔项
    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    vView = viewPos.xyz;
    vColor = gl_Color;
    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
}
