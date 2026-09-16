#version 150

uniform vec4 uColorStart;
uniform vec4 uColorEnd;
uniform vec2 uDirection;  // normalized direction vector in UV space

in vec2 fragUV;
out vec4 fragColor;

void main() {
    float t = dot(fragUV, uDirection);
    t = clamp(t, 0.0, 1.0);
    fragColor = mix(uColorStart, uColorEnd, t);
}
