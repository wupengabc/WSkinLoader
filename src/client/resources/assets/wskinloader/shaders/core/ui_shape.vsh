#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ProjMat;

out vec2 fragUV;

void main() {
    gl_Position = ProjMat * vec4(Position, 1.0);
    fragUV = UV0;
}
