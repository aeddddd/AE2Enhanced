#version 150

in vec3 Position;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 uCenter;

out vec4 vColor;
out vec3 vPos;
out vec3 vView;

void main() {
    // 顶点缓冲中的 Position 已是相机空间坐标（CPU 侧乘过 pose 平移），
    // 减去相机空间的黑洞中心，还原以黑洞为原点的局部坐标供片元着色器程序化计算
    vPos = Position - uCenter;
    // 相机空间顶点位置（相机位于原点），供片元着色器计算菲涅尔项
    vView = Position;
    vColor = Color;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
