#version 150

uniform vec4 uColorStart;  // inner color
uniform vec4 uColorEnd;    // outer color
uniform vec2 uCenter;      // center in UV space
uniform float uRadius;     // radius in UV space

in vec2 fragUV;
out vec4 fragColor;

void main() {
    float dist = length(fragUV - uCenter) / uRadius;
    dist = clamp(dist, 0.0, 1.0);
    fragColor = mix(uColorStart, uColorEnd, dist);
}
