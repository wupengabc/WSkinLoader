#version 150

uniform sampler2D Sampler0;  // captured game framebuffer snapshot
uniform vec4 uTintColor;
uniform float uBlurRadius;
uniform float uQuality;
uniform vec2 uResolution;

in vec2 fragUV;
out vec4 fragColor;

void main() {
    vec2 texelSize = 1.0 / uResolution;
    vec4 color = vec4(0.0);
    float totalWeight = 0.0;

    int samples = int(uQuality);
    for (int x = -samples; x <= samples; x++) {
        for (int y = -samples; y <= samples; y++) {
            vec2 offset = vec2(float(x), float(y)) * texelSize * uBlurRadius;
            float weight = 1.0 - length(vec2(x, y)) / float(samples + 1);
            weight = max(weight, 0.0);
            color += texture(Sampler0, fragUV + offset) * weight;
            totalWeight += weight;
        }
    }

    color /= totalWeight;
    fragColor = mix(color, uTintColor, uTintColor.a);
}
