#version 150

uniform vec4 uColor;

in vec2 fragUV;
out vec4 fragColor;

void main() {
    fragColor = uColor;
}
