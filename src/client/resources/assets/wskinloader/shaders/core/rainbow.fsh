#version 150

uniform float uTime;
uniform float uSpeed;
uniform float uScale;
uniform float uSaturation;
uniform float uBrightness;
uniform float uTransparency;

in vec2 fragUV;
out vec4 fragColor;

vec3 hsb2rgb(float h, float s, float b) {
    vec3 rgb = clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0,
                     0.0, 1.0);
    rgb = rgb * rgb * (3.0 - 2.0 * rgb); // smoothstep
    return b * mix(vec3(1.0), rgb, s);
}

void main() {
    float hue = fract(uTime * uSpeed + fragUV.x * uScale);
    vec3 rgb = hsb2rgb(hue, uSaturation, uBrightness);
    fragColor = vec4(rgb, uTransparency);
}
